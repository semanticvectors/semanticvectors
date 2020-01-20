package pitt.search.semanticvectors.lsh;

import org.apache.lucene.store.IndexInput;
import pitt.search.semanticvectors.*;
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
		VectorStoreReaderLucene vecStore = null;
		try {
			vecStore = new VectorStoreReaderLucene(vecStoreFile.getAbsolutePath(), flagConfig);
			IndexInput indexInput = vecStore.getIndexInput();
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
		} finally {
			VectorStoreUtils.closeVectorStores(vecStore);
		}
	}

	@Override
	public Enumeration<ObjectVector> getSimilar(Vector vector) throws IOException {
		if (storeHash == null) {
			throw new RuntimeException("There is no cached data");
		}

		VectorStoreReaderLucene vecStore = new VectorStoreReaderLucene(vecStoreFile.getAbsolutePath(), flagConfig);
		try {
			short[] potentialHashes = lsh.getSimilarHashes(vector);
			Set<Long> allVecPositions = new HashSet<>();
			for (short potentialHash : potentialHashes) {
				if (storeHash.containsKey(potentialHash))
					allVecPositions.addAll(storeHash.get(potentialHash));
			}
			Iterator<Long> iter = allVecPositions.iterator();
			return getObjectVectorEnum(iter, vecStore, flagConfig);
		} finally {
			vecStore.close();
		}
	}

}
