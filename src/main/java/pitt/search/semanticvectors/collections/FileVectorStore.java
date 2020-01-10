package pitt.search.semanticvectors.collections;

import com.ontotext.trree.io.ReadWriteFile;
import gnu.trove.iterator.TObjectLongIterator;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.map.hash.TObjectLongHashMap;
import pitt.search.semanticvectors.LuceneUtils;
import pitt.search.semanticvectors.ObjectVector;
import pitt.search.semanticvectors.vectors.Vector;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.Enumeration;

/**
 * VectorStore which uses {@link ReadWriteFile} to store the vectors
 */
public class FileVectorStore implements ModifiableVectorStore {

	private int bufferSizeInBytes;
	private ReadWriteFile file;
	private VectorBufferConverter converter;
	private TObjectLongHashMap<String> keyToPos;
	private TLongObjectHashMap<String> posToKey;
	private long size;
	private boolean temp;

	public FileVectorStore(File storageFile, VectorBufferConverter converter) {
		this(storageFile, converter, true);
	}

	public FileVectorStore(File storageFile, VectorBufferConverter converter, boolean temp) {
		this.bufferSizeInBytes = converter.getBufferSizeInBytes();
		this.file = new ReadWriteFile(bufferSizeInBytes, storageFile, false);
		this.converter = converter;
		this.temp = temp;
		final int MAPS_INITIAL_CAPACITY = (int) (LuceneUtils.MAPS_INITIAL_CAPACITY_COEFFICIENT * this.bufferSizeInBytes / 4);
		keyToPos = new TObjectLongHashMap<>(MAPS_INITIAL_CAPACITY);
		posToKey = new TLongObjectHashMap<>(MAPS_INITIAL_CAPACITY);
		size = 0;

		if (!temp && storageFile.exists() && storageFile.length() != 0) {
			restoreMaps();
			size = keyToPos.size();
		}
	}

	@Override
	public void putVector(Object key, Vector vector) {
		long currentPosition;
		if (keyToPos.containsKey(key)) {
			currentPosition = keyToPos.get(key);
		} else {
			currentPosition = size;
			keyToPos.put((String) key, size++);
		}
		posToKey.put(currentPosition, (String) key);

		ByteBuffer byteBuffer = converter.writeToBuffer(vector);
		file.write(byteBuffer, currentPosition * bufferSizeInBytes);
	}

	@Override
	public Vector getVector(Object object) {
		Long position = keyToPos.get(object);

		if (position != null) {
			ByteBuffer byteBuffer = ByteBuffer.allocateDirect(bufferSizeInBytes);
			file.read(byteBuffer, position * bufferSizeInBytes);

			return converter.readFromBuffer(byteBuffer);
		}

		return null;
	}

	@Override
	public boolean containsVector(Object object) {
		return keyToPos.containsKey(object);
	}

	@Override
	public Enumeration<ObjectVector> getAllVectors() {
		return new Enumeration<ObjectVector>() {
			long currPos = 0;

			@Override
			public boolean hasMoreElements() {
				return currPos < size;
			}

			@Override
			public ObjectVector nextElement() {
				ByteBuffer byteBuffer = ByteBuffer.allocateDirect(bufferSizeInBytes);
				file.read(byteBuffer, currPos * bufferSizeInBytes);

				return new ObjectVector(posToKey.get(currPos++), converter.readFromBuffer(byteBuffer));
			}
		};
	}

	@Override
	public int getNumVectors() {
		return (int) size;
	}

	@Override
	public void updateVector(Object key, Vector vector) {
		putVector(key, vector);
	}

	@Override
	public void flush() {
		file.flush();
		if (!temp) {
			persistKeyMap();
		}
	}

	@Override
	public void close() {
		if (temp) {
			file.delete();
		} else {
			persistKeyMap();
		}
	}

	private void persistKeyMap() {
		String storageFileName = file.getFileIdentifier().getAbsolutePath();
		File keyMap = new File(storageFileName + ".keymap");
		if (keyMap.exists())
			keyMap.delete();

		try (FileOutputStream fileOutputStream = new FileOutputStream(keyMap);
			 ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream)) {
			objectOutputStream.writeObject(keyToPos);
		} catch (IOException e) {
			throw new RuntimeException("Could not save keymap to file", e);
		}
	}

	private void restoreMaps() {
		String storageFileName = file.getFileIdentifier().getAbsolutePath();
		File keyMap = new File(storageFileName + ".keymap");
		if (!keyMap.exists())
			throw new RuntimeException("KeyMap file does not exist! " + keyMap.getAbsolutePath());

		try (FileInputStream fileInputStream = new FileInputStream(keyMap);
			 ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream)) {
			keyToPos = (TObjectLongHashMap<String>) objectInputStream.readObject();
		} catch (IOException | ClassNotFoundException e) {
			throw new RuntimeException("Could not save keymap to file", e);
		}

		TObjectLongIterator<String> iterator = keyToPos.iterator();
		while (iterator.hasNext()) {
			iterator.advance();
			posToKey.put(iterator.value(), iterator.key());
		}
	}
}
