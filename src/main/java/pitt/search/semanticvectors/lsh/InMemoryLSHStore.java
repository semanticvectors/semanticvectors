package pitt.search.semanticvectors.lsh;

import org.apache.lucene.store.IndexInput;
import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.ObjectVector;
import pitt.search.semanticvectors.VectorStoreReaderLucene;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;

import java.io.IOException;
import java.io.File;
import java.util.*;

/**
 * A {@link LSHStore} which stores the vectors in a {@link HashMap} in-memory
 */
public class InMemoryLSHStore extends LSHStore {

	LocalitySensitiveHash lsh;
	FlagConfig flagConfig;
	TreeMap<Short, Collection<Long>> storeHash;
	File vecStoreFile;


	InMemoryLSHStore(File vecStoreFile, FlagConfig flagConfig) {
		this.vecStoreFile = vecStoreFile;
		this.flagConfig = flagConfig;
		this.lsh = new LocalitySensitiveHash(flagConfig);
	}


	@Override
	public void initCache() throws IOException {
		storeHash = new TreeMap<>();
		try (IndexInput indexInput = new VectorStoreReaderLucene(vecStoreFile.getAbsolutePath(), flagConfig).getIndexInput()) {
			indexInput.seek(0);
			indexInput.readString();

			long currentPosition;
			while ((currentPosition = indexInput.getFilePointer()) < indexInput.length()) {
				indexInput.readString();
				Vector vector = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());
				vector.readFromLuceneStream(indexInput);

				short hash = lsh.getHash(vector);
				storeHash.computeIfAbsent(hash, h -> new LinkedList<>()).add(currentPosition);
			}
		}
	}

	@Override
	public Enumeration<ObjectVector> getSimilar(Vector vector) throws IOException {
		if (storeHash == null) {
			throw new RuntimeException("There is no cached data");
		}

		VectorStoreReaderLucene vecStore = new VectorStoreReaderLucene(vecStoreFile.getAbsolutePath(), flagConfig);
		short[] potentialHashes = lsh.getSimilarHashes(vector);
		Set<Long> allVecPositions = new HashSet<>();
		for (short potentialHash : potentialHashes) {
			if (storeHash.containsKey(potentialHash))
				allVecPositions.addAll(storeHash.get(potentialHash));
		}
		Iterator<Long> iter = allVecPositions.iterator();

		return getObjectVectorEnum(iter, vecStore, flagConfig);
	}

}
