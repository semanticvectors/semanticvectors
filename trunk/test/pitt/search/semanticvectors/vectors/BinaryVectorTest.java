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

import junit.framework.TestCase;

import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.RAMDirectory;
import org.junit.Test;

public class BinaryVectorTest extends TestCase {



  @Test
  public void testDimensionMustMatchWhenReading() {
    BinaryVector vector = (BinaryVector) VectorFactory.createZeroVector(VectorType.BINARY, 128);
    try {
      vector.readFromString("0101010101");
      fail();
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("expected 128"));
    }
  }

  @Test
  public void testSuperposeAndNormalize() {
    BinaryVector vector = (BinaryVector) VectorFactory.createZeroVector(VectorType.BINARY, 64);
    vector.readFromString("0101010111000011110100011111110110100000001110111000011000100100");
    BinaryVector vector2 = (BinaryVector) VectorFactory.createZeroVector(VectorType.BINARY, 64);
    vector2.readFromString("0000111111000011110100011111110110100000001110111000011000100100");
    vector.superpose(vector2, 1000, null);
    // This is a surprise to me - calling normalize to make the superposition "take" was
    // unexpected.
    assertEquals("0101010111000011110100011111110110100000001110111000011000100100", vector.writeToString());
    vector.normalize();
    assertEquals("0000111111000011110100011111110110100000001110111000011000100100", vector.writeToString());

    BinaryVector vector3 = (BinaryVector) VectorFactory.createZeroVector(VectorType.BINARY, 64);
    vector3.readFromString("0000000001111110000111101000111111101101000000011101110000110101");
    BinaryVector vector4 = (BinaryVector) VectorFactory.createZeroVector(VectorType.BINARY, 64);
    vector4.readFromString("0100000001111110000111101000111111101101000000011101110000110101");
    vector3.superpose(vector4, 2, null);
    vector3.normalize();
  }

  @Test
  public void testGetMaximumSharedWeight() {
    BinaryVector vector1 = (BinaryVector) VectorFactory.createZeroVector(VectorType.BINARY, 64);
    vector1.readFromString("0000000000000000000000000000000000000000000000000000000000000000");
    BinaryVector vector2 = (BinaryVector) VectorFactory.createZeroVector(VectorType.BINARY, 64);
    vector2.readFromString("0000111100001111000011110000111100001111000011110000111100001111");
    BinaryVector vector3 = (BinaryVector) VectorFactory.createZeroVector(VectorType.BINARY, 64);
    vector3.readFromString("1111000011110000111100001111000011110000111100001111000011110000");
    vector1.superpose(vector2, 4, null);
    //System.err.println("vector1 + 4*vector2:\n" + vector1.toString());
    vector1.superpose(vector3, 8, null);
    //System.err.println("vector1 + 4*vector2 + 8*vector3:\n" + vector1.toString());
    assertEquals(0, vector1.getMaximumSharedWeight());
    vector1.normalize();
    //System.err.println("vector1 normalized:\n" + vector1.toString());
    assertEquals("1111000011110000111100001111000011110000111100001111000011110000",
                 vector1.writeToString());
  }

  @Test
  public void testCreateZeroVectorAndOverlap() {
    Vector zero = VectorFactory.createZeroVector(VectorType.BINARY, 64);
    assertEquals("0|", ((BinaryVector) zero).writeLongToString());
  }

  @Test
  public void testPermutation() {
    int dim = 512;
    Random random = new Random();
    BinaryVector elementalVector = (BinaryVector) VectorFactory.generateRandomVector(
        VectorType.BINARY, dim, dim/2, random);
    Vector semanticVector = VectorFactory.createZeroVector(VectorType.BINARY, dim);

    int longDim = (int) dim/64;
    if (dim % 64 > 0) longDim++;

    //check that number of long in array meets expectation 
    assertEquals(elementalVector.bitLength(),longDim);

    int[] shiftRight = PermutationUtils.getShiftPermutation(VectorType.BINARY, dim, 1);
    int[] shiftLeft = PermutationUtils.getInversePermutation(shiftRight);

    BinaryVector elementalClone = elementalVector.copy();

    // Check superposition + permutation
    semanticVector.superpose(elementalVector, 1, shiftRight);
    semanticVector.normalize();

    // Check that elemental vector not altered during permutation
    assertEquals(((BinaryVector) elementalVector).writeLongToString(), ((BinaryVector) elementalClone).writeLongToString());

    // Check that permuted elemental vector matches added elemental vector
    elementalVector.permute(shiftRight);
    assertEquals(((BinaryVector) elementalVector).writeLongToString(), ((BinaryVector) semanticVector).writeLongToString());

    // Check that it has in face changed.
    assertFalse(((BinaryVector) elementalVector).writeLongToString().equals(
        ((BinaryVector) elementalClone).writeLongToString()));
    
    // Check that permutation is invertible
    elementalVector.permute(shiftLeft);
    assertEquals(((BinaryVector) elementalVector).writeLongToString(), ((BinaryVector) elementalClone).writeLongToString());

  }


  @Test
  public void testGenerateRandomVectorWriteAndRead() {
    Random random = new Random(0);

    Vector vector = VectorFactory.generateRandomVector(VectorType.BINARY, 64, 2, random);
    assertEquals("1100001111010001111111011010000000111011100001100010010010001111", vector.writeToString());

    RAMDirectory directory = new RAMDirectory();
    try {
      IndexOutput indexOutput = directory.createOutput("binaryvectors.bin");
      vector.writeToLuceneStream(indexOutput);
      indexOutput.flush();
      IndexInput indexInput = directory.openInput("binaryvectors.bin");
      Vector vector2 = VectorFactory.createZeroVector(VectorType.BINARY, 64);
      assertEquals("0000000000000000000000000000000000000000000000000000000000000000", vector2.writeToString());
      vector2.readFromLuceneStream(indexInput);
      assertEquals("1100001111010001111111011010000000111011100001100010010010001111", vector2.writeToString());
    } catch (IOException e) {
      e.printStackTrace();
      fail();
    }
  }  

  /*
  {
  Random random = new Random();
  Vector testV = new BinaryVector(10000);

  Vector randV = testV.generateRandomVector(10000,5000, random);

  for (int q = 0; q < 10000; q++)
    ((BinaryVector) randV).bitSet.set(q);
  Vector origin = randV.copy();

  for (int x =1; x < 10000; x++)
  {

    System.out.println("--------Number of votes "+x);
    testV.superpose(randV, 1, null);
    testV.normalize();


    System.out.println(testV.measureOverlap(origin)+"\t"+ ((BinaryVector) testV).numRows()+"\t"+Math.pow(2,((BinaryVector) testV).numRows()));

    System.out.println("Vector added:");
    System.out.println(randV);

    System.out.println("Superposition:");

    System.out.println(testV);


    randV = testV.generateRandomVector(10000,5000, random);
   */

}