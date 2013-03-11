package pitt.search.semanticvectors;

import java.util.Enumeration;
import java.util.Hashtable;

import pitt.search.semanticvectors.orthography.NumberRepresentation;
import pitt.search.semanticvectors.orthography.StringEdit;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorType;

/**
 * This class provides methods for retrieving vectors that are computed
 * using the orthographical techniques in {@link NumberRepresentation}.
 * 
 * <p>
 * To save time, vectors are cached by default.
 * Methods exist to disable/enable caching and to clear the cache.
 * 
 * <p>
 * The serialization currently presumes that the object (in the ObjectVectors)
 * should be serialized as a String.
 * 
 * @see ObjectVector
 **/
public class VectorStoreOrthographical implements VectorStore, CloseableVectorStore {
  private FlagConfig flagConfig;
  private Hashtable<Object, ObjectVector> objectVectors;
  private boolean cacheVectors = true;
  private NumberRepresentation theNumbers;
  private VectorStoreRAM letterVectors;

  public VectorStoreOrthographical(FlagConfig flagConfig) {
    this.flagConfig = flagConfig;
    if (flagConfig.vectortype() == VectorType.REAL) {
      VerbatimLogger.warning("Not yet implemented for real vectors");
      VerbatimLogger.warning("Changing to 4096-dimensional binary vectors");
      flagConfig.setVectortype(VectorType.BINARY);
      flagConfig.setDimension(4096);
    }

    this.objectVectors = new Hashtable<Object, ObjectVector>();
    this.theNumbers = new NumberRepresentation(flagConfig);
    this.letterVectors = new VectorStoreRAM(flagConfig);
  }

  @Override
  public VectorType getVectorType() {
    return flagConfig.vectortype();
  }

  @Override
  public void close()
  {
    //dummy method to ease integration into existing command line query code
  }

  @Override
  public int getDimension() {
    return flagConfig.dimension();
  }

  public Enumeration<ObjectVector> getAllVectors() {
    return this.objectVectors.elements();
  }

  @Override
  public int getNumVectors() {
    return this.objectVectors.size();
  }

  /**
   * Clear the vector cache.
   */
  public void clear() {
    objectVectors.clear();
  }

  /**
   * Enable or disable vector caching. Enabled cache speeds up repeated
   * querying of the same vector, but increases memory footprint. Cache can be
   * cleared with {@link #clear()}. By default the cache is enabled.
   * 
   * @param cacheVectors <code>true</code> to enable the cache,
   *        <code>false</code> otherwise
   */
  public void enableVectorCache(boolean cacheVectors) {
    this.cacheVectors = cacheVectors;
  }

  /**
   * Given an object, get its corresponding vector.
   * <p>
   * This implementation only works for string objects so far.
   * 
   * @param desiredObject the string you're searching for
   * @return vector from the VectorStore, or null if not found.
   * @throws NullPointerException if desiredObject or vector is
   *         <code>null</code>
   */
  public Vector getVector(Object desiredObject) throws NullPointerException {
    ObjectVector objectVector = this.objectVectors.get(desiredObject);
    if (objectVector != null) {
      return objectVector.getVector();
    } else {      
      Vector v = StringEdit.getStringVector(
          desiredObject.toString(),
          theNumbers.getNumberVectors(1, desiredObject.toString().length()),
          letterVectors, flagConfig);

      if (cacheVectors) {
        objectVectors.put(desiredObject, new ObjectVector(desiredObject, v));
      }
      return v;
    }
  }
}
