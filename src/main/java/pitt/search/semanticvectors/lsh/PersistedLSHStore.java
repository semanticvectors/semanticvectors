package pitt.search.semanticvectors.lsh;

import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.ObjectVector;
import pitt.search.semanticvectors.VectorStoreReaderLucene;
import pitt.search.semanticvectors.vectors.Vector;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of the {@link LSHStore} which is stored on the hard-drive. It uses the {@link PersistedHashMap} class
 * to do so.
 */
public class PersistedLSHStore extends LSHStore {

	// Stores the vectors with their hash
	public static final String STORE_SUFFIX = ".lshstore";
	// Stores the hashing configuration
	public static final String LSH_SUFFIX = ".lsh";

	protected File homeDir;

	LocalitySensitiveHash lsh;
	File vecStoreFile;
	FlagConfig flagConfig;
	File cacheFile;
	PersistedHashMap phm;

	public PersistedLSHStore(File vecStoreFile, FlagConfig flagConfig) {
		this.vecStoreFile = vecStoreFile;
		this.flagConfig = flagConfig;
	}

	/**
	 * If the store exists it gets reinitialized. If the store does not exist a new one is created.
	 * @throws IOException
	 */
	@Override
	public void initCache() throws IOException {
		homeDir = new File(vecStoreFile.getParentFile(), "lsh");
		if (!homeDir.exists())
			Files.createDirectories(homeDir.toPath());

		cacheFile = new File(homeDir,vecStoreFile.getName() + STORE_SUFFIX + flagConfig.lsh_hashes_num() + flagConfig.lsh_max_bits_diff());
		File lshFile = new File(homeDir, vecStoreFile.getName() + LSH_SUFFIX + flagConfig.lsh_hashes_num() + flagConfig.lsh_max_bits_diff());

		phm = new PersistedHashMap(cacheFile);

		if (cacheFile.exists()) {
			phm.init();
			lsh = LocalitySensitiveHash.initFromFile(lshFile);
		} else {
			// We create an in-memory store and once it's been initialized we basically persist it. Makes our lives easier
			InMemoryLSHStore inMemoryStore = new InMemoryLSHStore(vecStoreFile, flagConfig);
			inMemoryStore.initCache();
			this.lsh = inMemoryStore.lsh;
			lsh.writeToFile(lshFile);
			phm.persist(inMemoryStore.storeHash);
		}
	}


	@Override
	public Enumeration<ObjectVector> getSimilar(Vector vector) throws IOException {
		// get all hashes which are similar to the vector's hash
		short[] similarHashes = lsh.getSimilarHashes(vector);
		Arrays.sort(similarHashes);

		// filter the hashes which are not present in the LSH store
		List<Short> keys = phm.keys();
		List<Short> toCheck = keys.stream().filter(aShort -> Arrays.binarySearch(similarHashes, aShort) >= 0).collect(Collectors.toList());
		Iterator<Short> buckets = toCheck.iterator();

		Iterator<Long> iter = new Iterator<Long>() {
			LongIterator bucketIter;

			@Override
			public boolean hasNext() {
				if (bucketIter == null && buckets.hasNext()) {
					bucketIter = phm.get(buckets.next());
				}
				if (!bucketIter.hasNext()) {
					if (buckets.hasNext()) {
						bucketIter = phm.get(buckets.next());
					} else {
						return false;
					}
				}
				return true;
			}

			@Override
			public Long next() {
				if (!hasNext())
					return null;

				return bucketIter.next();
			}
		};

		return getObjectVectorEnum(iter, new VectorStoreReaderLucene(vecStoreFile.getAbsolutePath(), flagConfig), flagConfig);
	}

	@Override
	public void close() {
		phm.close();
	}
}
