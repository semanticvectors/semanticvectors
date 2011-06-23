package pitt.search.semanticvectors.vectors;

import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;

enum VectorType {
  BINARY,
  REAL,
  COMPLEX;
}

/**
 * Base representation of a vector over a particular ground field and vector operations.
 * Designed to enable real, complex, and binary implementations. 
 * 
 * @author Dominic Widdows
 */
public interface Vector {  
  /** Returns the dimension of the vector. */
  public int getDimension();
  
  /**
   * Returns a canonical overlap measure (usually between 0 and 1) between this and other.
   */
  double measureOverlap(Vector other);
  
  /**
   * Returns a new Vector created by superposing all source vectors. Output is not normalized.
   */
  Vector superpose(Vector[] sources);
  
  /**
   * Transforms vector to a normalized representation.
   */
  void normalize();
  
  /**
   * Writes vector to Lucene output stream. Writes exactly {@link dimension} coordinates.
   */
  void writeToLuceneStream(IndexOutput outputStream);

  /**
   * Reads vector from Lucene input stream. Reads exactly {@link dimension} coordinates.
   */
  Vector readFromLuceneStream(IndexInput inputStream);
  
  /**
   * Writes vector to text representation. Writes exactly {@link dimension} coordinates.
   */
  void writeToString(IndexOutput outputStream);

  /**
   * Reads vector from text representation. Reads exactly {@link dimension} coordinates.
   */
  void readFromString(IndexInput inputStream);
}
