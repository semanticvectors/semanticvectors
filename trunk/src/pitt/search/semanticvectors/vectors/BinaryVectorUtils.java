/**
   Copyright (c) 2007, University of Pittsburgh

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

package pitt.search.semanticvectors.vectors;

import java.util.ArrayList;
import java.util.Random;
import java.util.logging.Logger;

import org.apache.lucene.util.OpenBitSet;

import pitt.search.semanticvectors.hashing.Bobcat;

/**
 * This class provides standard vector methods, e.g., cosine measure,
 * normalization, tensor utils.
 */
public class BinaryVectorUtils {
  private static final Logger logger = Logger.getLogger(BinaryVectorUtils.class.getCanonicalName());

  /**
   * The orthogonalize function takes an array of vectors and
   * makes them dissimilar from one another (Hamming distance = n/2) in
   * a manner analogous to the Gram-Schmidt process with real vectors. 
   * The vectors are altered in place, so there is no return value.  Note
   * that the output of this function is order dependent, in
   * particular, the jth vector in the array will be made orthogonal
   * to all the previous vectors. Since this means that the last
   * vector is disimilar from all the others, this can be used as a
   * negation function to give an vector for
   * vectors[last] NOT (vectors[0] OR ... OR vectors[last - 1].
   *
   * @param vectors ArrayList of vectors (which are themselves arrays of
   * floats) to be orthogonalized in place.
   */
  public static boolean orthogonalizeVectors(ArrayList<Vector> vectors) {
    long dimension = vectors.get(0).getDimension();
     
    // Go up through vectors in turn, parameterized by k.
    for (int k = 0; k < vectors.size(); ++k) {
     Vector kthVector = vectors.get(k);
     
      if (kthVector.getDimension() != dimension) {
        System.err.println("In orthogonalizeVector: not all vectors have required dimension.");
        return false;
      }
      // Go up to vector k, parameterized by j.
      for (int j = 0; j < k; ++j) {
    	 Vector jthVector = vectors.get(j);
    	 sampleSubtract(((BinaryVector) kthVector).bitSet, ((BinaryVector) jthVector).bitSet); 
           
      }
    }
        
    
    
    return true;
  }
  
  /**
   * Binary equivalent of comparing to projection in a subspace as occurs with real and complex vectors
   * The score is the sum of any overlap greater than 50% across all vector components of the binary pseudo-subspace
   */  
  public static double compareWithProjection(Vector testVector, ArrayList<Vector> vectors) {
	    float score = 0;
	    for (int i = 0; i < vectors.size(); ++i) {
	     score += testVector.measureOverlap(vectors.get(i));
	    }
	    return (float) (score);
	  }
  
  /**
   * This method provides the equivalent of orthogonalization for binary vectors. Rather than making vector k 
   * orthogonal to vector j, we alter vector j so it has a Hamming distance of 1/n to vector k. This is the 
   * analog of orthogonality in real/complex space.
   */
  public static void sampleSubtract(OpenBitSet vector,  OpenBitSet subvector) {	
	  long numchanges =  vector.size()/2 - OpenBitSet.xorCount(vector, subvector); //total common bits - n/2
	  java.util.Random random = new java.util.Random();
	  random.setSeed((long) 23); //for consistency across experiments 
	  OpenBitSet commonGround = (OpenBitSet) vector.clone();
	  //everything different
	  commonGround.xor(subvector);
	    
	  int cnt = 0;
	  
	  //if it is required to introduce random noise to increase the distance between the two vectors
	  if (numchanges > 0)
	  for (int x =0; cnt < numchanges; x++) {	
		 // if (x == 0) System.err.print(cnt+"/"+ numchanges+".."+"loop...");
		  if (x >= vector.size()) x =0;
			double change = random.nextDouble();
			if (!commonGround.get(x) && change > 0.5) {
				vector.fastFlip(x);
				cnt++;
			}
		}
	  //if it is required to introduce commonalities to increase the similarity between the two vectors
	  else if (numchanges < 0)
		  for (int x =0; cnt > numchanges; x++) {	
			 // if (x == 0) System.err.print(cnt+"/"+ numchanges+".."+"loop...");
			  if (x >= vector.size()) x =0;
				double change = random.nextDouble();
				if (commonGround.get(x) && change > 0.5) {
					vector.fastFlip(x);
					cnt--;
				}
			}
	  
  }
  
  public static Vector weightedSuperposition(BinaryVector v1, double weight1, BinaryVector v2, double weight2)
  {
	  BinaryVector conclusion = (BinaryVector) VectorFactory.createZeroVector(VectorType.BINARY, v1.getDimension());
	  OpenBitSet cVote = conclusion.bitSet;
	  OpenBitSet v1vote = v1.bitSet;
	  OpenBitSet v2vote = v2.bitSet;
	  
      Random random = new Random();
      random.setSeed(Bobcat.asLong(v1.writeLongToString())); 
      
       for (int x = 0; x < v1.getDimension(); x++)
       {
    	   double probability = 0;
    	   if (v1vote.get(x)) probability += weight1/(weight1+weight2);
    	   if (v2vote.get(x)) probability += weight2/(weight1+weight2);
    	   
    	   if (random.nextDouble() <= probability)
    	    cVote.fastSet(x);	
       }
        return conclusion;
  }
  
}
