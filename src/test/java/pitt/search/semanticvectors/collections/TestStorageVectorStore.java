package pitt.search.semanticvectors.collections;

import org.junit.Test;
import pitt.search.semanticvectors.ObjectVector;
import pitt.search.semanticvectors.vectors.BinaryVector;
import pitt.search.semanticvectors.vectors.RealVector;
import pitt.search.semanticvectors.vectors.Vector;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;

public class TestStorageVectorStore {

	@Test
	public void testVectorStoreWithRealVectors() throws IOException {

		Random rand = new Random();
		int numVecs = 10;
		int dimension = 200;

		File tmp = Files.createTempFile("storage", "tmp").toFile();

		ModifiableVectorStore store = new FileVectorStore(tmp, new RealVectorBufferConverter(dimension));

		Map<String, Vector> vectors = new HashMap<>();

		for (int i = 0; i < numVecs; i++) {
			float[] coords = new float[dimension];
			for (int j = 0; j < dimension; j++) {
				coords[j] = rand.nextFloat();
			}
			String key = String.valueOf(i);
			RealVector vec = new RealVector(coords);

			vectors.put(key, vec);
			store.putVector(key, vec);
		}

		vectorsAreInStore(store, vectors);
	}

	@Test
	public void testVectorStoreWithBinaryVectors() throws IOException {

		Random rand = new Random();
		int numVecs = 10;
		int dimension = 4096;

		File tmp = Files.createTempFile("storage", "tmp").toFile();

		ModifiableVectorStore store = new FileVectorStore(tmp, new BinaryVectorBufferConverter(dimension));

		Map<String, Vector> vectors = new HashMap<>();

		for (int i = 0; i < numVecs; i++) {
			String key = String.valueOf(i);
			BinaryVector vec = new BinaryVector(dimension);
			vec = vec.generateRandomVector(dimension, dimension / 2, rand);

			vectors.put(key, vec);
			store.putVector(key, vec);
		}

		vectorsAreInStore(store, vectors);

	}

	private void vectorsAreInStore(ModifiableVectorStore store, Map<String, Vector> vectors) {
		for (Map.Entry<String, Vector> entry : vectors.entrySet()) {
			assertEquals(1.0, entry.getValue().measureOverlap(store.getVector(entry.getKey())), 0.0);
		}

		Enumeration<ObjectVector> allVectors = store.getAllVectors();

		while (allVectors.hasMoreElements()) {
			ObjectVector objectVector = allVectors.nextElement();

			assertEquals(1.0, vectors.get(objectVector.getObject()).measureOverlap(objectVector.getVector()), 0.0);
		}
	}
}
