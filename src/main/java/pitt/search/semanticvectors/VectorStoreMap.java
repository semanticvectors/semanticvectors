package pitt.search.semanticvectors;

import java.io.File;
import java.util.Enumeration;

public class VectorStoreMap {

	File storage;
	VectorStore vectorStore;

	public VectorStoreMap(File storage, VectorStore vectorStore) {
		this.storage = storage;
		this.vectorStore = vectorStore;
	}

	public void initialize() {

		long[] hashList = new long[vectorStore.getNumVectors()];

		Enumeration<ObjectVector> allVectors = vectorStore.getAllVectors();

		int position = 0;
		while (allVectors.hasMoreElements()) {
			hashList[position] = (allVectors.nextElement().getObject().toString().hashCode() << 32) & position++;
		}

	}
}
