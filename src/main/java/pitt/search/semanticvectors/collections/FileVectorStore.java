package pitt.search.semanticvectors.collections;

import com.ontotext.trree.io.ReadWriteFile;
import pitt.search.semanticvectors.ObjectVector;
import pitt.search.semanticvectors.vectors.Vector;

import java.io.File;
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

	public FileVectorStore(File storageFile, VectorBufferConverter converter) {
		this.bufferSizeInBytes = converter.getBufferSizeInBytes();
		this.file = new ReadWriteFile(bufferSizeInBytes, storageFile, false);
		this.converter = converter;
		keyToPos = new HashMap<>();
		posToKey = new HashMap<>();
		size = 0;
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
	}

	@Override
	public void close() {
		file.delete();
	}
}
