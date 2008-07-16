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
import java.lang.Float;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.StringTokenizer;

/**
   This class provides methods for reading a VectorStore into memory
   as an optimization if batching many searches. <p>

   The serialization currently presumes that the object (in the ObjectVectors)
   should be serialized as a String. <p>

   The class is constructed by creating a VectorStoreReader class,
   iterating through vectors and reading them into memory.
	 @see VectorStoreReader
   @see ObjectVector
**/
public class VectorStoreRAM implements VectorStore {
	private Hashtable<Object, ObjectVector> objectVectors;

	// Default constructor.
	public VectorStoreRAM() {
    this.objectVectors = new Hashtable<Object, ObjectVector>();
	};

	// Initialization routine.
  public void InitFromFile (String vectorFile) throws IOException {
		VectorStoreReader vectorReaderDisk = new VectorStoreReader(vectorFile);
		Enumeration<ObjectVector> vectorEnumeration = vectorReaderDisk.getAllVectors();
		
		System.err.println("Reading vectors from store on disk into memory cache  ...");
		while (vectorEnumeration.hasMoreElements()) {
			ObjectVector objectVector = vectorEnumeration.nextElement();
			this.objectVectors.put(objectVector.getObject().toString(), objectVector);
		}
		System.err.println("Cached " + objectVectors.size() + " vectors.");
  }

	// Add a single vector.
	public void addVector(Object key, float[] vector) {
		ObjectVector objectVector = new ObjectVector(key, vector);
		this.objectVectors.put(key, objectVector);
	}

  public Enumeration getAllVectors() {
    return this.objectVectors.elements();
  }

	public int getNumVectors() {
		return this.objectVectors.size();
	}

  /**
   * Given an object, get its corresponding vector <br>
   * This implementation only works for string objects so far <br>
   * @param desiredObject - the string you're searching for
	 * @return vector from the VectorStore, or null if not found. 
   */
  public float[] getVector(Object desiredObject) {
		ObjectVector objectVector = this.objectVectors.get(desiredObject);
		if (objectVector != null)
			return objectVector.getVector();
		else
			return null;
  }
}
