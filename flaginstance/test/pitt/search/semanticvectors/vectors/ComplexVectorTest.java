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

import java.io.IOException;
import java.util.Random;

import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.RAMDirectory;
import org.junit.Test;

import static pitt.search.semanticvectors.TestUtils.assertFloatArrayEquals;
import static pitt.search.semanticvectors.vectors.ComplexVector.Mode;

import junit.framework.TestCase;

import static org.junit.Assert.assertArrayEquals;

/**
 * Tests for {@code ComplexVector} class.  Many of these tests use {@code ComplexVector#toString}
 * to check correct results.  This is somewhat ad hoc and any change in the debug string
 * representation will likely cause breakages.  Be warned!
 */
public class ComplexVectorTest extends TestCase {

  private static final double TOL = 0.0001;

  @Test 
  public void testGenerateRandomVector() {
    // Generate random vector
    ComplexVector cv1 = (ComplexVector) VectorFactory.generateRandomVector(
        VectorType.COMPLEX, 5, 2, new Random(0));
    assertArrayEquals(new short[] {0, 13622, 4, 9934}, cv1.getSparseOffsets());
    assertEquals(Mode.POLAR_SPARSE, cv1.getOpMode());
  }
  
  @Test
  public void testDenseToCartesian() {
    ComplexVector vector = new ComplexVector(new short[] {-1, 0});
    vector.toCartesian();
    assertEquals(ComplexVector.Mode.CARTESIAN, vector.getOpMode());
    assertFloatArrayEquals(new float[] {0.0f, 0.0f, 1.0f, 0.0f}, vector.getCoordinates(), TOL);
  }

  @Test
  public void testComplexVectorConversion() {
    ComplexVector vector = (ComplexVector) VectorFactory.createZeroVector(VectorType.COMPLEX, 10);
    assertTrue(vector.isZeroVector());
    assertEquals(10, vector.getDimension());

    vector = (ComplexVector) VectorFactory.createZeroVector(VectorType.COMPLEX, 2);
    vector.setSparseOffsets(new short[] {1, 0});
    vector.toDensePolar();
    assertArrayEquals(new short[] {-1, 0}, vector.getPhaseAngles());
    vector.toCartesian();
    assertFloatArrayEquals(new float[] {0, 0, 1, 0}, vector.getCoordinates(), TOL);
  }

  @Test
  public void testWriteToString() {
    float[] coords = { 12.3f, 3.2f, 2.6f, -1.3f, -0.01f, -1000.2f };
    ComplexVector cv = new ComplexVector(coords);
    assertTrue(cv.writeToString().contains("12.3|3.2|2.6|-1.3|-0.01|-1000.2"));
    assertTrue(cv.toString().contains("12.3 3.2 2.6 -1.3 -0.01 -1000.2 "));
  }

  @Test
  public void testSuperposeSparseOnZero() {
    int dim = 4;
    ComplexVector cv1 = (ComplexVector) VectorFactory.createZeroVector(VectorType.COMPLEX, dim);
    cv1.setSparseOffsets(new short[] {1, 0, 3, CircleLookupTable.PHASE_RESOLUTION / 4});
    ComplexVector cv2 = (ComplexVector) VectorFactory.createZeroVector(VectorType.COMPLEX, dim);
    cv2.superpose(cv1, 5, null);
    assertEquals(Mode.CARTESIAN, cv2.getOpMode());
    assertFloatArrayEquals(
        new float[] {0, 0, 5, 0, 0, 0, 0, 5}, cv2.getCoordinates(), TOL);
  }

  @Test
  public void testSuperposeZeroOnSparse() {
    int dim = 4;
    int seedLength = 2;
    ComplexVector cv1 = (ComplexVector) VectorFactory.generateRandomVector(
        VectorType.COMPLEX, dim, seedLength, new Random(0));
    assertArrayEquals(new short[] {2, 13622, 0, 9934}, cv1.getSparseOffsets());
    ComplexVector cv2 = (ComplexVector) VectorFactory.createZeroVector(VectorType.COMPLEX, dim);
    cv2.superpose(cv1, 1.0, null);
    assertFloatArrayEquals(
        new float[] {-0.78503f, -0.61945f, 0, 0, 0.48955f, -0.872064f, 0, 0},
        cv2.getCoordinates(), TOL);
  }

  @Test
  public void testVectorCopyCartesian() {
    float[] coords = { 12.3f, 3.2f, 2.6f, -1.3f };
    ComplexVector cv1 = new ComplexVector(coords);
    ComplexVector cv2 = cv1.copy();
    assertFloatArrayEquals(
        new float[] { 12.3f, 3.2f, 2.6f, -1.3f }, cv2.getCoordinates(), TOL);
  }

  @Test
  public void testCartesianToDensePolar() {
    ComplexVector cv = new ComplexVector( new float[] {1.0f, 1.0f, 0, 1.0f, 1.0f, 0 } );
    cv.toDensePolar();
    assertArrayEquals(new short[] {2048, 4096, 0}, cv.getPhaseAngles());
  }

