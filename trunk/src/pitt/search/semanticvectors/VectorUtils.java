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

package pitt.search.semanticvectors;

import java.lang.IllegalArgumentException;
import java.lang.Integer;
import java.lang.Math;
import java.lang.Exception;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Enumeration;
import java.util.Random;

/**
 * This class provides standard vector methods, e.g., cosine measure,
 * normalization, tensor utils.
 */
public class VectorUtils{

	public static void printVector(float[] vector) {
		for (int i = 0; i < vector.length - 1; ++i) {
			System.out.print(vector[i] + "|");
		}
		// Print last coordinate followed by newline, not "|".
		System.out.println(vector[vector.length - 1]);
	}

	public static void printMatrix(float[][] matrix) {
		for (float[] vector: matrix) {
			printVector(vector);
		}
	}

	public static float[] Floats(double[] vector) {
		float[] output = new float[vector.length];
		for (int i = 0; i < vector.length; ++i) {
			output[i] = (float) vector[i];
		}
		return output;
	}

	public static float[][] Floats(double[][] matrix) {
		float[][] output = new float[matrix.length][matrix.length];
		for (int i = 0; i < matrix.length; ++i) {
			output[i] = Floats(matrix[i]);
		}
		return output;
	}

	/**
	 * Check whether a vector is all zeros.
	 */
	static final float kTolerance = 0.0001f;
	public static boolean isZeroVector(float[] vec) {
		for (int i = 0; i < vec.length; ++i) {
			if (Math.abs(vec[i]) > kTolerance) {
				return false;
			}
		}
		return true;
	}

	public static boolean isZeroTensor(float[][] ten) {
		for (int i = 0; i < ten.length; ++i) {
			if (!isZeroVector(ten[i])) {
				return false;
			}
		}
		return true;
	}

	public static float[][] createZeroTensor(int dim) {
		float[][] newTensor = new float[dim][dim];
		for (int i = 0; i < dim; ++i) {
			for (int j = 0; j < dim; ++j) {
				newTensor[i][j] = 0;
			}
		}
		return newTensor;
	}

	/**
	 * Returns the scalar product (dot product) of two vectors
	 * for normalized vectors this is the same as cosine similarity.
	 * @param vec1 First vector.
	 * @param vec2 Second vector.
	 */
	public static float scalarProduct(float[] vec1, float[] vec2){
		float result = 0;
		for (int i = 0; i < vec1.length; ++i) {
	    result += vec1[i] * vec2[i];
		}
		return result;
	}


	/* Euclidean distance metric */
	public static float euclideanDistance(float[] vec1, float[] vec2){
		float distance=0;
		for (int i = 0; i < vec1.length; ++i) {
	    distance += (vec1[i] - vec2[i]) * (vec1[i] - vec2[i]);
		}
		return (float)Math.sqrt(distance);
	}

	/**
	 * Get nearest vector from list of candidates.
	 * @param vector The vector whose nearest neighbor is to be found.
	 * @param candidates The list of vectors from whoe the nearest is to be chosen.
	 * @return Integer value referencing the position in the candidate list of the nearest vector.
	 */
	public static int getNearestVector(float[] vector, float[][] candidates) {
		int nearest = 0;
		float minDist = euclideanDistance(vector, candidates[0]);
		float thisDist = minDist;
		for (int i = 1; i < candidates.length; ++i) {
	    thisDist = euclideanDistance(vector, candidates[i]);
	    if (thisDist < minDist) {
				minDist = thisDist;
				nearest = i;
	    }
		}
		return nearest;
	}


	/**
	 * Returns the normalized version of a vector, i.e. same direction,
	 * unit length.
	 * @param vec Vector whose normalized version is requested.
	 */
	public static float[] getNormalizedVector(float[] vec){
		float norm = 0;
		int i;
		float[] tmpVec = new float[vec.length];
		for (i = 0; i < vec.length; ++i) {
	    tmpVec[i] = vec[i];
		}
		for (i = 0; i < tmpVec.length; ++i) {
	    norm += tmpVec[i]*tmpVec[i];
		}
		norm = (float)Math.sqrt(norm);
		for (i = 0; i < tmpVec.length; ++i) {
	    tmpVec[i] = tmpVec[i]/norm;
		}
		return tmpVec;
	}

	/**
	 * Returns the normalized version of a 2 tensor, i.e. an array of
	 * arrays of floats.
	 */
	public static float[][] getNormalizedTensor(float[][] tensor){
		int dim = tensor[0].length;
		float[][] normedTensor = new float[dim][dim];
		float norm = (float)Math.sqrt(getInnerProduct(tensor, tensor));
		for (int i = 0; i < dim; ++i) {
	    for (int j = 0; j < dim; ++j) {
				normedTensor[i][j] = tensor[i][j]/norm;
	    }
		}
		return normedTensor;
	}

