package pitt.search.semanticvectors.lsh;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;


public class TestPersistedHashMap {

	@Test
	public void testWrite() throws IOException {
		File tmp = Files.createTempFile("test", "file").toFile();
		tmp.deleteOnExit();

		TreeMap<Short, Collection<Long>> controlMap = new TreeMap<>();
		PersistedHashMap persistedMap = new PersistedHashMap(tmp);

		Random random = new Random();

		for (int i = 0; i < 100; i++) {
			List<Long> listLong = new LinkedList<>();
			for (int j = 0; j < random.nextInt(1000); j++) {
				listLong.add(random.nextLong());
			}
			controlMap.put((short) random.nextInt(Short.MAX_VALUE), listLong);
		}

		persistedMap.persist(controlMap);

		for (int i = 0; i < 100; i++) {
			Object[] keys = controlMap.keySet().toArray();
			short key = (short) keys[random.nextInt(keys.length)];

			LongIterator iter = persistedMap.get(key);
			Iterator<Long> controlIter = controlMap.get(key).iterator();

			while (iter.hasNext()) {
				assertEquals(iter.next(), (long) controlIter.next());
			}

			assertFalse(controlIter.hasNext());
		}
	}

	@Test
	public void testWithNonExistingFile() throws IOException {
		File tmp = Files.createTempFile("test", "file").toFile();
		tmp.delete();

		TreeMap<Short, Collection<Long>> controlMap = new TreeMap<>();
		PersistedHashMap persistedMap = new PersistedHashMap(tmp);

		Random random = new Random();

		for (int i = 0; i < 100; i++) {
			List<Long> listLong = new LinkedList<>();
			for (int j = 0; j < random.nextInt(1000); j++) {
				listLong.add(random.nextLong());
			}
			controlMap.put((short) random.nextInt(Short.MAX_VALUE), listLong);
		}

		persistedMap.persist(controlMap);

		for (int i = 0; i < 100; i++) {
			Object[] keys = controlMap.keySet().toArray();
			short key = (short) keys[random.nextInt(keys.length)];

			LongIterator iter = persistedMap.get(key);
			Iterator<Long> controlIter = controlMap.get(key).iterator();

			while (iter.hasNext()) {
				assertEquals(iter.next(), (long) controlIter.next());
			}

			assertFalse(controlIter.hasNext());
		}

		assertEquals(persistedMap.keys().size(), controlMap.keySet().size());
		for (Short key : persistedMap.keys()) {
			controlMap.keySet().contains(key);
		}
	}

	@Test
	public void testRestoreMap() throws IOException {
		File tmp = Files.createTempFile("test", "file").toFile();
		tmp.deleteOnExit();

		TreeMap<Short, Collection<Long>> controlMap = new TreeMap<>();
		PersistedHashMap persistedMap = new PersistedHashMap(tmp);

		Random random = new Random();

		for (int i = 0; i < 100; i++) {
			List<Long> listLong = new LinkedList<>();
			for (int j = 0; j < random.nextInt(1000); j++) {
				listLong.add(random.nextLong());
			}
			controlMap.put((short) random.nextInt(Short.MAX_VALUE), listLong);
		}

		persistedMap.persist(controlMap);
		persistedMap.close();

		persistedMap = new PersistedHashMap(tmp);
		persistedMap.init();

		for (int i = 0; i < 100; i++) {
			Object[] keys = controlMap.keySet().toArray();
			short key = (short) keys[random.nextInt(keys.length)];

			LongIterator iter = persistedMap.get(key);
			Iterator<Long> controlIter = controlMap.get(key).iterator();

			while (iter.hasNext()) {
				assertEquals(iter.next(), (long) controlIter.next());
			}

			assertFalse(controlIter.hasNext());
		}
	}

	@Test
	public void perfTest() throws IOException {
		File tmp = Files.createTempFile("test", "file").toFile();
		tmp.deleteOnExit();

		TreeMap<Short, Collection<Long>> controlMap = new TreeMap<>();
		PersistedHashMap persistedMap = new PersistedHashMap(tmp);

		Random random = new Random();

		for (int i = 0; i < 1000; i++) {
			List<Long> listLong = new LinkedList<>();
			for (int j = 0; j < random.nextInt(10000); j++) {
				listLong.add(random.nextLong());
			}
			controlMap.put((short) random.nextInt(Short.MAX_VALUE), listLong);
		}

		persistedMap.persist(controlMap);

		Instant start = Instant.now();
		for (int i = 0; i < 10000; i++) {
			Object[] keys = controlMap.keySet().toArray();
			short key = (short) keys[random.nextInt(keys.length)];

			Iterator<Long> controlIter = controlMap.get(key).iterator();

			while (controlIter.hasNext()) {
				controlIter.next();
			}

			assertFalse(controlIter.hasNext());
		}
		System.out.println("control in ms: " + Duration.between(start, Instant.now()).toMillis());

		start = Instant.now();
		for (int i = 0; i < 10000; i++) {
			Object[] keys = controlMap.keySet().toArray();
			short key = (short) keys[random.nextInt(keys.length)];

			LongIterator controlIter = persistedMap.get(key);

			while (controlIter.hasNext()) {
				controlIter.next();
			}

			assertFalse(controlIter.hasNext());
		}
		System.out.println("persisted in ms: " + Duration.between(start, Instant.now()).toMillis());
	}

}
