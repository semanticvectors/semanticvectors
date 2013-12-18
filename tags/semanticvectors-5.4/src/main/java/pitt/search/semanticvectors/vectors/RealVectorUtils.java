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

package pitt.search.semanticvectors.vectors;

import java.util.List;
import java.util.logging.Logger;

import cern.colt.matrix.tfcomplex.impl.DenseFComplexMatrix1D;
import cern.colt.matrix.tfloat.impl.DenseFloatMatrix1D;
import cern.jet.math.tfcomplex.FComplex;

public class RealVectorUtils {
  private static final Logger logger = Logger.getLogger(RealVectorUtils.class.getCanonicalName());

  /**
   * Takes an array of vectors and orthogonalizes them using the Gram-Schmidt process.
   * 
   * The vectors are orthogonalized in place, so there is no return value.  Note that the output
   * of this function is order dependent, in particular, the jth vector in the array will be made
   * orthogonal to all the previous vectors. Since this means that the last
   * vector is orthogonal to all the others, this can be used as a negation function to give an
   * vector for vectors[last] NOT (vectors[0] OR ... OR vectors[last - 1].
   *
   * @param list ArrayList of real vectors to be orthogonalized in place.
   */
  public static boolean orthogonalizeVectors(List<Vector> list) {
    int dimension = list.get(0).getDimension();
    // Go up through vectors in turn, parameterized by k.
    for (int k = 0; k < list.size(); ++k) {
      Vector kthVector = list.get(k);
      if (kthVector.getVectorType() != VectorType.REAL) throw new IncompatibleVectorsException();
      kthVector.normalize();
      if (kthVector.getDimension() != dimension) {
        logger.warning("In orthogonalizeVector: not all vectors have required dimension.");
        return false;
      }
      // Go up to vector k, parameterized by j.
      for (int j = 0; j < k; ++j) {
        Vector jthVector = list.get(j);
        double dotProduct = kthVector.measureOverlap(jthVector);
        // Subtract relevant amount from kth vector.
        kthVector.superpose(jthVector, -dotProduct, null);
        // And renormalize each time.
        kthVector.normalize();
      }
    }
    return true;
  }
  
  /**
   * Returns the circular convolution of the two input vectors.
   * 
   * See Plate, Holographic Reduced Representations, Section 3.1
   */
  public static RealVector fftConvolution(RealVector first, RealVector second) {
    IncompatibleVectorsException.checkVectorsCompatible(first, second);
    DenseFloatMatrix1D coltVec1 = new DenseFloatMatrix1D(first.getCoordinates());
    DenseFloatMatrix1D coltVec2 = new DenseFloatMatrix1D(second.getCoordinates());
    int dimension = first.getDimension();

    
    DenseFComplexMatrix1D fft1 = coltVec1.getFft();
    DenseFComplexMatrix1D fft2 = coltVec2.getFft();

    for (int i = 0; i < dimension; i++ ) {
      fft1.setQuick(i, FComplex.mult(fft1.getQuick(i), fft2.getQuick(i)));
    }

    fft1.ifft(true);

    DenseFloatMatrix1D coltResult = ((DenseFloatMatrix1D)(fft1.getRealPart()));
    float[] coordinates = coltResult.elements();
    RealVector result = new RealVector(coordinates);
    return result;
  }
  
  /**
   * Return the normalized convolution of normalized vectors.
   * 
   * (This would probably be needed for inverse convolution to work, if we used scalar
   * product rather than cosine similarity for {@link RealVector#measureOverlap(Vector)}.)
   */
  public static RealVector normalizedConvolution(RealVector first, RealVector second) {
    first.normalize();
    second.normalize();
    RealVector convolution = fftConvolution(first, second);
    convolution.normalize();
    return convolution;
  }


  /**
   * Returns the involution (the vector with the coordinates reversed).
   */
  public static RealVector getInvolution(RealVector vector) {
    vector.sparseToDense();
    float[] coordinates = vector.getCoordinates();
    int dimension = vector.getDimension();
    float[] involution = new float[dimension];
    involution[0] = coordinates[0];
    for (int i = 1; i < dimension; ++i) {
      involution[i] = coordinates[dimension - i];
    }
    return new RealVector(involution);
  }
  
  /**
   * Returns the approximate inverse convolution, the circular correlation.
   * Only expected to be an approximate inverse in high dimensions.
   *
   * See Plate, Holographic Reduced Representations, Section 3.1.3
   */
  public static RealVector fftApproxInvConvolution(RealVector first, RealVector second) {
    return fftConvolution(getInvolution(first), second);
  }
}
