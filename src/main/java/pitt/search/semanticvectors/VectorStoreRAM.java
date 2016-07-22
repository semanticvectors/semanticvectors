/**
   Copyright (c) 2008, Google Inc.

   All rights reserved.

   Redistribution and use in source and binary forms, with or without
   modification, are permitted provided that the following conditions are
   met:

   * Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.

   * Redistributions in binary form must reproduce the above
   copyright notice, this list of conditions and the following
   disclaimer in the documentation and/or other materials provided
   with the distribution.

   * Neither the name of the University of Pittsburgh nor the names
   of its contributors may be used to endorse or promote products
   derived from this software without specific prior written
   permission.

   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
   A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
   CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
   EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
   PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
   PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
   LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
   NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
   SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
**/

package pitt.search.semanticvectors;

import java.io.IOException;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import pitt.search.semanticvectors.utils.Distribution;
import pitt.search.semanticvectors.utils.VerbatimLogger;
import pitt.search.semanticvectors.vectors.*;

/**
   This class provides methods for reading a VectorStore into memory
   as an optimization if batching many searches. <p>

   The serialization currently presumes that the object (in the ObjectVectors)
   should be serialized as a String. <p>

   The class is constructed by creating a VectorStoreReader class,
   iterating through vectors and reading them into memory.
   @see VectorStoreReaderLucene
   @see ObjectVector
**/
public class VectorStoreRAM implements VectorStore {
  private static final Logger logger =
    Logger.getLogger(VectorStoreRAM.class.getCanonicalName());
  private FlagConfig flagConfig;
  private ConcurrentHashMap<Object, ObjectVector> objectVectors;
  private VectorType vectorType;
  private int dimension;
  /** Used for checking compatibility of new vectors. */
  private Vector zeroVector;
  
  public VectorStoreRAM(FlagConfig flagConfig) {
    this.objectVectors = new ConcurrentHashMap<Object, ObjectVector>();
    this.flagConfig = flagConfig;
    this.vectorType = flagConfig.vectortype();
    this.dimension = flagConfig.dimension();
    zeroVector = VectorFactory.createZeroVector(vectorType, dimension);
  }
    
  /**
   * Returns a new vector store, initialized from disk with the given vectorFile.
   *
   * Dimension and vector type from store on disk may overwrite any previous values in flagConfig.
   **/
  public static VectorStoreRAM readFromFile(FlagConfig flagConfig, String vectorFile) throws IOException {
    if (vectorFile.isEmpty()) {
      throw new IllegalArgumentException("vectorFile argument cannot be empty.");
    }
    VectorStoreRAM store = new VectorStoreRAM(flagConfig);
    store.initFromFile(vectorFile);
    return store;
  }
  
  /** Initializes a vector store from disk. */
  public void initFromFile(String vectorFile) throws IOException {
    CloseableVectorStore vectorReaderDisk = VectorStoreReader.openVectorStore(vectorFile, flagConfig);
    Enumeration<ObjectVector> vectorEnumeration = vectorReaderDisk.getAllVectors();
		
    logger.fine("Reading vectors from store on disk into memory cache  ...");
    while (vectorEnumeration.hasMoreElements()) {
      ObjectVector objectVector = vectorEnumeration.nextElement();
      this.objectVectors.put(objectVector.getObject().toString(), objectVector);
    }
    vectorReaderDisk.close();
    logger.log(Level.FINE, "Cached {0} vectors.", objectVectors.size());
  }
  
  /**
   * Adds a single vector with the given key and value.
   * Overwrites any existing vector with this key.
   */
  public void putVector(Object key, Vector vector) {
    IncompatibleVectorsException.checkVectorsCompatible(zeroVector, vector);
    ObjectVector objectVector = new ObjectVector(key, vector);
    this.objectVectors.put(key, objectVector);
  }

  @Override
  public Enumeration<ObjectVector> getAllVectors() {
    return this.objectVectors.elements();
  }

  @Override
  public int getNumVectors() {
    return this.objectVectors.size();
  }
  
  /**
   * Given an object, get its corresponding vector.
   * 
   * <p>
   * This implementation only works for string objects so far.
   * 
   * @param desiredObject - the string you're searching for
   * @return vector from the VectorStore, or null if not found. 
   */
  public Vector getVector(Object desiredObject) {
    ObjectVector objectVector = this.objectVectors.get(desiredObject);
    if (objectVector != null) {
      return objectVector.getVector();
    } else {
      return null;
    }
  }

