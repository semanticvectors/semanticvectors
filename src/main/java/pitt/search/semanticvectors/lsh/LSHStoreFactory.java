package pitt.search.semanticvectors.lsh;

import pitt.search.semanticvectors.FlagConfig;

import java.io.IOException;
import java.io.File;
import java.util.*;

/**
 * Ensures there are single instances of the {@link LSHStore}'s for each indexes vector store.
 */
public enum LSHStoreFactory {

	INSTANCE;

	private static Map<LSHConfig, LSHStore> hashStores = new HashMap<>();

	/**
	 * Returns a {@link PersistedLSHStore} for a given {@link pitt.search.semanticvectors.VectorStoreReaderLucene} store.
	 * If such is not already present one gets created.<b>
	 * Initially my idea was to be able to choose between {@link PersistedLSHStore} and {@link InMemoryLSHStore} but as
	 * there is no performance difference between the 2 there is no real benefit of the in-memory one.
	 */
	public LSHStore getStore(File vecStoreFile, FlagConfig flagConfig) {
		return hashStores.computeIfAbsent(new LSHConfig(vecStoreFile.getAbsolutePath(), flagConfig.lsh_hashes_num(), flagConfig.lsh_max_bits_diff()),
				config -> {
					try {
						LSHStore store = new PersistedLSHStore(vecStoreFile, flagConfig);
						store.initCache();
						return store;
					} catch (IOException e) {
						throw new RuntimeException("Could not init vector store cache", e);
					}
				});
	}

	/**
	 * This is used (although not in this code-base). It deletes the stores related to a certain similarity index. This
	 * should be called once an index is deleted/recreated because otherwise you can end-up with outdated cache.
	 */
	public void clearStoresForIndex(File indexHome) {
		List<LSHConfig> toDelete = new LinkedList<>();
		for (LSHConfig lshConfig : hashStores.keySet()) {
			if (lshConfig.fileName.startsWith(indexHome.getAbsolutePath()))
				toDelete.add(lshConfig);
		}
		for (LSHConfig lshConfig : toDelete) {
			hashStores.remove(lshConfig).close();
		}

	}

	private static class LSHConfig {
		String fileName;
		int numOfHashes;
		int maxBitsDiff;

		public LSHConfig(String fileName, int numOfHashes, int maxBitsDiff) {
			this.fileName = fileName;
			this.numOfHashes = numOfHashes;
			this.maxBitsDiff = maxBitsDiff;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			LSHConfig lshConfig = (LSHConfig) o;
			return numOfHashes == lshConfig.numOfHashes &&
					maxBitsDiff == lshConfig.maxBitsDiff &&
					Objects.equals(fileName, lshConfig.fileName);
		}

		@Override
		public int hashCode() {
			return Objects.hash(fileName, numOfHashes, maxBitsDiff);
		}
	}

}
