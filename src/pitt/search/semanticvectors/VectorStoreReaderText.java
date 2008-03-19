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

import java.util.Enumeration;
import java.io.*;
import java.lang.Float;
import java.lang.Integer;
import java.util.StringTokenizer;

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

   @see VectorStoreReader
   @see ObjectVector
**/
public class VectorStoreReaderText implements VectorStore {
  private String vectorFileText;
  private boolean hasHeader;
  private BufferedReader inBuf;

  public VectorStoreReaderText (String vectorFileText) throws IOException {
    this. vectorFileText = vectorFileText;
    this.inBuf = new BufferedReader(new FileReader (vectorFileText));
    try {
      // Read number of dimensions from header information.
      String firstLine = inBuf.readLine();
      // Include "-" character to avoid unlikely case that first term is "dimensions"!
      String[] firstLineData = firstLine.split("\\|");
      if ((firstLineData[0].equalsIgnoreCase("-dimensions"))) {
        ObjectVector.vecLength = Integer.parseInt(firstLineData[1]);
        System.err.println("Found first line header, dimensions = " + ObjectVector.vecLength);
        this.hasHeader = true;
      }
      else {
        System.err.println("No file header for file " + vectorFileText +
                           "\nPresuming vector length is: " + (firstLineData.length - 1) +
                           "\nIf this fails, consider rebuilding indexes - existing " +
                           "ones were probably created with old version of software.");
        ObjectVector.vecLength = firstLineData.length - 1;
        this.hasHeader = false;
      }
    } catch (IOException e) {
      System.out.println("Cannot read file: " + vectorFileText + "\n" + e.getMessage());
    }
  }

  public Enumeration getAllVectors() {
    try{
      this.inBuf = new BufferedReader(new FileReader (vectorFileText));
      if (hasHeader) {
        inBuf.readLine();
      }
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    return new VectorEnumerationText(this.inBuf);
  }

  /**
   * Returns an object vector from a text line.
   */
  public static ObjectVector parseVectorLine(String line) throws IOException {
    String[] entries = line.split("\\|");
    if (entries.length != ObjectVector.vecLength + 1) {
      throw new IOException("Found " + (entries.length - 1) + " possible coordinates: "
                            + "expected " + ObjectVector.vecLength);
    }
    String objectName = entries[0];
    float[] tmpVector = new float[ObjectVector.vecLength];
    for (int i = 0; i < ObjectVector.vecLength; ++i) {
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
   * Implements the hasMoreElements() and nextElement() methods
   * to give Enumeration interface from store in VectorTextFile.
   */
  public class VectorEnumerationText implements Enumeration {
    BufferedReader inBuf;

    public VectorEnumerationText(BufferedReader inBuf) {
      this.inBuf = inBuf;
    }

    public boolean hasMoreElements() {
      try {
        char[] cbuf = new char[1];
        inBuf.mark(10);
        if (inBuf.read(cbuf, 0, 1) != -1) {
          inBuf.reset();
          return true;
        }
        else {
          return false;
        }
      }
      catch (IOException e) {
        e.printStackTrace();
      }
      return false;
    }

    public ObjectVector nextElement() {
      try {
        return parseVectorLine(inBuf.readLine());
      }
      catch (IOException e) {
        e.printStackTrace();
      }
      return null;
    }
  }
}
