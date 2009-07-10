/**
   Copyright 2009, the SemanticVectors authors.
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

import java.lang.Math;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;

public class CompoundVectorBuilderTest {

  private VectorStoreRAM CreateVectorStore() {
    VectorStoreRAM vectorStore = new VectorStoreRAM();
    Flags.dimension = 2;
    float[] vector1 = {1.0f, 2.0f};
    vectorStore.putVector("vector1", vector1);
    float[] vector2 = {1.0f, -1.0f};
    vectorStore.putVector("vector2", vector2);
    return vectorStore;
  }

  @Test
    public void TestGetAdditiveQueryVectorTest() {
    System.err.println("Running tests for CompoundVectorBuilder");
    VectorStore vectorStore = CreateVectorStore();
    float[] queryVector =
      CompoundVectorBuilder.getQueryVectorFromString(vectorStore, null, "vector1 vector2");
    assertEquals(2, queryVector.length);
    float norm = (float) Math.sqrt(5);
    assertEquals(2.0/norm, queryVector[0], 0.0001);
    assertEquals(1.0/norm, queryVector[1], 0.0001);

    // Test again to check for side effects.
    float[] queryVector2 =
      CompoundVectorBuilder.getQueryVectorFromString(vectorStore, null, "vector1 vector2");
    assertEquals(2, queryVector.length);
    assertEquals(2.0/norm, queryVector[0], 0.0001);
    assertEquals(1.0/norm, queryVector[1], 0.0001);
  }

  private VectorStoreRAM CreateNormalizedVectorStore() {
    VectorStoreRAM vectorStore = new VectorStoreRAM();
    Flags.dimension = 2;
    float[] vector1 = {1.0f, 2.0f};
    vector1 = VectorUtils.getNormalizedVector(vector1);
    vectorStore.putVector("vector1", vector1);
    float[] vector2 = {1.0f, -1.0f};
    vector2 = VectorUtils.getNormalizedVector(vector2);
    vectorStore.putVector("vector2", vector2);
    return vectorStore;
  }

  @Test
    public void TestGetNegatedQueryVectorTest() {
    VectorStore vectorStore = CreateNormalizedVectorStore();
    float[] queryVector =
      CompoundVectorBuilder.getQueryVectorFromString(vectorStore, null, "vector1 vector2");
    assertEquals(VectorUtils.scalarProduct(queryVector, vectorStore.getVector("vector1")),
		 VectorUtils.scalarProduct(queryVector, vectorStore.getVector("vector2")),
		 0.001);

    queryVector =
      CompoundVectorBuilder.getQueryVectorFromString(vectorStore, null, "vector1 NOT vector2");
    assertEquals(0, VectorUtils.scalarProduct(queryVector, vectorStore.getVector("vector2")), 0.01);

    Flags.suppressnegatedqueries = true;
    queryVector =
      CompoundVectorBuilder.getQueryVectorFromString(vectorStore, null, "vector1 NOT vector2");
    assertEquals(VectorUtils.scalarProduct(queryVector, vectorStore.getVector("vector1")),
		 VectorUtils.scalarProduct(queryVector, vectorStore.getVector("vector2")),
		 0.001);
  }
}