	/**
	 * Returns a 2-tensor which is the outer product of 2 vectors.
	 */
	public static float[][] getOuterProduct(float[] vec1, float[] vec2) {
		int dim = vec1.length;
		float[][] outProd = new float[dim][dim];
		for (int i=0; i<dim; ++i) {
	    for (int j=0; j<dim; ++j) {
				outProd[i][j] = vec1[i] * vec2[j];
	    }
		}
		return outProd;
	}

	/**
	 * Returns the sum of two tensors.
	 */
	public static float[][]	getTensorSum(float[][] ten1, float[][] ten2) {
		int dim = ten1[0].length;
		float[][] result = new float[dim][dim];
		for (int i = 0; i < dim; ++i) {
	    for (int j = 0; j < dim; ++j) {
				result[i][j] += ten1[i][j] + ten2[i][j];
	    }
		}
		return result;
	}

	/**
	 * Returns the inner product of two tensors.
	 */
	public static float getInnerProduct(float[][] ten1, float[][]ten2){
		float result = 0;
		int dim = ten1[0].length;
		for (int i = 0; i < dim; ++i) {
	    for (int j = 0; j < dim; ++j) {
				result += ten1[i][j] * ten2[j][i];
	    }
		}
		return result;
	}

	/**
	 * Returns the convolution of two vectors; see Plate,
	 * Holographic Reduced Representation, p. 76.
	 */
	public static float[] getConvolutionFromTensor(float[][] tensor){
		int dim = tensor.length;
		float[] conv = new float[2*dim - 1];
		for (int i = 0; i < dim; ++i) {
	    conv[i] = 0;
	    conv[conv.length - 1 - i] = 0;
	    for (int j = 0; j <= i; ++j) {
				// Count each pair of diagonals.
				// TODO(widdows): There may be transpose conventions to check.
				conv[i] += tensor[i-j][j];
				if (i != dim - 1) { // Avoid counting lead diagonal twice.
					conv[conv.length - 1 - i] = tensor[dim-1-i+j][dim-1-j];
				}
	    }
		}
		return VectorUtils.getNormalizedVector(conv);
	}

	/**
	 * Returns the convolution of two vectors; see Plate,
	 * Holographic Reduced Representation, p. 76.
	 */
	public static float[] getConvolutionFromVectors(float[] vec1, float[] vec2) {
		int dim = vec1.length;
		float[] conv = new float[2 * dim - 1];
		for (int i = 0; i < dim; ++i) {
	    conv[i] = 0;
	    conv[conv.length - 1 - i] = 0;
	    for (int j = 0; j <= i; ++j) {
				// Count each pair of diagonals.
				conv[i] += vec1[i-j] * vec2[j];
				if (i != dim - 1) { // Avoid counting lead diagonal twice.
					conv[conv.length - 1 - i] = vec1[dim-1-i+j] * vec2[dim-1-j];
				}
	    }
		}
		return VectorUtils.getNormalizedVector(conv);
	}

	/**
	 * Sums the scalar products of a vector and each member of a list of
	 * vectors.  If the list is orthonormal, this gives the cosine
	 * similarity of the test vector and the subspace generated by the
	 * orthonormal vectors.
	 */
	public static float getSumScalarProduct(float[] testVector, ArrayList<float[]> vectors) {
		float score = 0;
		for (int i = 0; i < vectors.size(); ++i) {
			score += scalarProduct(vectors.get(i), testVector);
		}
		return score;
	}


	/**
	 * The orthogonalize function takes an array of vectors and
	 * orthogonalizes them using the Gram-Schmidt process. The vectors
	 * are orthogonalized in place, so there is no return value.  Note
	 * that the output of this function is order dependent, in
	 * particular, the jth vector in the array will be made orthogonal
	 * to all the previous vectors. Since this means that the last
	 * vector is orthogonal to all the others, this can be used as a
	 * negation function to give an vector for
	 * vectors[last] NOT (vectors[0] OR ... OR vectors[last - 1].
	 *
	 * @param vectors ArrayList of vectors (which are themselves arrays of
	 * floats) to be orthogonalized in place.
	 */
	public static boolean orthogonalizeVectors(ArrayList<float[]> vectors) {
		vectors.set(0, getNormalizedVector(vectors.get(0)));
		// Go up through vectors in turn, parameterized by k.
		for (int k = 0; k < vectors.size(); ++k) {
	    float[] kthVector = vectors.get(k);
	    if (kthVector.length != ObjectVector.vecLength) {
				System.err.println("In orthogonalizeVector: not all vectors have required dimension.");
				return false;
	    }
	    // Go up to vector k, parameterized by j.
	    for (int j = 0; j < k; ++j) {
				float[] jthVector = vectors.get(j);
				float dotProduct = scalarProduct(kthVector, jthVector);
				// Subtract relevant amount from kth vector.
				for (int i = 0; i < ObjectVector.vecLength; ++i) {
					kthVector[i] -= dotProduct * jthVector[i];
				}
	    }
			// Normalize the vector we're working on.
			vectors.set(k, getNormalizedVector(kthVector));
		}

		return true;
	}

