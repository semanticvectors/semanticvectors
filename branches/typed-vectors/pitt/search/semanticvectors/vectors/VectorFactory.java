package pitt.search.semanticvectors.vectors;

/**
 * Class for building 
 * 
 * @author widdows
 */
public class VectorFactory {

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
}
