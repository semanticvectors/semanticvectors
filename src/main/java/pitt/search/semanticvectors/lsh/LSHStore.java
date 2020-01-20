package pitt.search.semanticvectors.lsh;

import org.apache.lucene.store.DataInput;
import org.apache.lucene.store.IndexInput;
import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.ObjectVector;
import pitt.search.semanticvectors.VectorStoreReaderLucene;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;

import java.io.File;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Enumeration;
import java.util.Iterator;

/**
 * A store which uses Locality-Sensitive Hashing for storing and retrieving vectors. The vector store which should be
 * indexed is passed in the constructor of the concrete class.
 */
public abstract class LSHStore {

	public abstract void initCache() throws IOException;

	public abstract Enumeration<ObjectVector> getSimilar(Vector vector) throws IOException;

	public void close() {
	}

	/**
	 * Returns an Enumeration of vectors given an iterator containing their position in a vector store
	 * @param iter iterator of vector file positions
	 * @param vecStore the vector store containing the vectors
	 * @param flagConfig
	 * @return
	 * @throws IOException
	 */
	protected Enumeration<ObjectVector> getObjectVectorEnum(Iterator<Long> iter, VectorStoreReaderLucene vecStore, FlagConfig flagConfig) throws IOException {
		Enumeration<ObjectVector> vecs;
		File storeFile = vecStore.getVectorFile();

		// The MappedByteBuffer is faster than the IndexInput but supports only smaller files. Creating a class which uses
		// an array of mapped buffers to cover a big file and have better performance than the InputIndex is IMPOSSIBLE!!!
		if (storeFile.length() < Integer.MAX_VALUE) {
			MappedByteBuffer byteBuffer = FileChannel.open(storeFile.toPath()).map(FileChannel.MapMode.READ_ONLY, 0, storeFile.length());
			DataInput di = new DataInput() {
				@Override
				public byte readByte() throws IOException {
					return byteBuffer.get();
				}

				@Override
				public void readBytes(byte[] b, int offset, int len) throws IOException {
					byteBuffer.get(b, offset, len);
				}
			};

			vecs = new Enumeration<ObjectVector>() {

				@Override
				public boolean hasMoreElements() {
					if (iter.hasNext())
						return true;
					else {
						DirectByteBufferCleaner.closeDirectByteBuffer(byteBuffer);
						vecStore.close();
						return false;
					}
				}

				@Override
				public ObjectVector nextElement() {
					if (!hasMoreElements())
						return null;
					String object;
					Vector vector = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());
					try {
						long position = iter.next();
						byteBuffer.position((int) position);
						object = di.readString();
						vector.readFromByteBuffer(byteBuffer);
					} catch (IOException e) {
						throw new RuntimeException("Could not read vector", e);
					}
					return new ObjectVector(object, vector);
				}
			};
		} else {
			IndexInput indexInput = vecStore.getIndexInput();
			vecs = new Enumeration<ObjectVector>() {

				@Override
				public boolean hasMoreElements() {
					if (iter.hasNext())
						return true;
					else {
						vecStore.close();
						return false;
					}
				}

				@Override
				public ObjectVector nextElement() {
					if (!hasMoreElements())
						return null;
					String object;
					Vector vector = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());
					try {
						long position = iter.next();
						indexInput.seek(position);
						object = indexInput.readString();
						vector.readFromLuceneStream(indexInput);
					} catch (IOException e) {
						throw new RuntimeException("Could not read vector", e);
					}
					return new ObjectVector(object, vector);
				}
			};
		}
		return vecs;
	}

}
