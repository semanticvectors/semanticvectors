/**
   Copyright (c) 2011, the SemanticVectors AUTHORS.

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

package pitt.search.semanticvectors.vectors;

import java.util.Random;

import org.junit.Test;

import junit.framework.TestCase;

public class RealVectorTest extends TestCase {
  public static final double TOL = 0.00001;
  
  @Test
  public void testCreateZeroVectorAndOverlap() {
    Vector vector1 = VectorFactory.createZeroVector(VectorType.REAL, 2);
    assertEquals(2, vector1.getDimension());
    assertEquals(0.0, vector1.measureOverlap(vector1));
    Vector vector2 = VectorFactory.createZeroVector(VectorType.REAL, 3);
    try {
      vector1.measureOverlap(vector2);
      fail();
    } catch (IncompatibleVectorsException e) {
      assertTrue(e.getMessage().contains("dimension"));
    }
  }

  @Test
  public void testNonZeroOverlap() {
    RealVector vector1 = new RealVector(new float[] {0, 1});
    RealVector vector2 = new RealVector(new float[] {1, 1});
    assertEquals(0.7071067, vector1.measureOverlap(vector2), TOL);
  }

  @Test
  public void testSparseRandomAllocation() {
    Random random = new Random(0);
    try {
      VectorFactory.generateRandomVector(VectorType.REAL, 2, 10, random);
      fail();
    } catch (RuntimeException e) {
      assertTrue(e.getMessage().contains("not sparse"));
    }
    RealVector vector = (RealVector) VectorFactory.generateRandomVector(
        VectorType.REAL, 10, 2, random);
    assertTrue(vector.toString().contains("Sparse"));
    assertEquals(1.0, vector.measureOverlap(vector), TOL);
    // Vector will have been cast to dense form to measure overlap.
    assertTrue(vector.toString().contains("Dense"));
    RealVector vector2 = (RealVector) VectorFactory.generateRandomVector(
        VectorType.REAL, 10, 2, random);
    assertEquals(0.0, vector.measureOverlap(vector2), TOL);
    // Both vectors now dense.
    assertTrue(vector.toString().contains("Dense"));
    assertTrue(vector2.toString().contains("Dense"));
  }

  @Test
  public void testSparseAdditionAndConversion() {
    // Sparse representation of vector whose dense coordinates would be 1 0 -1.
    RealVector sparse1 = new RealVector(3, new short[] {1, -3});
    // Sparse representation of vector whose dense coordinates would be -1 1 0.
    RealVector sparse2 = new RealVector(3, new short[] {-1, 2});
    sparse1.superpose(sparse2, 1, null);
    assertTrue(sparse1.toString().contains("0.0 1.0 -1.0"));
    assertTrue(sparse2.toString().contains("Sparse"));
    sparse2.superpose(sparse1, 2, null);
    assertTrue(sparse2.toString().contains("-1.0 3.0 -2.0"));
    assertTrue(sparse2.toString().contains("Dense"));
  }

  @Test
  public void testDenseAddition() {
    RealVector dense1 = new RealVector(new float[] {1, 0, -1});
    RealVector dense2 = new RealVector(new float[] {-1, 1, 0});
    dense1.superpose(dense2, 1, null);
    assertTrue(dense1.toString().contains("0.0 1.0 -1.0"));
    assertTrue(dense2.toString().contains("Dense"));
    dense2.superpose(dense1, 2, null);
    assertTrue(dense2.toString().contains("-1.0 3.0 -2.0"));
    assertTrue(dense2.toString().contains("Dense"));
  }

  @Test
  public void testPermutedAddition() {
    Vector vector1 = VectorFactory.createZeroVector(VectorType.REAL, 3);
    Vector vector2 = new RealVector(new float[] {1, 0, 0});
    vector1.superpose(vector2, 1, new int[] {1, 2, 0});
    assertTrue(vector1.toString().contains("0.0 1.0 0.0"));
    vector1.superpose(vector2, 1, new int[] {2, 1, 0});
    assertTrue(vector1.toString().contains("0.0 1.0 1.0"));
    Vector vector3 = new RealVector(new float[] {0, 2, 0});
    vector1.superpose(vector3, 2, new int[] {2, 0, 1});
    assertTrue(vector1.toString().contains("4.0 1.0 1.0"));
  }
  
  @Test
  public void testSparseConversion() {
    // Sparse representation of vector whose dense coordinates would be 1 0 -1.
    RealVector sparse1 = new RealVector(3, new short[] {1, -3});
    sparse1.sparseToDense();
    assertTrue(sparse1.toString().contains("1.0 0.0 -1.0"));
    // Sparse representation of vector whose dense coordinates would be -1 1 0.
    RealVector sparse2 = new RealVector(3, new short[] {-1, 2});
    sparse2.sparseToDense();
    assertTrue(sparse2.toString().contains("-1.0 1.0 0.0"));
  }
  
  
  @Test
  public void testReadAndWriteStrings() {
    Vector vector = VectorFactory.createZeroVector(VectorType.REAL, 2);
    try {
      vector.readFromString("1.0|-2.0|3.0");
      fail();
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().equals("Found 3 possible coordinates: expected 2"));
    }
    vector.readFromString("1.0|-2.0");
    assertEquals("1.0|-2.0", vector.writeToString());
  }
}