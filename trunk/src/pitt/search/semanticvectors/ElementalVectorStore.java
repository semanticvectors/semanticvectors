/**
   Copyright 2013, the SemanticVectors AUTHORS.
   All rights reserved.

   Redistribution and use in source and binary forms, with or without
   modification, are permitted provided that the following conditions are
   met:

 * Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.

 * Redistributions in binary form must reproduce the above
   copyright notice, this list of conditions and the following disclaimer
   in the documentation and/or other materials provided with the
   distribution.

 * Neither the name of Google Inc. nor the names of its
   contributors may be used to endorse or promote products derived from
   this software without specific prior written permission.

   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
   A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
   OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
   SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
   LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
   DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
   THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
   (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
   THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 **/
package pitt.search.semanticvectors;

import java.util.Enumeration;
import java.util.Random;

import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;

/**
 * Encapsulates the generation of elemental vectors.
 * See the {@link ElementalGenerationMethod} for configuration options.
 * 
 * @author dwiddows
 */
public class ElementalVectorStore implements VectorStore{

  public enum ElementalGenerationMethod {
    /** Generate elemental vectors randomly, and stores in a {@link VectorStoreRAM} */
    RANDOM,
    /** Generate elemental vectors using a hash of the contents.
     * See {@link VectorStoreDeterministic}. */
    CONTENTHASH,
    /** Generate elemental vectors using the orthography of the string.
     * See {@link VectorStoreOrthographical} */
    ORTHOGRAPHIC
  }

  private Random random;
  private final FlagConfig flagConfig;
  private VectorStore backingStore;

  /**
   * Constructs a new instance with the given config.
   */
  public ElementalVectorStore(FlagConfig flagConfig) {
    this.flagConfig = flagConfig;
    switch(flagConfig.elementalmethod()) {
    case RANDOM:
      backingStore = new VectorStoreRAM(flagConfig);
      random = new Random();
      break;
    case CONTENTHASH:
      backingStore = new VectorStoreDeterministic(flagConfig);
      break;
    case ORTHOGRAPHIC:
      backingStore = new VectorStoreOrthographical(flagConfig);
      break;
    }
  }
  
  /**
   * Returns a vector for the given object, generating an appropriate elemental vector
   * if the term does not already have one.
   */
  @Override
  public Vector getVector(Object term) {
    switch(flagConfig.elementalmethod()) {
    case RANDOM:
      Vector vector = backingStore.getVector(term);
      if (vector == null) {
        vector = VectorFactory.generateRandomVector(
            flagConfig.vectortype(), flagConfig.dimension(), flagConfig.seedlength(), random);
        VectorStoreRAM ramCache = (VectorStoreRAM) backingStore;
        ramCache.putVector(term, vector);
      }
      return vector;
    case CONTENTHASH:
    case ORTHOGRAPHIC:
      return backingStore.getVector(term);
    default:
      throw new IllegalStateException(
          "Not a recgonized generation method: '" + flagConfig.elementalmethod() + "'");
    }
  }

  @Override
  public Enumeration<ObjectVector> getAllVectors() {
    return backingStore.getAllVectors();
  }

  @Override
  public int getNumVectors() {
    return backingStore.getNumVectors();
  }
}
