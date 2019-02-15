package pitt.search.semanticvectors.collections;

import pitt.search.semanticvectors.VectorStore;

public interface CloseableVectorStore extends VectorStore, AutoCloseable {

	@Override
	void close();

}
