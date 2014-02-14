/**
   Copyright (c) 2011, the SemanticVectors AUTHORS.

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

/**
 * Class that provides utilities for generating special permutations.
 *
 * Permutations themselves are just represented as int[] arrays saying.  It is presumed that
 * each such array is a permutation of the numbers from 1 to n, where n is the length of the
 * array.  This invariant could be enforced by making a Permutation class but this has not been
 * considered necessary to date.
 * 
 * @author Dominic Widdows
 */
public class PermutationUtils {

  private PermutationUtils() {}

  /**
   * Returns dimension for real and complex vectors, or dimension / 64 for binary vectors.
   */
  public static int getPermutationLength(VectorType vectorType, int dimension) {
    return (vectorType != VectorType.BINARY) ? dimension : dimension / 64; 
  }
  
  /**
   * Creates a shift permutation that can be applied to vectors of this type and dimension.
   * 
   * @param vectorType necessary argument because binary vectors are only permuted in 64-bit chunks
   * @param dimension dimension of vectors to be permuted
   * @param shift number of places to shift the vector
   * @return array of length given by {@link #getPermutationLength}.
   */
  public static int[] getShiftPermutation(VectorType vectorType, int dimension, int shift) {
    
	//avoid breaking up unit circle components with complex vectors
	if (vectorType.equals(VectorType.COMPLEX) && (shift %2 != 0)) shift+=Math.signum(shift); 
	
	int permutationLength = getPermutationLength(vectorType, dimension);
    int[] permutation = new int[permutationLength];

    for (int i = 0; i < permutationLength; ++i) {
      int entry = (i + shift) % permutationLength;
      if (entry < 0) entry += permutationLength;
      permutation[i] = entry;
    }
    return permutation;
  }

  public static int[] getInversePermutation(int[] permutation) {
    int[] inversePermutation = new int[permutation.length];
    for (int x=0; x < permutation.length; x++) {
      inversePermutation[permutation[x]] = x;
    }
    return inversePermutation;  
  }
}