  /**
   * Generates a basic sparse vector (dimension = ObjectVector.vecLength)
   * with mainly zeros and some 1 and -1 entries (seedLength/2 of each)
   * each vector is an array of length seedLength containing 1+ the index of a non-zero
   * value, signed according to whether this is a + or -1.
   * <br>
   * e.g. +20 would indicate a +1 in position 19, +1 would indicate a +1 in position 0.
   *      -20 would indicate a -1 in position 19, -1 would indicate a -1 in position 0.
   * <br>
   * The extra offset of +1 is because position 0 would be unsigned,
   * and would therefore be wasted. Consequently we've chosen to make
   * the code slightly more complicated to make the implementation
   * slightly more space efficient.
   *
   * @return Sparse representation of basic ternary vector. Array of
   * short signed integers, indices to the array locations where a
   * +/-1 entry is located.
   */
	public static short[] generateRandomVector(int seedLength, Random random) {
    boolean[] randVector = new boolean[ObjectVector.vecLength];
    short[] randIndex = new short[seedLength];

    int testPlace, entryCount = 0;

    /* put in +1 entries */
    while (entryCount < seedLength / 2) {
      testPlace = random.nextInt(ObjectVector.vecLength);
      if (!randVector[testPlace]) {
        randVector[testPlace] = true;
        randIndex[entryCount] = new Integer(testPlace + 1).shortValue();
        entryCount++;
      }
    }

    /* put in -1 entries */
    while (entryCount < seedLength) {
      testPlace = random.nextInt (ObjectVector.vecLength);
      if (!randVector[testPlace]) {
        randVector[testPlace] = true;
        randIndex[entryCount] = new Integer((1 + testPlace) * -1).shortValue();
        entryCount++;
      }
    }

    return randIndex;
  }

	/**
	 * Given an array of floats, return an array of indices to the n largest values.
	 */
	public static short[] getNLargestPositions(float[] values, int numResults) {
		// TODO(dwiddows): Find some apprpriate "CHECK" function to use here.
		if (numResults > values.length) {
			System.err.println("Asking for highest " + numResults
												 + " entries out of only " + values.length);
			throw new IllegalArgumentException();
		}

		LinkedList<Integer> largestPositions = new LinkedList<Integer>();

		// Initialize result list if just starting.
		largestPositions.add(new Integer(0));
		float threshold = values[0];

		for (int i = 0; i < values.length; ++i) {
	    if (values[i] > threshold || largestPositions.size() < numResults) {
				boolean added = false;
				for (int j = 0; j < largestPositions.size(); ++j) {
					// Add to list if this is right place.
					if (values[i] > values[largestPositions.get(j).intValue()] && added == false) {
						largestPositions.add(j, new Integer(i));
						added = true;
					}
				}
				// Prune list if there are already numResults.
				if (largestPositions.size() > numResults) {
					largestPositions.removeLast();
					threshold = values[largestPositions.getLast().intValue()];
				} else {
					if (added == false) {
						largestPositions.add(new Integer(i));
					}
				}
	    }
		}

		// CHECK
		if (largestPositions.size() != numResults) {
			System.err.println("We have " + largestPositions.size()
												 + " results. Expecting " + numResults);			
			throw new IllegalArgumentException();
		}
		Object[] intArray = largestPositions.toArray();
		short[] results = new short[numResults];
		for (int i = 0; i < numResults; ++i) {
			results[i] = ((Integer)intArray[i]).shortValue();
		}
		return results;
	}

