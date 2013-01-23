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
import java.util.Hashtable;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import pitt.search.semanticvectors.vectors.IncompatibleVectorsException;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorType;

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
  private Hashtable<Object, ObjectVector> objectVectors;
  private VectorType vectorType;
  private int dimension;
  /** Used for checking compatibility of new vectors. */
  private Vector zeroVector;
  
  @Override
  public VectorType getVectorType() { return vectorType; }
  
  @Override
  public int getDimension() { return dimension; }
  
  public VectorStoreRAM(FlagConfig flagConfig) {
    this.objectVectors = new Hashtable<Object, ObjectVector>();
    this.flagConfig = flagConfig;
    this.vectorType = VectorType.valueOf(flagConfig.getVectortype().toUpperCase());
    this.dimension = flagConfig.getDimension();
    zeroVector = VectorFactory.createZeroVector(vectorType, dimension);
  }
  
  // Initialization routine.
  public void initFromFile (String vectorFile) throws IOException {
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

  // Initialization routine.
  public void createRandomVectors (int numVectors, int seedLength, Random random) {
    if (random == null) { random = new Random(); }
    VerbatimLogger.fine("Creating store of " + numVectors + " elemental vectors  ...\n");
    for (int i = 0; i < numVectors; ++i) {
      this.objectVectors.put(Integer.toString(i),
                             new ObjectVector(Integer.toString(i),
                                 VectorFactory.generateRandomVector(vectorType, dimension,
                                                                    seedLength, random)));
    }
  }
  
  // Add a single vector.
  public void putVector(Object key, Vector vector) {
    IncompatibleVectorsException.checkVectorsCompatible(zeroVector, vector);
    ObjectVector objectVector = new ObjectVector(key, vector);
    this.objectVectors.put(key, objectVector);
  }

  public Enumeration<ObjectVector> getAllVectors() {
    return this.objectVectors.elements();
  }

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
}
