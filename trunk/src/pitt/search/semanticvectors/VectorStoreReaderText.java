/**
   Copyright (c) 2008, Google Inc.

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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.logging.Logger;

import pitt.search.semanticvectors.utils.VerbatimLogger;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorType;

/**
   This class provides methods for reading a VectorStore from a textfile.<p>

   The textfile should start with an optional header line,
   "<code>-dimension|N</code>".<br>
   All subsequent lines should be of the form <br>
   <code>String|Num1|Num2|...|NumN</code><p>

   The serialization currently presumes that the object (in the ObjectVectors)
   should be serialized as a String. <p>

   This class is mainly for interoperability with plain text file
   formats: normal (fast) implementations should use the internal
   VectorStoreReader class that uses Lucene's I/O functions.
   
   Not thread-safe.

   @see VectorStoreReaderLucene
   @see ObjectVector
 **/
public class VectorStoreReaderText implements CloseableVectorStore {
  private static final Logger logger = Logger.getLogger(
      VectorStoreReaderText.class.getCanonicalName());

  private VectorType vectorType;
  private int dimension;
  private String vectorFileText;
  private BufferedReader inBuf;
  
  /**
   * Initializes a VectorStoreReaderText from a file.
   * Sets internal dimension and vector type, and public flags to match.
   * 
   * @throws IOException
   */
  public VectorStoreReaderText(String vectorFileText, FlagConfig flagConfig) throws IOException {
    this.vectorFileText = vectorFileText;
    this.inBuf = new BufferedReader(new FileReader(vectorFileText));
    try {
      // Read number of dimension from header information.
      String firstLine = inBuf.readLine();
      FlagConfig.mergeWriteableFlagsFromString(firstLine, flagConfig);
      this.dimension = flagConfig.dimension();
      this.vectorType = flagConfig.vectortype();
    } catch (IOException e) {
      System.out.println("Cannot read file: " + vectorFileText + "\n" + e.getMessage());
    }
  }

  public void close() {
    try {
      this.inBuf.close(); //closes underlying filereader too
    } catch (IOException e) {
      System.out.println("Cannot close resources from file: " + this.vectorFileText
          + "\n" + e.getMessage());
    }
  }

  public Enumeration<ObjectVector> getAllVectors() {
    //create new buffered reader to guarantee that it closes properly
    BufferedReader vecBuf;
    try{
      vecBuf = new BufferedReader(new FileReader (vectorFileText));
      vecBuf.readLine();  // Header line will already have been parsed.
    }
    catch (IOException e) {
      //empty, nextElement will always return false
      vecBuf = new BufferedReader(new StringReader(""));
      e.printStackTrace();
    }
    return new VectorEnumerationText(vecBuf);
  }

  /**
   * Returns an object vector from a text line.
   */
  // TODO(widdows): This is eminently testable.
  public ObjectVector parseVectorLine(String line) throws IOException {
    int firstSplitPoint = line.indexOf("|");
    String objectName = new String(line.substring(0, firstSplitPoint));
    Vector tmpVector = VectorFactory.createZeroVector(vectorType, dimension);
    tmpVector.readFromString(line.substring(firstSplitPoint + 1, line.length()));
    return new ObjectVector(objectName, tmpVector);
  }

  /**
   * Given an object, get its corresponding vector <br>
   * This implementation only works for string objects so far <br>
   * @param desiredObject - the string identifying the object being searched for.
   */
  public Vector getVector(Object desiredObject) {
    try {
      this.close();
      inBuf = new BufferedReader(new FileReader (vectorFileText));
      inBuf.readLine();  // Skip header line.
      String line;
      while ((line = inBuf.readLine()) != null) {
        String[] entries = line.split("\\|");
        if (entries[0].equals(desiredObject)) {
          VerbatimLogger.info("Found vector for '" + desiredObject + "'\n");
          return (parseVectorLine(line).getVector());
        }
      }
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    VerbatimLogger.info("Failed to find vector for '" + desiredObject + "'\n");
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
   * to give Enumeration interface from store in VectorTextFile.
   */
  public class VectorEnumerationText implements Enumeration<ObjectVector> {
    BufferedReader vecBuf;

    public VectorEnumerationText(BufferedReader vecBuf) {
      this.vecBuf = vecBuf;
    }

    /**
     * @return True if more vectors are available. False if vector
     * store is exhausted, including exceptions from reading past EOF.
     */
    public boolean hasMoreElements() {
      try {
        char[] cbuf = new char[1];
        vecBuf.mark(10);
        if (vecBuf.read(cbuf, 0, 1) != -1) {
          vecBuf.reset();
          return true;
        }
        else {
          vecBuf.close();
          return false;
        }
      }
      catch (IOException e) {
        e.printStackTrace();
      }
      return false;
    }

    /**
     * @return Next element if found.
     * @throws NoSuchElementException if no element is available.
     */
    public ObjectVector nextElement() throws NoSuchElementException {
      try {
        return parseVectorLine(vecBuf.readLine());
      }
      catch (IOException e) {
        e.printStackTrace();
        throw (new NoSuchElementException("Failed to get next element from vector store."));
      }
    }
  }
}
