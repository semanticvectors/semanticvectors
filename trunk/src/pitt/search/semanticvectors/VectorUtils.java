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

import java.lang.Math;
import java.util.LinkedList;
import java.util.Enumeration;

/**
 * This class provides a basic vector methods, e.g., cosine measure, normalization,
 * and nearest neighbor search.
 */
public class VectorUtils{

    /**
     * Returns the scalar product (dot product) of two vectors
     * for normalized vectors this is the same as cosine similarity.
     * @param vec1 First vector.
     * @param vec2 Second vector.
     */
    public static float scalarProduct(float[] vec1, float[] vec2){
	float result = 0;
	for( int i=0; i<vec1.length; i++ ){
	    result += vec1[i]*vec2[i];
	}
	return result;
    }

    /**
     * Returns the normalized version of a vector, i.e. same direction, unit length.
     * @param vec Vector whose normalized version is requested.
     */
    public static float[] getNormalizedVector(float[] vec){
	float norm = 0;
	int i;
	float[] tmpVec = new float[vec.length];
	for( i=0; i<vec.length; i++ ){
	    tmpVec[i] = vec[i];
	}
	for( i=0; i<tmpVec.length; i++ ){
	    norm += tmpVec[i]*tmpVec[i];
	}
	norm = (float)Math.sqrt(norm);
	for( i=0; i<tmpVec.length; i++ ){
	    tmpVec[i] = tmpVec[i]/norm;
	}
	return tmpVec;
    }

    /**
     * Returns the normalized version of a 2 tensor, i.e. an array of arrays of floats.
     */
    public static float[][] getNormalizedTensor(float[][] tensor){
	int dim = tensor[0].length;
	float[][] normedTensor = new float[dim][dim];
	float norm = (float)Math.sqrt(getInnerProduct(tensor, tensor));
	for(int i=0; i<dim; ++i){
	    for(int j=0; j<dim; ++j){
		normedTensor[i][j] = tensor[i][j]/norm;
	    }
	}
	return normedTensor;
    }

    /**
     * Returns a 2-tensor which is the outer product of 2 vectors.
     */
    public static float[][] getOuterProduct(float[] vec1, float[] vec2){
	int dim = vec1.length;
	float[][] outProd = new float[dim][dim];
	for(int i=0; i<dim; ++i){
	    for(int j=0; j<dim; ++j){
		outProd[i][j] = vec1[i] * vec2[j];
	    }
	}
	return outProd;
    }

    /**
     * Returns the inner product of two tensors.
     */
    public static float getInnerProduct(float[][] ten1, float[][]ten2){
	float result = 0;
	int dim = ten1[0].length;
	for(int i=0; i<dim; ++i){
	    for(int j=0; j<dim; ++j){
		result += ten1[i][j] * ten2[j][i];
	    }
	}
	return result;
    }

    /**
     * Returns the convolution of two vectors; see Plate,
     * Holographic Reduced Represenation, p. 76.
     */
    public static float[] getConvolution(float[] vec1, float[] vec2){
	int dim = vec1.length;
	float[] conv = new float[2*dim - 1];
	for(int i=0; i < dim; ++i){
	    conv[i] = 0;
	    conv[conv.length - 1 - i] = 0;
	    for(int j=0; j<=i; ++j){
		// Count each pair of diagonals.
		conv[i] += vec1[i-j] * vec2[j];
		if ( i != dim-1 ) { // Avoid counting lead diagonal twice.
		    conv[conv.length - 1 - i] = vec1[dim-1-i+j] * vec2[dim-1-j];
		}
	    }
	}
	return VectorUtils.getNormalizedVector(conv);
    }
}
