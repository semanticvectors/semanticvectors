package pitt.search.semanticvectors.collections;

import com.ontotext.trree.io.ReadWriteFile;
import pitt.search.semanticvectors.ObjectVector;
import pitt.search.semanticvectors.vectors.Vector;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * VectorStore which uses {@link ReadWriteFile} to store the vectors
 */
public class FileVectorStore implements ModifiableVectorStore {

	private int bufferSizeInBytes;
	private ReadWriteFile file;
	private VectorBufferConverter converter;
	private Map<String, Long> keyToPos;
	private Map<Long, String> posToKey;
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
		keyToPos = new HashMap<>();
		posToKey = new HashMap<>();
		size = 0;

		if (!temp && storageFile.exists() && storageFile.length() != 0) {
			try {
				restoreMaps();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			size = keyToPos.size();
		}
	}

	@Override
	public void putVector(Object key, Vector vector) {
		long currentPosition = keyToPos.computeIfAbsent((String) key, v -> size++);
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
			Vector vector = converter.readFromBuffer(byteBuffer);

			return vector;
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
				Vector vector = converter.readFromBuffer(byteBuffer);

				return new ObjectVector(posToKey.get(currPos++), vector);
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

		try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(keyMap))) {
			for (Map.Entry<String, Long> entry : keyToPos.entrySet()) {
				dos.writeUTF(entry.getKey());
				dos.writeLong(entry.getValue());
			}
		} catch (IOException e) {
			throw new RuntimeException("Could not save keymap to file", e);
		}
	}

	private void restoreMaps() throws IOException {
		String storageFileName = file.getFileIdentifier().getAbsolutePath();
		File keyMap = new File(storageFileName + ".keymap");
		if (!keyMap.exists())
			throw new RuntimeException("KeyMap file does not exist! " + keyMap.getAbsolutePath());

		try (FileInputStream fis = new FileInputStream(keyMap);
			 DataInputStream dis = new DataInputStream(fis)) {
			while (fis.getChannel().position() < fis.getChannel().size()) {
				String key = dis.readUTF();
				long position = dis.readLong();
				keyToPos.put(key, position);
				posToKey.put(position, key);
			}
		}
	}
}
