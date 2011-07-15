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
  public void testCreateZeroVectorAndOverlap() {
    Vector zero = VectorFactory.createZeroVector(VectorType.BINARY, 8);
    assertEquals("00000000", zero.writeToString());
  }
  
  @Test
  public void testGenerateRandomVectorWriteAndRead() {
    Random random = new Random(0);
    Vector vector = VectorFactory.generateRandomVector(VectorType.BINARY, 8, 2, random);
    assertEquals("00000110", vector.writeToString());
    
    RAMDirectory directory = new RAMDirectory();
    try {
      IndexOutput indexOutput = directory.createOutput("binaryvectors.bin");
      vector.writeToLuceneStream(indexOutput);
      indexOutput.flush();
      IndexInput indexInput = directory.openInput("binaryvectors.bin");
      Vector vector2 = VectorFactory.createZeroVector(VectorType.BINARY, 8);
      assertEquals("00000000", vector2.writeToString());
      vector2.readFromLuceneStream(indexInput);
      assertEquals("00000110", vector2.writeToString());
    } catch (IOException e) {
      e.printStackTrace();
      fail();
    }
  }
  
  @Test
  public void testDimensionsMustMatchWhenReading() {
    BinaryVector vector = (BinaryVector) VectorFactory.createZeroVector(VectorType.BINARY, 8);
    try {
      vector.readFromString("0101010101");
      fail();
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("expected 8"));
    }
  }
  
  @Test
  public void testSuperposeAndNormalize() {
    BinaryVector vector = (BinaryVector) VectorFactory.createZeroVector(VectorType.BINARY, 8);
    vector.readFromString("01010101");
    BinaryVector vector2 = (BinaryVector) VectorFactory.createZeroVector(VectorType.BINARY, 8);
    vector2.readFromString("00001111");
    vector.superpose(vector2, 1000, null);
    // This is a surprise to me - calling normalize to make the superposition "take" was
    // unexpected.
    assertEquals("01010101", vector.writeToString());
    vector.normalize();
    assertEquals("00001111", vector.writeToString());
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