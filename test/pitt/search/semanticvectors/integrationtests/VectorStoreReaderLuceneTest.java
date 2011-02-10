/**
   Copyright 2009, SemanticVectors AUTHORS.
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

package pitt.search.semanticvectors.integrationtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import pitt.search.semanticvectors.ObjectVector;
import pitt.search.semanticvectors.VectorStoreReaderLucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.NoSuchElementException;

public class VectorStoreReaderLuceneTest {

  @Before
  public void setUp() { assert(RunTests.prepareTestData()); }

  @Test
  public void TestReadFromTestData() throws IOException {
    System.err.println("Running tests for VectorStoreReaderLucene");
    VectorStoreReaderLucene reader = new VectorStoreReaderLucene(RunTests.vectorBinFile);
    assertEquals(2, reader.getNumVectors());
    float[] abraham = reader.getVector("abraham");
    reader.close();
    assertEquals(1.0f, abraham[0], 0.01);
  }

  @Test
  public void TestOpensAndCloses() throws IOException {
    VectorStoreReaderLucene reader;
    reader = new VectorStoreReaderLucene(RunTests.vectorBinFile);
    reader.close();
  }

  @Test
  // I'm not sure you *should* be able to open two versions of the
  // same vector store file open at once, even for reads, but it's
  // good to test for this somehow.
  public void TestMultipleOpensForRead() throws IOException {
    VectorStoreReaderLucene reader = new VectorStoreReaderLucene(RunTests.vectorBinFile);
    VectorStoreReaderLucene reader2 = new VectorStoreReaderLucene(RunTests.vectorBinFile);
    reader.close();
    reader2.close();
  }

  @Test
  public void TestEnumerationThreadSafety() throws IOException {
    final int maxThreads = 5;

    // It's a bit of a dance to get a VectorStoreReaderLucene that you
    // can use inside different threads.
    VectorStoreReaderLucene vectorStoreInit = null;
    vectorStoreInit = new VectorStoreReaderLucene(RunTests.vectorBinFile);
    final VectorStoreReaderLucene vectorStore = vectorStoreInit;
    ArrayList<Thread> threads = new ArrayList<Thread>();

    for (int i = 0; i < maxThreads; ++i) {
      Thread thread = new Thread(new Runnable() {
        public void run() {
          Enumeration<ObjectVector> vecEnum = vectorStore.getAllVectors();
          while (vecEnum.hasMoreElements()) {
            try {
              Thread.sleep(25L);
            } catch (InterruptedException e) {
              e.printStackTrace();
              fail();
            }
            try {
              vecEnum.nextElement();
            } catch (NoSuchElementException e) {
              e.printStackTrace();
              // There is a problem with getting RunTests to report
              // these failures from within threads properly.
              fail("There is a problem with concurrent reads from a VectorStore");
            }
          }
          vectorStore.closeIndexInput();
        }
      }
      );
      threads.add(thread);
      thread.start();
    }

    for (Thread thread: threads) {
      try {
        thread.join();
      } catch (InterruptedException e) {
        e.printStackTrace();
        fail();
      }
    }
    vectorStoreInit.close();
  }
}
