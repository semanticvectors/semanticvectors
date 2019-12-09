package pitt.search.semanticvectors;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


/**
 * This wrapper class is based on a solution given in the book Java Concurrency in Practice by Brian Goetz.
 * The solution in the book only takes two constructor parameters: an Executor and a bound used for the semaphore.
 * The semaphore acts as a guard, giving the effect of a virtual queue size.
 */
public final class BlockingExecutor {
	private final ExecutorService executor;
	private final Semaphore semaphore;

	public BlockingExecutor(int blockingQueueSize, int corePoolSize, int maxPoolSize, long keepAliveTime, TimeUnit unit) {
		// Do not create bounded queue, because of RejectedExecutionException.
		// Blocking will be handled by Semaphore
		BlockingQueue<Runnable> blockingQueue = new LinkedBlockingQueue<>();
		this.executor = new ThreadPoolExecutor(corePoolSize, maxPoolSize, keepAliveTime, unit, blockingQueue);
		this.semaphore = new Semaphore(blockingQueueSize + maxPoolSize);
	}

	private void execImpl (final Runnable command) throws InterruptedException {
		try {
			semaphore.acquireUninterruptibly();
			executor.execute(() -> {
				try {
					command.run();
				} finally {
					semaphore.release();
				}
			});
		} catch (RejectedExecutionException e) {
			// will never be thrown with an unbounded buffer (LinkedBlockingQueue)
			// only if registered shutdownHook is called
			semaphore.release();
			throw e;
		}
	}

	public void execute (Runnable command) throws InterruptedException {
		execImpl(command);
	}

	public void shutdown() throws IOException {
		executor.shutdown();
		try {
			executor.awaitTermination(7, TimeUnit.DAYS);
		} catch (InterruptedException e) {
			throw new IOException("Building index did not complete in 1 week");
		}
	}

	public void shutdownNow() throws InterruptedException {
		executor.shutdownNow();
		executor.awaitTermination(30, TimeUnit.SECONDS);
	}
}