	/**
	 * Take a vector of floats and simplify by quantizing to a sparse format. Lossy.
	 */
	public static short[] floatVectorToSparseVector(float[] floatVector, int seedLength) {
		// TODO(dwiddows): Find some appropriate "CHECK" function to use here.
		if (seedLength > floatVector.length) {
			System.err.println("Asking sparse form of length " + seedLength +
												 " from float vector of length " + floatVector.length);
			throw new IllegalArgumentException();
		}

		short[] topN = getNLargestPositions(floatVector, seedLength/2);

		float[] inverseVector = new float[floatVector.length];
		for (int i = 0; i < floatVector.length; ++i) {
			inverseVector[i] = -1 * floatVector[i];
		}
		short[] lowN = getNLargestPositions(inverseVector, seedLength/2);

		short[] sparseVector = new short[seedLength];
		for (int i = 0; i < seedLength/2; ++i) {
			sparseVector[i] = new Integer(topN[i] + 1).shortValue();
			sparseVector[seedLength/2 + i] = new Integer(-1 * (lowN[i] + 1)).shortValue();
		}
		return sparseVector;
	}

	/**
	 * Translate sparse format (listing of offsets) into full float vector.
	 * The random vector is in condensed (signed index + 1)
	 * representation, and is converted to a full float vector by adding -1 or +1 to the
	 * location (index - 1) according to the sign of the index.
	 * (The -1 and +1 are necessary because there is no signed
	 * version of 0, so we'd have no way of telling that the
	 * zeroth position in the array should be plus or minus 1.)
	 */
	public static float[] sparseVectorToFloatVector(short[] sparseVector, int dimension) {
		float[] output = new float[dimension];
		for (int i = 0; i < dimension; ++i) {
			output[i] = 0;
		}
		for (int i = 0; i < sparseVector.length; ++i) {
			output[Math.abs(sparseVector[i]) - 1] = Math.signum(sparseVector[i]);
		}
		return output;
	}

	/**
	 * This method implements rotation as a form of vector permutation,
	 * as described in Sahlgren, Holst and Kanervi 2008. This supports
	 * encoding of N-grams, as rotating random vectors serves as a convenient
	 * alternative to random permutation
	 * @param indexVector the sparse vector to be permuted
	 * @param rotation the direction and number of places to rotate
	 * @return sparse vector with permutation
	 */
	public static short[] permuteVector (short[] indexVector, int rotation)	{
		short[] permutedVector = new short[indexVector.length];
		for (int x = 0; x < permutedVector.length; x++) {
			int newIndex = Math.abs(indexVector[x]);
			int sign = Integer.signum(indexVector[x]);
			// rotate vector
			newIndex += rotation;
			if (newIndex > ObjectVector.vecLength) newIndex = newIndex - ObjectVector.vecLength;
			if (newIndex < 1) newIndex = ObjectVector.vecLength + newIndex;
			newIndex = newIndex * sign;
			permutedVector[x] = (short) newIndex;
		}
		return permutedVector;
	}

	/**
	 * This method implements rotation as a form of vector permutation,
	 * as described in Sahlgren, Holst and Kanervi 2008. This supports
	 * encoding of N-grams, as rotating random vectors serves as a convenient
	 * alternative to random permutation
	 * @param indexVector the sparse vector to be permuted
	 * @param rotation the direction and number of places to rotate
	 * @return  vector with permutation
	 */

	public static float[] permuteVector (float[] indexVector, int rotation)	{
		// Correct for unlikely possibility that rotation specified > indexVector.length
		if (Math.abs(rotation) > indexVector.length)
			rotation = rotation % indexVector.length;
		float[] permutedVector = new float[indexVector.length];
		int max = indexVector.length;
		
		for (int x = 0; x < max; x++) {
			int newIndex = x + rotation;
			if (newIndex >= max) newIndex = newIndex - max;
			if (newIndex < 0) newIndex = max + newIndex;
			permutedVector[newIndex] = indexVector[x];
		}
		
			return permutedVector;
	}
	
	/**
	 * Add two vectors. Overloaded vector to handle either float[] + float[] or float[] + sparse vector
	 * @param vector1 	initial vector
	 * @param vector2	vector to be added 
	 * @param weight	weight (presently only term frequency implemented - may need this to take floats later)
	 * @return sum of two vectors
	 */
	
	public static float[] addVectors(float[] vector1, float[] vector2, int weight)
	{	float[] sum = vector1;
		for (int x=0; x < sum.length; x++)
		sum[x] = sum[x] + vector2[x]*weight;
		return sum;
	}

	/**
	 * Add two vectors. Overloaded vector to handle either float[] + float[] or float[] + sparse vector
	 * @param vector1 	initial vector
	 * @param vector2	vector to be added 
	 * @param weight	weight (presently only term frequency implemented - may need this to take floats later)
	 * @return sum of two vectors
	 */
	
	public static float[] addVectors(float[] vector1, short[] sparseVector, int weight)
	{	float[] sum = vector1;
	for (int i = 0; i < sparseVector.length; ++i) {
		short index = sparseVector[i];
		sum[Math.abs(index) - 1] +=  Math.signum(index)* weight;
	}
		return sum;
	}
	
}


