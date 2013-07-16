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
import pitt.search.semanticvectors.vectors.BinaryVector;
import pitt.search.semanticvectors.vectors.BinaryVectorUtils;
import pitt.search.semanticvectors.vectors.ComplexVector;
import pitt.search.semanticvectors.vectors.ComplexVector.Mode;
import pitt.search.semanticvectors.vectors.CircleLookupTable;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorType;
import pitt.search.semanticvectors.vectors.VectorUtils;

/**
 * Class that generates sequences of evenly distributed vectors to represent
 * numbers between two given end points.
 * 
 * @author Manuel Wahle, Trevor Cohen, some cleanup by Dominic Widdows
 */
public class NumberRepresentation {
  /** Random seed used for starting demarcator vectors. */
  private String startRandomSeed = "*START*";
  /** Random seed used for ending demarcator vectors. */
  private String endRandomSeed = "*END*";

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

  @Deprecated
  /**
   * A simple testing / exploration routine that generates a handful of
   * NumberRepresentation vectors and prints out a table of their similarities.
   * 
   * Deprecated - see instead {@link NumberRepresentationTest}.
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
    VectorStoreRAM VSR = NR.getNumberVectors(1,5);
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
        System.out.printf("%.2f", VSR.getVector(VSR.getNumVectors() + 1 - q).measureOverlap(OV.getVector()));
      }
      System.out.println();
    }
  }

  /**
   * Initializes an instance of {@link NumberRepresentation} with its start and end vectors,
   * checking that these demarcator vectors are not too close together. 
   * 
   * Allows for the specification of a start and end seed, so mutually near-orthogonal sets
   * of demarcator vectors can be created
   * 
   * @param flagConfig Flag configuration, used in particular to control vectortype and dimension. 
   */
  public NumberRepresentation(FlagConfig flagConfig, String startSeed, String endSeed) {
    if (flagConfig == null) throw new NullPointerException("flagConfig cannot be null");

    this.startRandomSeed = startSeed;
    this.endRandomSeed = endSeed;

    this.flagConfig = flagConfig;
    if (flagConfig.vectortype() == VectorType.COMPLEX)
      ComplexVector.setDominantMode(Mode.CARTESIAN);

    // Generate a vector for the lowest number and one for the highest and make sure they
    // have no significant overlap.
    Random random = new Random(Bobcat.asLong(startRandomSeed));
    vL = VectorFactory.generateRandomVector(
        flagConfig.vectortype(), flagConfig.dimension(), flagConfig.seedlength(), random);
    vL.normalize();

    random.setSeed(Bobcat.asLong(endRandomSeed));
    vR = VectorFactory.generateRandomVector(
        flagConfig.vectortype(), flagConfig.dimension(), flagConfig.seedlength(), random);
    vR.normalize();

    // Small routine to guarantee that end vector has low similarity with start vector.
    ArrayList<Vector> toOrthogonalize = new ArrayList<Vector>();
    toOrthogonalize.add(vL);
    toOrthogonalize.add(vR);
    VectorUtils.orthogonalizeVectors(toOrthogonalize);
  }

  /**
   * This constructor allows for the generation of sets of number vectors, using
   * a standard random seed
   * 
   * @param flagConfig 
   */
  public NumberRepresentation(FlagConfig flagConfig) {
    this(flagConfig,  "*START*", "*END*");
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

    if (iEnd < iStart) throw new IllegalArgumentException(
        String.format(
            "End index (%d) should be greater than start index (%d).", iEnd, iStart));

    VectorStoreRAM theVSR = new VectorStoreRAM(flagConfig);
    for (int i = 0; i <= iEnd - iStart; ++i) {
      Vector ithNumberVector = VectorUtils.weightedSuperposition(vL, iEnd-iStart-i, vR, i);
      theVSR.putVector(iStart + i, ithNumberVector);
    }

    numberVectorsCache.put(iStart+":"+iEnd, theVSR);
    return theVSR;
  }
}

