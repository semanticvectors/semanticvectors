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

import java.lang.Integer;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Random;
import java.util.logging.Logger;

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
public class VectorStoreSparseRAM implements VectorStore {
  private static final Logger logger = Logger.getLogger(
      VectorStoreSparseRAM.class.getCanonicalName());

  private Hashtable<String, short[]> sparseVectors;
  int dimension;
  int seedLength;

  // Default constructor.
  public VectorStoreSparseRAM(int dimension) {
    this.sparseVectors = new Hashtable<String, short[]>();
    this.dimension = dimension;
  }

  public Enumeration<String> getKeys() { return this.sparseVectors.keys(); }

  // Initialization routine.
  public void createRandomVectors (int numVectors, int seedLength) {
    this.seedLength = seedLength;

    Random random = new Random();

    logger.info("Creating store of sparse vectors  ...");
    for (int i = 0; i < numVectors; ++i) {
      short[] sparseVector = VectorUtils.generateRandomVector(seedLength, dimension, random);
      this.sparseVectors.put(Integer.toString(i), sparseVector);
    }
    logger.info("Created " + sparseVectors.size() + " sparse random vectors.");
  }

  public void putVector(String key, short[] sparseVector) {
    this.sparseVectors.put(key, sparseVector);
  }

  /**
   * Given an object, get its corresponding vector <br>
   * This implementation only works for string objects so far <br>
   * @param desiredObject - the string you're searching for
   * @return vector from the VectorStore, or null if not found. 
   */
  public float[] getVector(Object desiredObject) {
    short[] sparseVector = this.sparseVectors.get(desiredObject);
    if (sparseVector != null) {
      return VectorUtils.sparseVectorToFloatVector(sparseVector, dimension);
    } else {
      return null;
    }
  }

  /**
   * Returns the sparse vector without going through the float[] interface.
   */
  public short[] getSparseVector(Object desiredObject) {
    return this.sparseVectors.get(desiredObject);
  }


  public int getNumVectors() {
    return this.sparseVectors.size();
  }

  public Enumeration<ObjectVector> getAllVectors() {
    return new SparseVectorEnumeration(this);
  }

  /**
   * Implements the hasMoreElements() and nextElement() methods
   * to give Enumeration interface from sparse vector store.
   */
  public class SparseVectorEnumeration implements Enumeration<ObjectVector> {
    VectorStoreSparseRAM sparseVectorStore;
    Enumeration<String> keys;

    public SparseVectorEnumeration(VectorStoreSparseRAM sparseVectorStore) {
      this.sparseVectorStore = sparseVectorStore;
      this.keys = sparseVectorStore.getKeys();
    }

    public boolean hasMoreElements() {
      return this.keys.hasMoreElements();
    }

    public ObjectVector nextElement() {
      Object key = this.keys.nextElement();
      float[] vector = this.sparseVectorStore.getVector(key);
      return new ObjectVector(key, vector);
    }
  }
}
