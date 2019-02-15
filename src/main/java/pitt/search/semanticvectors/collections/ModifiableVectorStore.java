package pitt.search.semanticvectors.collections;

import pitt.search.semanticvectors.vectors.Vector;

public interface ModifiableVectorStore extends CloseableVectorStore {

	void putVector(Object key, Vector vector);

	void updateVector(Object key, Vector vector);

	void flush();

	@Override
	void close();
}
