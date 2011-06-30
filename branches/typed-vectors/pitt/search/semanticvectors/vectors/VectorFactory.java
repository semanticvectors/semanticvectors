package pitt.search.semanticvectors.vectors;

import java.util.Random;

/**
 * Class for building 
 * 
 * @author widdows
 */
public class VectorFactory {
  private static final RealVector realInstance = new RealVector(0);
  
  public static Vector createZeroVector(VectorType type, int dimension) {
    switch (type) {
      case BINARY:
        return null;
      case REAL:
        return new RealVector(dimension);
      case COMPLEX:
        return null;
      default:
        throw new IllegalArgumentException("Unrecognized VectorType: " + type);
    }
  }
  
  public static Vector generateRandomVector(
      VectorType type, int dimension, int numEntries, Random random) {
    if (2 * numEntries >= dimension) {
      throw new RuntimeException("Requested " + numEntries + " to be filled in sparse "
          + "vector of dimension " + dimension + ". This is not sparse and may cause problems.");
    }
    switch (type) {
    case BINARY:
      return null;
    case REAL:
      return realInstance.generateRandomVector(dimension, numEntries, random);
    case COMPLEX:
      return null;
    default:
      throw new IllegalArgumentException("Unrecognized VectorType: " + type);
    }
  }
}
