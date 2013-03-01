package pitt.search.semanticvectors;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Random;

import pitt.search.semanticvectors.hashing.Bobcat;
import pitt.search.semanticvectors.infer.NumberRepresentation;
import pitt.search.semanticvectors.infer.StringEdit;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorType;

/**
 * This class provides methods for retrieving vectors that are computed on the
 * fly in a deterministic way. To save time, vectors are cached by default.
 * Methods exist to disable/enable caching and to clear the cache.
 * <p>
 * 
 * The serialization currently presumes that the object (in the ObjectVectors)
 * should be serialized as a String.
 * <p>
 * 
 * 
 * @see ObjectVector
 **/
public class VectorStoreOrthographical implements VectorStore {
  private FlagConfig flagConfig;
  private Hashtable<Object, ObjectVector> objectVectors;
  private Random random = new Random();
  private VectorType vectorType;
  private int dimension;
  private boolean cacheVectors = true;
 private NumberRepresentation theNumbers;
 private VectorStoreRAM letterVectors;
  
  public VectorStoreOrthographical(FlagConfig flagConfig) {
    this.flagConfig = flagConfig;
    this.objectVectors = new Hashtable<Object, ObjectVector>();
    this.vectorType = flagConfig.vectortype();
    this.dimension = flagConfig.dimension();
    this.theNumbers = new NumberRepresentation(flagConfig);
    this.letterVectors = new VectorStoreRAM(flagConfig);
  }

  @Override
  public VectorType getVectorType() {
    return vectorType;
  }

  @Override
  public int getDimension() {
    return dimension;
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
      
         Vector v =     StringEdit.getStringVector(desiredObject.toString(), theNumbers.getNumberVectors(1, desiredObject.toString().length()), letterVectors, flagConfig);
        		 
        		 if (cacheVectors)
         objectVectors.put(desiredObject, new ObjectVector(
             desiredObject, v));
       return v;
     }
   }
}
