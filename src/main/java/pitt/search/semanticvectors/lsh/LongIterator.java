package pitt.search.semanticvectors.lsh;

/**
 * We want to avoid autoboxing so this is a simple iterator which uses primitive long
 */
public interface LongIterator {

	boolean hasNext();

	long next();
}
