/**
   Copyright (c) 2009, University of Pittsburgh

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

package pitt.search.semanticvectors;

import java.io.IOException;

/**
 * Wrapper class used to get access to underlying VectorStore implementations.
 * @author Dominic Widdows
 */
public class VectorStoreReader {

  /**
   * Opens a vector store for reading, setting flags appropriately.
   * 
   * @param storeName The name/path of the vector store to read (doesn't need ".txt" or ".bin" suffix).
   * @param flagConfig Supplies expected file format; vectortype and dimension will be set to the values
   *        given in the header line of the vector store.
   * @return Vector store object backed by the file given.
   * @throws IOException If the file is not found, or the header line cannot be parsed.
   */
  public static CloseableVectorStore openVectorStore(String storeName, FlagConfig flagConfig) throws IOException {
    CloseableVectorStore vectorStore = null;
    storeName = VectorStoreUtils.getStoreFileName(storeName, flagConfig);
    switch (flagConfig.indexfileformat()) {
    case LUCENE:
      vectorStore = new VectorStoreReaderLucene(storeName, flagConfig);
      break;
    case TEXT:
      vectorStore = new VectorStoreReaderText(storeName, flagConfig);
      break;
    default:
      throw new IllegalStateException("Unknown -indexfileformat: " + flagConfig.indexfileformat());
    }
    return vectorStore;
  }
}