  /**
   * Given an object, get its corresponding vector.
   *
   * <p>
   * This implementation only works for string objects so far.
   *
   * @param desiredObject - the string you're searching for
   * @return vector from the VectorStore, or new zero vector if not found.
   */
  public Vector getVectorOrZero(Object desiredObject) {
    ObjectVector objectVector = this.objectVectors.get(desiredObject);
    if (objectVector != null) {
      return objectVector.getVector();
    } else {
      this.putVector(desiredObject, VectorFactory.createZeroVector(this.vectorType, this.dimension));
      return this.getVector(desiredObject);
    }
  }

  /**
	 * Given an object, return its corresponding vector and remove it from the
	 * VectorStore. Does nothing and returns null if the object was not found.
	 * <p>
	 * This implementation only works for string objects so far.
	 * 
	 * @param desiredObject
	 *            - the string you're searching for
	 * @return vector from the VectorStore, or null if not found.
	 */
  public Vector removeVector(Object desiredObject) {
    ObjectVector objectVector = this.objectVectors.get(desiredObject);
    if (objectVector != null) {
      return objectVectors.remove(desiredObject).getVector();
    } else {
      return null;
    }
  }
  
  @Override
  public boolean containsVector(Object object) {
	  return objectVectors.containsKey(object);
  }

  /**
   * Creates a new vector store by redistributing the coordinates in the given vector store
   * to make the coordinates distributed roughly uniformly between -1 and 1.
   * @param source A vector store, must be of {@link VectorType#REAL} vectors.
   * @param sampleSize The number of vectors that will be used to create the approximate distributions.
   * @return A new vector store with rescaled coordinates.
   */
  public static VectorStoreRAM createRedistributedVectorStore(
      VectorStore source, FlagConfig flagConfig, int sampleSize) {
    if (flagConfig.vectortype() != VectorType.REAL) {
      throw new IllegalArgumentException("Vector store redistribution only works with VectorType.REAL vectors.");
    }

    if (source.getNumVectors() < sampleSize) {
      logger.info(String.format(
          "Source vector store only has %d elements, using all in sample.", source.getNumVectors()));
      sampleSize = source.getNumVectors();
    }

    // Get the first vectors into a sample, assuming that taking the first is a reasonably balanced thing to do.
    float[][] sampleCoordinates = new float[sampleSize][flagConfig.dimension()];
    Enumeration<ObjectVector> vectorEnumeration = source.getAllVectors();
    for (int i = 0; i < sampleSize; ++i) {
      sampleCoordinates[i] = ((RealVector) vectorEnumeration.nextElement().getVector()).getCoordinates();
    }

    // Create distributions for each coordinate.
    Distribution[] distributions = new Distribution[flagConfig.dimension()];
    for (int dim = 0; dim < flagConfig.dimension(); ++dim) {
      float[] coords = new float[sampleSize];
      for (int i = 0; i < sampleSize; ++i) {
        coords[i] = sampleCoordinates[i][dim];
      }
      distributions[dim] = new Distribution(coords);
    }

    // Create the new vector store with rescaled coordinates.
    VectorStoreRAM rescaledStore = new VectorStoreRAM(flagConfig);
    Enumeration<ObjectVector> vecEnum = source.getAllVectors();
    while (vecEnum.hasMoreElements()) {
      ObjectVector objectVector = vecEnum.nextElement();
      RealVector oldVector = (RealVector) objectVector.getVector();
      float[] oldCoords = oldVector.getCoordinates();
      float[] newCoords = new float[flagConfig.dimension()];
      for (int i = 0; i < flagConfig.dimension(); ++i) {
        // Remember to rescale so that values are normalized to between -1 and 1, not 0 and 1.
        newCoords[i] = (2 * distributions[i].getCumulativePosition(oldCoords[i])) - 1;
      }

      rescaledStore.putVector(objectVector.getObject(), new RealVector(newCoords));
    }

    VerbatimLogger.info("Created new vector store with redistributed coordinates.\n");
    return rescaledStore;
  }
}
