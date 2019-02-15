package pitt.search.semanticvectors.collections;

import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import pitt.search.semanticvectors.vectors.BinaryVector;
import pitt.search.semanticvectors.vectors.Vector;

import java.io.IOException;
import java.nio.ByteBuffer;

public class BinaryVectorBufferConverter implements VectorBufferConverter {

	private static final int BITS_IN_BYTE = 8;

	int dimension;
	int byteBufferInBytes;

	public BinaryVectorBufferConverter(int dimension) {
		this.dimension = dimension;
		this.byteBufferInBytes = dimension / BITS_IN_BYTE;
	}

	@Override
	public Vector readFromBuffer(ByteBuffer buffer) {
		BinaryVector vector = new BinaryVector(dimension);

		IndexInput input = new ByteBufferToIndexInput(buffer);
		vector.readFromLuceneStream(input);
		buffer.flip();

		return vector;
	}

	@Override
	public ByteBuffer writeToBuffer(Vector vector) {
		BinaryVector binaryVector = (BinaryVector) vector;
		binaryVector.normalize();

		ByteBuffer byteBuffer = ByteBuffer.allocateDirect(byteBufferInBytes);
		binaryVector.writeToLuceneStream(new ByteBufferToIndexOutput(byteBuffer));
		byteBuffer.flip();

		return byteBuffer;
	}

	@Override
	public int getBufferSizeInBytes() {
		return byteBufferInBytes;
	}

	private static class ByteBufferToIndexOutput extends IndexOutput {

		private ByteBuffer buffer;

		public ByteBufferToIndexOutput(ByteBuffer buffer) {
			super("vec", "vec");
			this.buffer = buffer;
		}

		@Override
		public void close() throws IOException {
			throw new RuntimeException("This method is no supposed to be called: " + Thread.currentThread().getStackTrace()[1].getMethodName());

		}

		@Override
		public long getFilePointer() {
			throw new RuntimeException("This method is no supposed to be called: " + Thread.currentThread().getStackTrace()[1].getMethodName());
		}

		@Override
		public long getChecksum() throws IOException {
			throw new RuntimeException("This method is no supposed to be called: " + Thread.currentThread().getStackTrace()[1].getMethodName());
		}

		@Override
		public void writeByte(byte b) throws IOException {
			buffer.put(b);
		}

		@Override
		public void writeBytes(byte[] bytes, int i, int i1) throws IOException {
			throw new RuntimeException("This method is no supposed to be called: " + Thread.currentThread().getStackTrace()[1].getMethodName());
		}
	}

	private static class ByteBufferToIndexInput extends IndexInput {

		private ByteBuffer buffer;

		public ByteBufferToIndexInput(ByteBuffer buffer) {
			super("vec");
			this.buffer = buffer;
		}

		@Override
		public void close() throws IOException {
			throw new RuntimeException("This method is no supposed to be called: " + Thread.currentThread().getStackTrace()[1].getMethodName());
		}

		@Override
		public long getFilePointer() {
			throw new RuntimeException("This method is no supposed to be called: " + Thread.currentThread().getStackTrace()[1].getMethodName());
		}

		@Override
		public void seek(long l) throws IOException {
			throw new RuntimeException("This method is no supposed to be called: " + Thread.currentThread().getStackTrace()[1].getMethodName());
		}

		@Override
		public long length() {
			throw new RuntimeException("This method is no supposed to be called: " + Thread.currentThread().getStackTrace()[1].getMethodName());
		}

		@Override
		public IndexInput slice(String s, long l, long l1) throws IOException {
			throw new RuntimeException("This method is no supposed to be called: " + Thread.currentThread().getStackTrace()[1].getMethodName());
		}

		@Override
		public byte readByte() throws IOException {
			return buffer.get();
		}

		@Override
		public void readBytes(byte[] bytes, int i, int i1) throws IOException {
			throw new RuntimeException("This method is no supposed to be called: " + Thread.currentThread().getStackTrace()[1].getMethodName());
		}

	}
}
