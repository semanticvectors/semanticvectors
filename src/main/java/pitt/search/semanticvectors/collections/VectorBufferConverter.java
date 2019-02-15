package pitt.search.semanticvectors.collections;

import pitt.search.semanticvectors.vectors.Vector;

import java.nio.ByteBuffer;

public interface VectorBufferConverter {

	Vector readFromBuffer(ByteBuffer buffer);

	ByteBuffer writeToBuffer(Vector vector);

	int getBufferSizeInBytes();
}