  @Test
  public void testNormalize() {
    ComplexVector.setDominantMode(Mode.CARTESIAN);
    ComplexVector cv = new ComplexVector(new float[] {3, 4, 4, 3, 4, 3, 0, 5});
    cv.normalize();
    assertFloatArrayEquals(new float[] {0.3f, 0.4f, 0.4f, 0.3f, 0.4f, 0.3f, 0.0f, 0.5f},
        cv.getCoordinates(), TOL);
    ComplexVector.setDominantMode(Mode.POLAR_DENSE);
    cv.normalize();
    assertArrayEquals(new short[] {2418, 1677, 1677, 4096}, cv.getPhaseAngles());
  }

  @Test
  public void testMeasureOverlap() {
    int RES = CircleLookupTable.PHASE_RESOLUTION;
    short[] angles1 = { 0, 0, 0 };
    short[] angles2 = { 0, -1, (short) (RES / 4) };  // Remember that -1 angle means complex zero.
    short[] angles3 = { (short) (3*RES / 4), (short) (RES / 2), 0 };

    ComplexVector cv1 = new ComplexVector(angles1);
    ComplexVector cv2 = new ComplexVector(angles2);
    ComplexVector cv3 = new ComplexVector(angles3);

    assertEquals(1.0/3.0, cv1.measurePolarDenseOverlap(cv2), TOL);
    assertEquals(0, cv1.measurePolarDenseOverlap(cv3), TOL);
    assertEquals(0, cv2.measurePolarDenseOverlap(cv3), TOL);
    assertEquals(1, cv1.measurePolarDenseOverlap(cv1), TOL);
    assertEquals(2.0/3.0, cv2.measurePolarDenseOverlap(cv2), TOL);  // Zero entry doesn't contribute.
    assertEquals(1, cv3.measurePolarDenseOverlap(cv3), TOL);
  }
  
  @Test
  public void testConvolve() {
    short RES = CircleLookupTable.PHASE_RESOLUTION;
    short ZERO_INDEX = CircleLookupTable.ZERO_INDEX;
    ComplexVector cv = new ComplexVector(new short[] {ZERO_INDEX, 0, (short) (RES/2)});
    ComplexVector cv2 = new ComplexVector(new short[] {0, (short) (RES/4), (short) (RES/4)});
    cv.convolve(cv2, 1);
    for (short i : cv.getPhaseAngles()) {
      System.err.print(i +  " ");
    }
    assertArrayEquals(new short[] {0, (short) (RES/4), (short) (3*RES/4)}, cv.getPhaseAngles());
    // Should have what we started with.
    cv.convolve(cv2, -1);
    assertArrayEquals(new short[] {0, 0, (short) (RES/2)}, cv.getPhaseAngles());
    // Convolving with inverse of itself gives all ones (or zeros).
    cv.convolve(cv, -1);
    assertArrayEquals(new short[] {0, 0, 0}, cv.getPhaseAngles());
  }
  
  @Test
  public void testBindFromRandom() {
    short ZERO_INDEX = CircleLookupTable.ZERO_INDEX;
    Random random = new Random(0);
    ComplexVector cv1 = (ComplexVector) VectorFactory.generateRandomVector(
        VectorType.COMPLEX, 5, 2, random);
    ComplexVector cv2 = (ComplexVector) VectorFactory.generateRandomVector(
        VectorType.COMPLEX, 5, 2, random);
    cv1.bind(cv2, 1);
    assertArrayEquals(new short[] {2301, 1917, ZERO_INDEX, ZERO_INDEX, 9934}, cv1.getPhaseAngles());
  }
  
  @Test
  public void testBindFromZero() {
    short ZERO_INDEX = CircleLookupTable.ZERO_INDEX;
    Random random = new Random(0);
    ComplexVector cv1 = (ComplexVector) VectorFactory.createZeroVector(VectorType.COMPLEX, 5);
    cv1.toDensePolar();
    ComplexVector cv2 = (ComplexVector) VectorFactory.generateRandomVector(
        VectorType.COMPLEX, 5, 2, random);
    cv1.bind(cv2, 1);
    assertArrayEquals(
        new short[] {13622, ZERO_INDEX, ZERO_INDEX, ZERO_INDEX, 9934}, cv1.getPhaseAngles());
  }

  @Test
  public void testReadWrite() {
    Vector v1 = new ComplexVector(new short[] { -1, 8000, 16000 });

    RAMDirectory directory = new RAMDirectory();

    try {
      IndexOutput indexOutput = directory.createOutput("complexvectors.bin");
      v1.writeToLuceneStream(indexOutput);
      indexOutput.flush();

      IndexInput indexInput = directory.openInput("complexvectors.bin");
      ComplexVector cv2 = new ComplexVector(3, Mode.POLAR_SPARSE);
      cv2.readFromLuceneStream(indexInput);
      assertFloatArrayEquals(
          new float[] {0, 0, -0.997290f, 0.073564f, 0.989176f, -0.1467304f},
          cv2.getCoordinates(), TOL);
    } catch (IOException e) {
      e.printStackTrace();
      fail();
    }
  }
}
