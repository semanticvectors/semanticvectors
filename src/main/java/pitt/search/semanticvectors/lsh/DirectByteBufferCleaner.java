package pitt.search.semanticvectors.lsh;


import pitt.search.semanticvectors.utils.VerbatimLogger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;

public class DirectByteBufferCleaner {
	private static boolean PRE_JAVA_9 =
			System.getProperty("java.specification.version","9").startsWith("1.");

	private static Method cleanMethod;
	private static Method attachmentMethod;
	private static Object theUnsafe;

	static void getCleanMethodPrivileged() {
		if (PRE_JAVA_9) {
			try {
				cleanMethod = Class.forName("sun.misc.Cleaner").getMethod("clean");
				cleanMethod.setAccessible(true);
				final Class<?> directByteBufferClass =
						Class.forName("sun.nio.ch.DirectBuffer");
				attachmentMethod = directByteBufferClass.getMethod("attachment");
				attachmentMethod.setAccessible(true);
			} catch (final Exception ex) {
			}
		} else {
			try {
				Class<?> unsafeClass;
				try {
					unsafeClass = Class.forName("sun.misc.Unsafe");
				} catch (Exception e) {
					// jdk.internal.misc.Unsafe doesn't yet have invokeCleaner(),
					// but that method should be added if sun.misc.Unsafe is removed.
					unsafeClass = Class.forName("jdk.internal.misc.Unsafe");
				}
				cleanMethod = unsafeClass.getMethod("invokeCleaner", ByteBuffer.class);
				cleanMethod.setAccessible(true);
				final Field theUnsafeField = unsafeClass.getDeclaredField("theUnsafe");
				theUnsafeField.setAccessible(true);
				theUnsafe = theUnsafeField.get(null);
			} catch (final Exception ex) {
			}
		}
	}

	static {
		AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
			getCleanMethodPrivileged();
			return null;
		});
	}

	private static boolean closeDirectByteBufferPrivileged(
			final ByteBuffer byteBuffer) {
		try {
			if (cleanMethod == null) {
				VerbatimLogger.severe("Could not unmap ByteBuffer, cleanMethod == null");
				return false;
			}
			if (PRE_JAVA_9) {
				if (attachmentMethod == null) {
					VerbatimLogger.severe("Could not unmap ByteBuffer, attachmentMethod == null");
					return false;
				}
				// Make sure duplicates and slices are not cleaned, since this can result in
				// duplicate attempts to clean the same buffer, which trigger a crash with:
				// "A fatal error has been detected by the Java Runtime Environment:
				// EXCEPTION_ACCESS_VIOLATION"
				// See: https://stackoverflow.com/a/31592947/3950982
				if (attachmentMethod.invoke(byteBuffer) != null) {
					// Buffer is a duplicate or slice
					return false;
				}
				// Invoke ((DirectBuffer) byteBuffer).cleaner().clean()
				final Method cleaner = byteBuffer.getClass().getMethod("cleaner");
				cleaner.setAccessible(true);
				cleanMethod.invoke(cleaner.invoke(byteBuffer));
				return true;
			} else {
				if (theUnsafe == null) {
					VerbatimLogger.severe("Could not unmap ByteBuffer, theUnsafe == null");
					return false;
				}
				// In JDK9+, calling the above code gives a reflection warning on stderr,
				// need to call Unsafe.theUnsafe.invokeCleaner(byteBuffer) , which makes
				// the same call, but does not print the reflection warning.
				try {
					cleanMethod.invoke(theUnsafe, byteBuffer);
					return true;
				} catch (final IllegalArgumentException e) {
					// Buffer is a duplicate or slice
					return false;
				}
			}
		} catch (final Exception e) {
			VerbatimLogger.severe("Could not unmap ByteBuffer: " + e);
			return false;
		}
	}

	/**
	 * Close a {@code DirectByteBuffer} -- in particular, will unmap a
	 * {@link java.nio.MappedByteBuffer}.
	 *
	 * @param byteBuffer
	 *            The {@link ByteBuffer} to close/unmap.
	 * @return True if the byteBuffer was closed/unmapped (or if the ByteBuffer
	 *            was null or non-direct).
	 */
	public static boolean closeDirectByteBuffer(final ByteBuffer byteBuffer) {
		if (byteBuffer != null && byteBuffer.isDirect()) {
			AccessController.doPrivileged((PrivilegedAction<Boolean>) () -> closeDirectByteBufferPrivileged(byteBuffer));
			return true;
		} else {
			// Nothing to unmap
			return false;
		}
	}
}
