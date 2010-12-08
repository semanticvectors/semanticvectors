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

import java.io.*;
import java.lang.Float;
import java.lang.Integer;
import java.util.Enumeration;
import java.util.StringTokenizer;
import java.util.NoSuchElementException;

/**
   This class provides methods for reading a VectorStore from a textfile.<p>

   The textfile should start with an optional header line,
   "<code>-dimensions|N</code>".<br>
   All subsequent lines should be of the form <br>
   <code>String|Num1|Num2|...|NumN</code><p>

   The serialization currently presumes that the object (in the ObjectVectors)
   should be serialized as a String. <p>

   This class is mainly for interoperability with plain text file
   formats: normal (fast) implementations should use the internal
   VectorStoreReader class that uses Lucene's I/O functions.

   @see VectorStoreReaderLucene
   @see ObjectVector
**/
public class VectorStoreReaderText implements CloseableVectorStore {
  private String vectorFileText;
  private boolean hasHeader;
  private BufferedReader inBuf;

  public VectorStoreReaderText (String vectorFileText) throws IOException {
    this.vectorFileText = vectorFileText;
    this.inBuf = new BufferedReader(new FileReader(vectorFileText));
    try {
      // Read number of dimensions from header information.
      String firstLine = inBuf.readLine();
      // Include "-" character to avoid unlikely case that first term is "dimensions"!
      String[] firstLineData = firstLine.split("\\|");
      if ((firstLineData[0].equalsIgnoreCase("-dimensions"))) {
        Flags.dimension = Integer.parseInt(firstLineData[1]);
        this.hasHeader = true;
      }
      else {
        System.err.println("No file header for file " + vectorFileText +
                           "\nPresuming vector length is: " + (firstLineData.length - 1) +
                           "\nIf this fails, consider rebuilding indexes - existing " +
                           "ones were probably created with old version of software.");
        Flags.dimension = firstLineData.length - 1;
        this.hasHeader = false;
      }
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
      if (hasHeader) {
        vecBuf.readLine();
      }
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
  public static ObjectVector parseVectorLine(String line) throws IOException {
    String[] entries = line.split("\\|");
    if (entries.length != Flags.dimension + 1) {
      throw new IOException("Found " + (entries.length - 1) + " possible coordinates: "
                            + "expected " + Flags.dimension);
    }
    String objectName = entries[0];
    float[] tmpVector = new float[Flags.dimension];
    for (int i = 0; i < Flags.dimension; ++i) {
      tmpVector[i] = Float.parseFloat(entries[i + 1]);
    }
    return new ObjectVector(objectName, tmpVector);
  }

  /**
   * Given an object, get its corresponding vector <br>
   * This implementation only works for string objects so far <br>
   * @param desiredObject - the string identifying the object being searched for.
   */
  public float[] getVector(Object desiredObject) {
    System.err.print("Seeking vector for ... " + desiredObject + " ... ");
    try {
      this.close();
      inBuf = new BufferedReader(new FileReader (vectorFileText));
      if (this.hasHeader) {
        inBuf.readLine();
      }
      String line;
      while ((line = inBuf.readLine()) != null) {
        String[] entries = line.split("\\|");
        if (entries[0].equals(desiredObject)) {
          System.err.println("Found it ...");
          return (parseVectorLine(line).getVector());
        }
      }
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    System.err.println("Didn't find it ...");
    return null;
  }

  /**
   * Trivial (costly) implementation of getNumVectors that iterates and counts vectors.
   */
  public int getNumVectors() {
    Enumeration allVectors = this.getAllVectors();
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
