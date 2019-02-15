package pitt.search.semanticvectors.collections;

import pitt.search.semanticvectors.vectors.RealVector;
import pitt.search.semanticvectors.vectors.Vector;

import java.nio.ByteBuffer;

public class RealVectorBufferConverter implements VectorBufferConverter {

	private static final int BITS_IN_BYTE = 8;

	int dimension;
	int bufferSizeInBytes;

	public RealVectorBufferConverter(int dimension) {
		this.dimension = dimension;
		this.bufferSizeInBytes = dimension * Float.SIZE / BITS_IN_BYTE;
	}

	@Override
	public Vector readFromBuffer(ByteBuffer byteBuffer) {
		float[] coords = new float[dimension];
		byteBuffer.asFloatBuffer().get(coords);
		return new RealVector(coords);
	}

	@Override
	public ByteBuffer writeToBuffer(Vector vector) {
		float[] coords = ((RealVector) vector).getCoordinates();
		ByteBuffer byteBuffer = ByteBuffer.allocateDirect(bufferSizeInBytes);
		byteBuffer.asFloatBuffer().put(coords);
		return byteBuffer;
	}

	@Override
	public int getBufferSizeInBytes() {
		return bufferSizeInBytes;
	}
}
