package pitt.search.semanticvectors.lsh;

import org.junit.Test;
import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TestLocalitySensitiveHash {

	@Test
	public void testSerialization() throws IOException {
		File tmp = Files.createTempFile("file", "tmp").toFile();
		FlagConfig config = FlagConfig.parseFlagsFromString("-lsh_hashes_num 6 -lsh_max_bits_diff 2");
		LocalitySensitiveHash lsh = new LocalitySensitiveHash(config);

		lsh.writeToFile(tmp);

		LocalitySensitiveHash restored = LocalitySensitiveHash.initFromFile(tmp);

		for (int i = 0; i < lsh.randomVectors.size(); i++) {
			assertEquals(1.0, lsh.randomVectors.get(i).measureOverlap(restored.randomVectors.get(i)), 0.001);
		}

	}

	@Test
	public void testIncorrectParameters() {
		makeSureConfigurationThrows("-lsh_hashes_num 6 -lsh_max_bits_diff 7");
		makeSureConfigurationThrows("-lsh_hashes_num 6 -lsh_max_bits_diff -1");
		makeSureConfigurationThrows("-lsh_hashes_num -1 -lsh_max_bits_diff 2");
		makeSureConfigurationThrows("-lsh_hashes_num 16 -lsh_max_bits_diff 2");
	}

	@Test
	public void testCalculatingSimilarHashes() {
		int numHashes = 8;
		for (int i = 1; i < numHashes; i++) {
			testCorrectSimilarHashSize(numHashes, i);
		}
	}

	private void testCorrectSimilarHashSize(int numHashes, int bitsDiff) {
		FlagConfig config = FlagConfig.parseFlagsFromString("-lsh_hashes_num " + numHashes + " -lsh_max_bits_diff " + bitsDiff);
		LocalitySensitiveHash lsh = new LocalitySensitiveHash(config);

		short[] similarHashes = lsh.getSimilarHashes(VectorFactory.createZeroVector(config.vectortype(), config.dimension()));

		int expected = 0;
		for (int i = 0; i <= bitsDiff; i++) {
			expected+= combination(numHashes, i);
		}
		assertEquals(expected, similarHashes.length);
	}

	private int combination(int n, int k) {
		int result = 1;

		for (int i = 0; i < k; i++) {
			result*= n - i;
		}
		for (int i = 0; i < k; i++) {
			result/= i + 1;
		}

		return result;
	}

	private void makeSureConfigurationThrows(String config) {
		try {
			FlagConfig.parseFlagsFromString(config);
			fail("The configuration did not throw: " + config);
		} catch (RuntimeException e) {
			// good
		}
	}

}
