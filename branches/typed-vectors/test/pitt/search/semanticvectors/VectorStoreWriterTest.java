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

package pitt.search.semanticvectors;

import java.io.IOException;

import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.RAMDirectory;
import org.junit.*;

import pitt.search.semanticvectors.vectors.RealVector;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorType;

import junit.framework.TestCase;

public class VectorStoreWriterTest extends TestCase {
  
  public static final RAMDirectory directory = new RAMDirectory();

  public VectorStoreRAM createTestVectorStore() {
    VectorStoreRAM store = new VectorStoreRAM(VectorType.REAL, 2);
    store.putVector("isaac", new RealVector(new float[] {1, 0}));
    store.putVector("abraham", new RealVector(new float[] {0.7f, 0.7f}));
    return store;
  }
  
  @Test
  public void testGenerateHeaderString() {
    Flags.dimension = 2;
    Flags.vectortype = "binary";
    assertEquals(VectorStoreWriter.generateHeaderString(), "-vectortype binary -dimension 2");
    Flags.vectortype = "real";  // Cleanup!
  }

  @Test
  public void testWriteLuceneVectorStoreAndRead() throws IOException {
    IndexOutput indexOutput = directory.createOutput("realvectors.bin");
    VectorStore store = createTestVectorStore();
    VectorStoreWriter writer = new VectorStoreWriter();
    writer.writeToIndexOutput(store, indexOutput);
    indexOutput.flush();
    
    ThreadLocal<IndexInput> threadLocalIndexInput = new ThreadLocal<IndexInput>() {
      @Override
      protected IndexInput initialValue() {
        try {
          return directory.openInput("realvectors.bin");
        } catch (IOException e) {
          e.printStackTrace();
        }
        return null;
      }
    };
    VectorStoreReaderLucene storeReader = new VectorStoreReaderLucene(threadLocalIndexInput);
    assertEquals(2, storeReader.getNumVectors());
    Vector abrahamVector = storeReader.getVector("abraham");
    Vector isaacVector = storeReader.getVector("isaac");
    assertEquals(0.7, abrahamVector.measureOverlap(isaacVector), 0.01);
  }
}