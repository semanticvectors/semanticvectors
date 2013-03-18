/**
   Copyright (c) 2013, the SemanticVectors AUTHORS.

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

package pitt.search.semanticvectors.orthography;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Random;

import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.ObjectVector;
import pitt.search.semanticvectors.VectorStoreRAM;
import pitt.search.semanticvectors.hashing.Bobcat;
import pitt.search.semanticvectors.vectors.ComplexVector;
import pitt.search.semanticvectors.vectors.ComplexVector.Mode;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorType;

/**
 * Class that generates sequences of evenly distributed vectors to represent
 * numbers between two given end points.
 * 
 * @author Manuel Wahle, Trevor Cohen, some cleanup by Dominic Widdows
 */
public class NumberRepresentation {
  /** Random seed used for starting demarcator vectors. */
  private final String startRandomSeen = "*START*";
  /** Random seed used for ending demarcator vectors. */
  private final String endRandomSeed = "*END*";
  /** Maximum acceptable similarity between start and end demarcator vectors/ */
  private final float maxStartEndSimilarity = 0.01f;

  /**
   * Cache of number vectors that have been seen before.
   * 
   * Key to table is string rendering of {@code left:right}, where {@code left} and
   * {@code right} are the numbers at each end of the representation.
   */
  private Hashtable<String, VectorStoreRAM> numberVectorsCache = new Hashtable<String, VectorStoreRAM>();
  
  private FlagConfig flagConfig = null;
  
  /** 'Left' vector, used to represent the beginning of each sequence of number vectors. */
  private Vector vL;
  /** 'Right' vector, used to represent the end of each sequence of number vectors. */
  private Vector vR;

  /**
   * A simple testing / exploration routine that generates a handful of
   * NumberRepresentation vectors and prints out a table of their similarities.
   * 
   * @param args
   */
  public static void main(String[] args) {
    FlagConfig flagConfig;

    try {
      flagConfig = FlagConfig.getFlagConfig(args);
      args = flagConfig.remainingArgs;
    } catch (IllegalArgumentException e) {
      System.err.println(e.getMessage());
      throw e;
    }

    NumberRepresentation NR = new NumberRepresentation(flagConfig);
    VectorStoreRAM VSR = NR.getNumberVectors(1,4);
    System.out.print("\t");
    for (int q=1; q <= VSR.getNumVectors(); q++)
      System.out.print(q+"\t");
    System.out.println();	

    for (int q=1; q <= VSR.getNumVectors(); q++) {
      Enumeration<ObjectVector> VEN = VSR.getAllVectors();
      System.out.print(q);

      while (VEN.hasMoreElements()) {	
        ObjectVector OV = VEN.nextElement();
        System.out.print("\t");
        System.out.printf("%.2f",VSR.getVector(VSR.getNumVectors() +1 - q).measureOverlap(OV.getVector()));
      }
      System.out.println();
    }
  }
  
  /**
   * Initializes an instance of {@link NumberRepresentation} with its start and end vectors,
   * checking that these demarcator vectors are not too close together. 
   * 
   * @param flagConfig Flag configuration, used in particular to control vectortype and dimension. 
   */
  public NumberRepresentation(FlagConfig flagConfig) {
    if (flagConfig == null) throw new NullPointerException("flagConfig cannot be null");
    
    this.flagConfig = flagConfig;
    if (flagConfig.vectortype() == VectorType.COMPLEX)
      ComplexVector.setDominantMode(Mode.CARTESIAN);

    // Generate a vector for the lowest number and one for the highest and make sure they
    // have no significant overlap.
    Random random = new Random(Bobcat.asLong(startRandomSeen));
    vL = VectorFactory.generateRandomVector(
        flagConfig.vectortype(), flagConfig.dimension(), flagConfig.seedlength(), random);
    vL.normalize();

    random.setSeed(Bobcat.asLong(endRandomSeed));
    vR = VectorFactory.generateRandomVector(
        flagConfig.vectortype(), flagConfig.dimension(), flagConfig.seedlength(), random);
    vR.normalize();

    // Small routine to guarantee that end vector has low similarity with start vector.
    String endPadding = "";
    while (Math.abs(vL.measureOverlap(vR)) > maxStartEndSimilarity) {
      endPadding += "*";
      random.setSeed(Bobcat.asLong(endRandomSeed + endPadding));
      vR = VectorFactory.generateRandomVector(
          flagConfig.vectortype(), flagConfig.dimension(), flagConfig.seedlength(), random);
    }  
  }

  /**
   * Gets a sequence of number vectors, the first and last of which are the demarcator vectors,
   * and the intervening members being evenly linearly distributed between these.
   *  
   * @param iStart The number represented by the beginning of the returned sequence.
   * @param iEnd The number represented by the end of the returned sequence.
   * @return VectorStore in memory whose keys are the integers between {@code iStart}
   *         and {@code iEnd} and whose values are appropriate linear combinations of
   *         the demarcator vectors.
   */
  public VectorStoreRAM getNumberVectors(int iStart, int iEnd) {
    if (numberVectorsCache.containsKey(iStart+":"+iEnd))
      return numberVectorsCache.get(iStart+":"+iEnd);

    if (iStart != 0) throw new IllegalArgumentException("Start index must be zero for now.");
    if (iEnd < 1) throw new IllegalArgumentException("End index must be greater than zero for now.");
    int original_iEnd = iEnd;
    if ((iEnd) %2 != 0) iEnd++;
    
    ArrayList<ObjectVector> numberVectors = new ArrayList<ObjectVector>(iEnd + 1);
    for (int i = 0; i <= iEnd; ++i) numberVectors.add(null);
    
    // add them to an arraylist
    ObjectVector ovL = new ObjectVector(Integer.toString(iStart), vL);
    ObjectVector ovR = new ObjectVector(Integer.toString(iEnd), vR);
    numberVectors.set(iStart, ovL);
    numberVectors.set(iEnd, ovR);

    // recursively fill the arraylist with number vectors
    generateNumbers(numberVectors, iStart, iEnd);
    
    VectorStoreRAM theVSR = new VectorStoreRAM(flagConfig);
    for (int q = iStart; q <= iEnd; q++) {
      theVSR.putVector(q, numberVectors.get(q).getVector());
    }

    if (iEnd > original_iEnd) //even number of vectors
      theVSR.removeVector(iEnd);

    numberVectorsCache.put(iStart+":"+iEnd, theVSR);
    return theVSR;
  }


  /**
   * Insert new vector in {@link #numberVectors} at position {@code iLeft + iRight) / 2}
   * by averaging the endpoints, and continue recursively until all indices between
   * endpoints are populated.
   * 
   * Parameters must be indices of vectors that already have representations in {@link #numberVectors}.
   * 
   * @param numberVectors list of number vectors to be populated, passed by reference recursively
   * @param iLeft index of vector in {@link #numberVectors} at "left" end of the region
   * @param iRight index of vector in {@link #numberVectors} at "right" end of the region 
   */
  private void generateNumbers(ArrayList<ObjectVector> numberVectors, int iLeft, int iRight)
  {
    if (Math.abs(iLeft - iRight) <= 1)
      return;

    Vector m = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());
    m.superpose(numberVectors.get(iLeft).getVector(), 1d, null);
    m.superpose(numberVectors.get(iRight).getVector(), 1d, null);
    m.normalize();
    
    int iMiddle = (iLeft + iRight) / 2;
    numberVectors.set(iMiddle, new ObjectVector(Integer.toString(iMiddle), m));

    generateNumbers(numberVectors, iLeft, iMiddle);
    generateNumbers(numberVectors, iMiddle, iRight);
  }
}

