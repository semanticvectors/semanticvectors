package pitt.search.semanticvectors.collections;

import com.ontotext.graphdb.Config;
import com.ontotext.trree.sdk.PluginException;
import pitt.search.semanticvectors.CloseableVectorStore;
import pitt.search.semanticvectors.*;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorType;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Random;

public class VectorStoreFactory {

	public static ModifiableVectorStore getVectorStore(FlagConfig config) {
		VectorBufferConverter converter = null;
		if (config.vectortype() == VectorType.REAL) {
			converter = new RealVectorBufferConverter(config.dimension());
		}

		if (converter != null) {
			try {
				File tempFile = Config.createTempFile("vecReader", ".tmp");
				FileVectorStore fvs = new FileVectorStore(tempFile, converter);
				return fvs;
			} catch (IOException e) {
				throw new PluginException("Could not create file for vector store", e);
			}
		}

		return new VectorStoreRAMWrapper(new VectorStoreRAM(config));
	}

	public static CloseableVectorStore getElementalVectorStore(FlagConfig config) {
		// Only when elementalmethod is RANDOM we can replace the backing store. When the vectors are REAL we can leave them
		// in the RAM as they are stored in sparse form which is not so memory consuming
		if (config.elementalmethod() == ElementalVectorStore.ElementalGenerationMethod.RANDOM && config.vectortype() == VectorType.BINARY) {
			return new RandomBinaryElementalVectorStore(config);
		}

		return new ElementalVectorStoreWrapper(new ElementalVectorStore(config));
	}

	private static class VectorStoreRAMWrapper implements ModifiableVectorStore {

		private VectorStoreRAM wrapped;

		public VectorStoreRAMWrapper(VectorStoreRAM wrapped) {
			this.wrapped = wrapped;
		}

		@Override
		public void putVector(Object key, Vector vector) {
			wrapped.putVector(key, vector);
		}

		@Override
		public Enumeration<ObjectVector> getAllVectors() {
			return wrapped.getAllVectors();
		}

		@Override
		public int getNumVectors() {
			return wrapped.getNumVectors();
		}

		@Override
		public Vector getVector(Object desiredObject) {
			return wrapped.getVector(desiredObject);
		}

		@Override
		public boolean containsVector(Object object) {
			return wrapped.containsVector(object);
		}

		@Override
		public void updateVector(Object key, Vector vector) {
			// ignore
		}

		@Override
		public void flush() {
			// ignore
		}

		@Override
		public void close() {
			// ignore
		}

	}

	private static class ElementalVectorStoreWrapper implements CloseableVectorStore {
		private ElementalVectorStore wrapped;

		public ElementalVectorStoreWrapper(ElementalVectorStore wrapped) {
			this.wrapped = wrapped;
		}

		@Override
		public Vector getVector(Object term) {
			return wrapped.getVector(term);
		}

		@Override
		public Enumeration<ObjectVector> getAllVectors() {
			return wrapped.getAllVectors();
		}

		@Override
		public int getNumVectors() {
			return wrapped.getNumVectors();
		}

		@Override
		public boolean containsVector(Object object) {
			return wrapped.containsVector(object);
		}

		@Override
		public void close() {
			// ignore
		}
	}

	/**
	 * This is a copy of the {@link ElementalVectorStore} class. It is not sexy but no better way to replace the
	 * private backingStore variable... <br>
	 * It should be used only when<br>
	 * config.elementalmethod() == ElementalVectorStore.ElementalGenerationMethod.RANDOM && config.vectortype() == VectorType.BINARY
	 */
	private static class RandomBinaryElementalVectorStore implements CloseableVectorStore {

		private Random random;
		private FlagConfig flagConfig;
		private ModifiableVectorStore backingStore;

		public RandomBinaryElementalVectorStore(FlagConfig flagConfig) {
			this.flagConfig = flagConfig;
			// We will deleteOnExit the temp file as the VectorStore interface does not offer much
			backingStore = VectorStoreFactory.getVectorStore(flagConfig);
			random = new Random();
		}

		@Override
		public Vector getVector(Object term) {
			Vector vector = backingStore.getVector(term);
			if (vector == null) {
				vector = VectorFactory.generateRandomVector(
						flagConfig.vectortype(), flagConfig.dimension(), flagConfig.seedlength(), random);
				backingStore.putVector(term, vector);
			}
			return vector;
		}

		@Override
		public Enumeration<ObjectVector> getAllVectors() {
			return backingStore.getAllVectors();
		}

		@Override
		public int getNumVectors() {
			return backingStore.getNumVectors();
		}

		@Override
		public boolean containsVector(Object object) {
			return backingStore.containsVector(object);
		}

		@Override
		public void close() {
			backingStore.close();
		}
	}
}
