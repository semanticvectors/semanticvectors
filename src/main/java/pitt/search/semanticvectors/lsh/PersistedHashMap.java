package pitt.search.semanticvectors.lsh;

import java.io.File;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * This is a {@link TreeMap} stored on the hard-drive. It supports keys {@link Short} and values list of longs ONLY.
 * It is quite fast - the performance is comparable to the {@link HashMap} java collection (if not better).
 */
public class PersistedHashMap {

	File cacheFile;
	MappedByteBuffer mbb;

	public PersistedHashMap(File file) {
		this.cacheFile = file;
	}

	public void init() throws IOException {
		if (cacheFile.exists() && cacheFile.length() != 0) {
			try (FileChannel fileChannel = FileChannel.open(cacheFile.toPath())) {
				mbb = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, cacheFile.length());
			}
		}
	}

	public void persist(TreeMap<Short, Collection<Long>> map) throws IOException {
		long size = calculateSize(map);

		if (!cacheFile.exists() || cacheFile.length() == 0) {
			try (FileChannel fileChannel = FileChannel.open(cacheFile.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
				MappedByteBuffer wmbb = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, size);

				writeHeader(wmbb, map);
				writeData(wmbb, map);

				wmbb.clear();
				DirectByteBufferCleaner.closeDirectByteBuffer(wmbb);
			}
		} else {
			if (size != cacheFile.length())
				throw new IOException("The file does not match the map length");
		}

		try (FileChannel fileChannel = FileChannel.open(cacheFile.toPath())) {
			mbb = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, size);
		}
	}

	public LongIterator get(short key) {
		mbb.position(0);
		long headerSizeInBytes = mbb.getLong();

		while (mbb.position() < headerSizeInBytes) {
			long bucketId = mbb.getShort();
			if (bucketId == key) {
				long start = mbb.getLong();
				long end;
				if (mbb.position() == headerSizeInBytes) {
					end = mbb.limit();
				} else {
					end = mbb.getLong(mbb.position() + Short.BYTES);
				}
				return new Range(start, end).iterator();
			}
			mbb.position(mbb.position() + Long.BYTES);
		}

		return new LongIterator() {
			@Override
			public boolean hasNext() {
				return false;
			}

			@Override
			public long next() {
				return 0;
			}
		};
	}

	public void close() {
		mbb.clear();
		DirectByteBufferCleaner.closeDirectByteBuffer(mbb);
	}

	public List<Short> keys() {
		List<Short> keys = new LinkedList<>();

		mbb.position(0);
		long headerSize = mbb.getLong();
		while (mbb.position() < headerSize) {
			keys.add(mbb.getShort());
			mbb.position(mbb.position() + Long.BYTES);
		}
		return keys;
	}

	private long calculateSize(TreeMap<Short, Collection<Long>> map) {
		long size = 0;
		size += Long.BYTES;
		for (Collection<Long> value : map.values()) {
			size += Short.BYTES + Long.BYTES;
			size += value.size() * Long.BYTES;
		}
		return size;
	}

	private void writeHeader(MappedByteBuffer wmbb, TreeMap<Short, Collection<Long>> storeHash) {
		long headerSizeInBytes = storeHash.size() * (Short.BYTES + Long.BYTES) + Long.BYTES;
		wmbb.putLong(headerSizeInBytes);

		long prevBucketEnd = headerSizeInBytes;
		for (Map.Entry<Short, Collection<Long>> integerListEntry : storeHash.entrySet()) {
			wmbb.putShort(integerListEntry.getKey());
			wmbb.putLong(prevBucketEnd);
			prevBucketEnd += integerListEntry.getValue().size() * Long.BYTES;
		}
	}

	private void writeData(MappedByteBuffer wmbb, TreeMap<Short, Collection<Long>> storeHash) {
		for (Collection<Long> value : storeHash.values()) {
			for (Long aLong : value) {
				wmbb.putLong(aLong);
			}
		}
	}


	private class Range {
		long start, end;

		public Range(long start, long end) {
			this.start = start;
			this.end = end;
		}

		public LongIterator iterator() {
			mbb.position((int) start);

			return new LongIterator() {
				@Override
				public boolean hasNext() {
					return mbb.position() < end;
				}

				@Override
				public long next() {
					return mbb.getLong();
				}
			};
		}
	}

}
