package pitt.search.semanticvectors.vectors;

/**
 * Exception indicating an attempt to combine vectors of different types or dimensions.
 * 
 * @author widdows
 */
public class IncompatibleVectorsException extends RuntimeException {
  public IncompatibleVectorsException() {
    super();
  }

  public IncompatibleVectorsException(String message) {
    super(message + "\nThis almost certainly indicates a programming error!");
  }
  
  public static void checkVectorsCompatible(Vector first, Vector second) {    
    if (first.getClass() != second.getClass()) {
      throw new IncompatibleVectorsException("Trying to combine vectors of type: "
          + first.getClass().getCanonicalName() + ", " + second.getClass().getCanonicalName());
    }
    if (first.getDimension() != second.getDimension()) {
      throw new IncompatibleVectorsException("Trying to combine vectors of dimension: "
          + first.getDimension() + ", " + second.getDimension());
    }
  }
}
