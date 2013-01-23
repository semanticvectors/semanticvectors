/**
   Copyright 2008, Google Inc.
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

import org.junit.*;

import pitt.search.semanticvectors.vectors.RealVector;
import pitt.search.semanticvectors.vectors.Vector;

import junit.framework.TestCase;

public class VectorStoreRAMTest extends TestCase {

  static final String[] COMMAND_LINE_ARGS = {"-vectortype", "real", "-dimension", "2"};
  static final FlagConfig FLAG_CONFIG = FlagConfig.getFlagConfig(COMMAND_LINE_ARGS);
  static double TOL = 0.0001;

  @Test
  public void testCreateWriteAndRead() {
    VectorStoreRAM vectorStore = new VectorStoreRAM(FLAG_CONFIG);
    assertEquals(0, vectorStore.getNumVectors());
    Vector vector = new RealVector(new float[] {1.0f, 0.0f});
    vectorStore.putVector("my vector", vector);
    assertEquals(1, vectorStore.getNumVectors());
    Vector vectorOut = vectorStore.getVector("my vector"); 
    assertEquals(2, vectorOut.getDimension());
    assertEquals(1, vectorOut.measureOverlap(vector), TOL);
  }

  @Test
  public void testRepeatReads() {
    VectorStoreRAM vectorStore = new VectorStoreRAM(FLAG_CONFIG);
    assertEquals(0, vectorStore.getNumVectors());
    Vector vector = new RealVector(new float[] {1.0f, 0.0f});
    vectorStore.putVector("my vector", vector);
    assertEquals(1, vectorStore.getNumVectors());
    Vector vectorOut = vectorStore.getVector("my vector"); 
    assertEquals(2, vectorOut.getDimension());
    vectorOut = null;
    vectorOut = vectorStore.getVector("my vector"); 
    assertEquals(2, vectorOut.getDimension());
  }
}