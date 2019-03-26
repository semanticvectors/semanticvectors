package pitt.search.semanticvectors.lsh;

import org.netlib.blas.BLAS;
import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.collections.BinaryVectorBufferConverter;
import pitt.search.semanticvectors.collections.RealVectorBufferConverter;
import pitt.search.semanticvectors.collections.VectorBufferConverter;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorType;
import pitt.search.semanticvectors.vectors.VectorUtils;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;

/**
 * Implementation of Locality-Sensitive Hashing.<b>
 * The idea is that we can give a certain "hash" to a vector by comparing it to a set of random orthogonal vectors. In
 * our case we can generate up to 15 random vectors which are used to generate that hash. <b>
 * We can expect that 2 vectors are similar if they have similar hashes. The similarity between the hashes is measured
 * by allowed number of different bits.
 */
public class LocalitySensitiveHash {

	private static final int MAX_NUM_HASHES = 15;

	private int numOfHashes;
	private int numBitsDifference;

	List<Vector> randomVectors;
	BLAS blas;

	private LocalitySensitiveHash() {
	}

	public LocalitySensitiveHash(FlagConfig flagConfig) {
		numOfHashes = Math.min(flagConfig.lsh_hashes_num(), MAX_NUM_HASHES);
		numBitsDifference = Math.min(flagConfig.lsh_max_bits_diff(), numOfHashes);

		randomVectors = new ArrayList<>(numOfHashes);
		blas = BLAS.getInstance();

		Random random = new Random();
		for (int i = 0; i < numOfHashes; i++) {
			randomVectors.add(VectorFactory.generateRandomVector(flagConfig.vectortype(), flagConfig.dimension(), flagConfig.dimension() / 2, random));
		}

		VectorUtils.orthogonalizeVectors(randomVectors);
	}

	/**
	 * Returns a short representing the "hash" of the vector. Each bit of the hash (up to the number of random vectors)
	 * represents whether the scalar product of the vector and one of the random vectors is positive.
	 */
	public short getHash(Vector vector) {
		short hash = 0;

		for (int i = 0; i < numOfHashes; i++) {
			if (VectorUtils.scalarProduct(vector, randomVectors.get(i), null, blas) > 0)
				hash |= (1 << i);
		}

		return hash;
	}

	/**
	 * Calculates the hash of the vector and returns all similar hashes.
	 */
	public short[] getSimilarHashes(Vector vector) {
		short hash = getHash(vector);

		Set<Short> candidates = new HashSet<>();
		candidates.add(hash);
		collectCandidates(hash, candidates, numBitsDifference);

		short[] result = new short[candidates.size()];
		int size = 0;
		for (Short candidate : candidates) {
			result[size++] = candidate;
		}

		return result;
	}

	/**
	 * Serializes the config. The serialization in includes the configuration and all random vectors.
	 */
	public void writeToFile(File file) throws IOException {
		if (randomVectors.size() == 0)
			return;

		Vector sample = randomVectors.get(0);
		VectorType type = sample.getVectorType();

		VectorBufferConverter converter;
		int dimension = sample.getDimension();

		converter = getVectorBufferConverter(type, dimension);

		try (FileOutputStream fw = new FileOutputStream(file);
			 DataOutputStream dw = new DataOutputStream(fw);
			 FileChannel fc = fw.getChannel()) {

			dw.writeInt(numOfHashes);
			dw.writeInt(numBitsDifference);
			dw.writeUTF(type.toString());
			dw.writeInt(dimension);

			for (Vector randomVector : randomVectors) {
				fc.write(converter.writeToBuffer(randomVector));
			}

		}
	}

	/**
	 * Deserialize the object.
	 */
	public static LocalitySensitiveHash initFromFile(File file) throws IOException {
		LocalitySensitiveHash lsh = new LocalitySensitiveHash();

		try (FileInputStream fis = new FileInputStream(file);
			 DataInputStream dis = new DataInputStream(fis);
			 FileChannel fc = fis.getChannel()) {

			lsh.numOfHashes = dis.readInt();
			lsh.numBitsDifference = dis.readInt();
			VectorType type = VectorType.valueOf(dis.readUTF());
			int dimension = dis.readInt();

			VectorBufferConverter converter = getVectorBufferConverter(type, dimension);
			lsh.randomVectors = new ArrayList<>();
			ByteBuffer buffer = ByteBuffer.allocateDirect(converter.getBufferSizeInBytes());

			for (int i = 0; i < lsh.numOfHashes; i++) {
				fc.read(buffer);
				buffer.position(0);
				lsh.randomVectors.add(converter.readFromBuffer(buffer));
			}
		}

		lsh.blas = BLAS.getInstance();

		return lsh;
	}

	private static VectorBufferConverter getVectorBufferConverter(VectorType type, int dimension) {
		VectorBufferConverter converter;
		if (type == VectorType.REAL) {
			converter = new RealVectorBufferConverter(dimension);
		} else if (type == VectorType.BINARY) {
			converter = new BinaryVectorBufferConverter(dimension);
		} else {
			throw new RuntimeException("Unsupported vector type " + type);
		}
		return converter;
	}

	private void collectCandidates(short hash, Set<Short> candidates, int numBitsDifference) {
		if (numBitsDifference == 0)
			return;

		for (short i = 0; i < numOfHashes; i++) {
			short modified = (short) (hash ^ (1 << i));
			candidates.add(modified);

			collectCandidates(modified, candidates, numBitsDifference - 1);
		}
	}

}
