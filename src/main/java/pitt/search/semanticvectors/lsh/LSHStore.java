package pitt.search.semanticvectors.lsh;

import org.apache.lucene.store.IndexInput;
import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.ObjectVector;
import pitt.search.semanticvectors.VectorStoreReaderLucene;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;

import java.io.IOException;
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

	protected Enumeration<ObjectVector> getObjectVectorEnum(Iterator<Long> iter, VectorStoreReaderLucene vecStore, FlagConfig flagConfig) {
		IndexInput indexInput = vecStore.getIndexInput();

		Enumeration<ObjectVector> vecs = new Enumeration<ObjectVector>() {

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
		return vecs;
	}
}
