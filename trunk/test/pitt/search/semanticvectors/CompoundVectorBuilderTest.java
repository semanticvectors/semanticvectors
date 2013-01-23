/**
   Copyright 2011, the SemanticVectors authors.
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

import junit.framework.TestCase;

import org.junit.Test;

import pitt.search.semanticvectors.vectors.RealVector;
import pitt.search.semanticvectors.vectors.Vector;

public class CompoundVectorBuilderTest extends TestCase {
  
  static final String[] COMMAND_LINE_ARGS = {"-vectortype", "real", "-dimension", "2"};
  static final FlagConfig FLAG_CONFIG = FlagConfig.getFlagConfig(COMMAND_LINE_ARGS);
  static final double TOL = 0.0001;

  private VectorStoreRAM createVectorStore() {  
    VectorStoreRAM vectorStore = new VectorStoreRAM(FLAG_CONFIG);
    Vector vector1 = new RealVector(new float[] {1.0f, 0.0f});
    vectorStore.putVector("vector1", vector1);
    Vector vector2 = new RealVector(new float[] {1.0f, -1.0f});
    vectorStore.putVector("vector2", vector2);
    return vectorStore;
  }

  private VectorStoreRAM createNormalizedVectorStore() {
    VectorStoreRAM vectorStore = new VectorStoreRAM(FLAG_CONFIG);
    Vector vector1 = new RealVector(new float[] {1.0f, 0.0f});
    vector1.normalize();
    vectorStore.putVector("vector1", vector1);
    Vector vector2 = new RealVector(new float[] {1.0f, -1.0f});
    vector2.normalize();
    vectorStore.putVector("vector2", vector2);
    return vectorStore;
  }

  @Test
    public void testGetAdditiveQueryVector() {
    VectorStore vectorStore = createVectorStore();
    Vector queryVector =
      CompoundVectorBuilder.getQueryVectorFromString(vectorStore, null, FLAG_CONFIG, "vector1 vector2");
    assertEquals(2, queryVector.getDimension());
    assertEquals(0.8944272, queryVector.measureOverlap(vectorStore.getVector("vector1")), TOL);

    // Test again to check for side effects.
    Vector queryVector2 =
      CompoundVectorBuilder.getQueryVectorFromString(vectorStore, null, FLAG_CONFIG, "vector1 vector2");
    assertEquals(2, queryVector.getDimension());
    assertEquals(0.8944272, queryVector.measureOverlap(vectorStore.getVector("vector1")), TOL);
    assertEquals(2, queryVector2.getDimension());
    assertEquals(0.8944272, queryVector2.measureOverlap(vectorStore.getVector("vector1")), TOL);
  }

  @Test
    public void testGetNegatedQueryVector() {
    VectorStore vectorStore = createNormalizedVectorStore();
    Vector queryVector =
      CompoundVectorBuilder.getQueryVectorFromString(vectorStore, null, FLAG_CONFIG, "vector1 vector2");
    assertEquals(queryVector.measureOverlap(vectorStore.getVector("vector1")),
		 queryVector.measureOverlap(vectorStore.getVector("vector2")),
		 TOL);

    queryVector =
      CompoundVectorBuilder.getQueryVectorFromString(vectorStore, null, FLAG_CONFIG, "vector1 ~NOT vector2");
    assertEquals(0, queryVector.measureOverlap(vectorStore.getVector("vector2")), TOL);

    String[] configForNegatedQueries = {"-vectortype", "real", "-dimension", "2", "-suppressnegatedqueries"};
    FlagConfig flagConfig = FlagConfig.getFlagConfig(configForNegatedQueries);
    queryVector =
      CompoundVectorBuilder.getQueryVectorFromString(vectorStore, null, flagConfig, "vector1 ~NOT vector2");
    assertEquals(queryVector.measureOverlap(vectorStore.getVector("vector1")),
		 queryVector.measureOverlap(vectorStore.getVector("vector2")),
		 TOL);
  }
}
