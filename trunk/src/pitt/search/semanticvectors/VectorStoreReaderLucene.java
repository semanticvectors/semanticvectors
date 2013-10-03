/**
   Copyright (c) 2007, University of Pittsburgh
   Copyright (c) 2008 and ongoing, the SemanticVectors AUTHORS.

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

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.logging.Logger;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;

import pitt.search.semanticvectors.utils.VerbatimLogger;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorType;
import pitt.search.semanticvectors.vectors.VectorFactory;

/**
   This class provides methods for reading a VectorStore from disk. <p>

   The serialization currently presumes that the object (in the ObjectVectors)
   should be serialized as a String. <p>

   The implementation uses Lucene's I/O package, which proved much faster
   than the native java.io.DataOutputStream.
   
   Attempts to be thread-safe but this is not fully tested.
   
   @see ObjectVector
 **/
public class VectorStoreReaderLucene implements CloseableVectorStore {
  private static final Logger logger = Logger.getLogger(
      VectorStoreReaderLucene.class.getCanonicalName());

  private String vectorFileName;
  private File vectorFile;
  private Directory directory;
  private FlagConfig flagConfig;
  
  private ThreadLocal<IndexInput> threadLocalIndexInput;

  public IndexInput getIndexInput() {
    return threadLocalIndexInput.get();
  }
  
  public VectorStoreReaderLucene(String vectorFileName, FlagConfig flagConfig) throws IOException {
    this.flagConfig = flagConfig;
    this.vectorFileName = vectorFileName;
    this.vectorFile = new File(vectorFileName);
    try {
      String parentPath = this.vectorFile.getParent();
      if (parentPath == null) parentPath = "";
      this.directory = FSDirectory.open(new File(parentPath));  // Old from FSDirectory impl.
      // Read number of dimension from header information.
      this.threadLocalIndexInput = new ThreadLocal<IndexInput>() {
        @Override
        protected IndexInput initialValue() {
          try {
            return directory.openInput(vectorFile.getName(), IOContext.READ);
          } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
          }
        }
      };
      readHeadersFromIndexInput(flagConfig);
    } catch (IOException e) {
      logger.warning("Cannot open file: " + this.vectorFileName + "\n" + e.getMessage());
      throw e;
    }
  }
  
  /**
   * Only for testing!  This does not create an FSDirectory so calling "close()" gives NPE.
   * TODO(widdows): Fix this, and ownership of FSDirectory or RAMDirectory!
   */
  protected VectorStoreReaderLucene(ThreadLocal<IndexInput> threadLocalIndexInput, FlagConfig flagConfig)
      throws IOException {
    this.threadLocalIndexInput = threadLocalIndexInput;
    this.flagConfig = flagConfig;
    readHeadersFromIndexInput(flagConfig);
  }

  /**
   * Sets internal dimension and vector type, and flags in flagConfig to match.
   * 
   * @throws IOException
   */
  public void readHeadersFromIndexInput(FlagConfig flagConfig) throws IOException {
    String header = threadLocalIndexInput.get().readString();
    FlagConfig.mergeWriteableFlagsFromString(header, flagConfig);
  }

  public void close() {
    this.closeIndexInput();
    try {
      this.directory.close();
    } catch (IOException e) {
      logger.severe("Failed to close() directory resources: have they already been destroyed?");
      e.printStackTrace();
    }
  }

  public void closeIndexInput() {
    try {
      this.getIndexInput().close();
    } catch (IOException e) {
      logger.info("Cannot close resources from file: " + this.vectorFile
          + "\n" + e.getMessage());
    }
  }

  public Enumeration<ObjectVector> getAllVectors() {
    try {
      getIndexInput().seek(0);
      // Skip header line.
      getIndexInput().readString();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    return new VectorEnumeration(getIndexInput());
  }

  /**
   * Given an object, get its corresponding vector <br>
   * This implementation only works for string objects so far <br>
   * 
   * @param desiredObject - the string you're searching for
   * @return vector from the VectorStore, or null if not found.
   */
  public Vector getVector(Object desiredObject) {
    try {
      String stringTarget = desiredObject.toString();
      getIndexInput().seek(0);
      // Skip header line.
      getIndexInput().readString();
      while (getIndexInput().getFilePointer() < getIndexInput().length() - 1) {
        String objectString = getIndexInput().readString();
        if (objectString.equals(stringTarget)) {
          VerbatimLogger.info("Found vector for '" + stringTarget + "'\n");
          Vector vector = VectorFactory.createZeroVector(
              flagConfig.vectortype(), flagConfig.dimension());
          vector.readFromLuceneStream(getIndexInput());
          return vector;
        }
        else{
          getIndexInput().seek(getIndexInput().getFilePointer()
              + VectorFactory.getLuceneByteSize(flagConfig.vectortype(), flagConfig.dimension()));
        }
      }
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    VerbatimLogger.info("Didn't find vector for '" + desiredObject + "'\n");
    return null;
  }

  /**
   * Trivial (costly) implementation of getNumVectors that iterates and counts vectors.
   */
  public int getNumVectors() {
    Enumeration<ObjectVector> allVectors = this.getAllVectors();
    int i = 0;
    while (allVectors.hasMoreElements()) {
      allVectors.nextElement();
      ++i;
    }
    return i;
  }

  /**
   * Implements the hasMoreElements() and nextElement() methods
   * to give Enumeration interface from store on disk.
   */
  public class VectorEnumeration implements Enumeration<ObjectVector> {
    IndexInput indexInput;

    public VectorEnumeration(IndexInput indexInput) {
      this.indexInput = indexInput;
    }

    public boolean hasMoreElements() {
      return (indexInput.getFilePointer() < indexInput.length());
    }

    public ObjectVector nextElement() {
      String object = null;
      Vector vector = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());
      try {
        object = indexInput.readString();
        vector.readFromLuceneStream(indexInput);
      }
      catch (IOException e) {
        e.printStackTrace();
      }
      return new ObjectVector(object, vector);
    }
  }
}
