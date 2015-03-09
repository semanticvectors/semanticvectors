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

import java.io.IOException;
import java.util.Random;
import java.util.logging.Logger;

import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;

import pitt.search.semanticvectors.vectors.ComplexVector.Mode;

/**
 * Real number implementation of Vector.
 * 
 * <p>
 * Supports both sparse and dense formats.  Some methods automatically transform from sparse to
 * dense format. (Method documentation should cover this when there are performance consequences.
 * 
 * @author Dominic Widdows
 */
public class RealVector implements Vector {
  
  /**
   * Enumeration of binding operation options. Change at compile-time to experiment.
   */
  public enum RealBindMethod {
    /**
     * Uses permutation operations, as in Sahlgren et al.
     * Fast, exact inverse, but lossy memory of slot-filling. 
     */
    PERMUTATION,
    /**
     * Uses convolution operations, as in Mewhort and Jones, BEAGLE.
     * Slower, but optimized using Fast Fourier Transforms.
     * Approximate inverse, but keeps memory of slot-filling.
     */
    CONVOLUTION
  }
  
  public static RealBindMethod BIND_METHOD = RealBindMethod.CONVOLUTION;
  public static void setBindType(RealBindMethod bindMethod) {
    logger.info("Globally setting real vector BIND_METHOD to: '" + bindMethod + "'");
    BIND_METHOD = bindMethod;
  }
  
  public static final Logger logger = Logger.getLogger(RealVector.class.getCanonicalName());

  /** Returns {@link VectorType#REAL} */
  public VectorType getVectorType() { return VectorType.REAL; }

  private final int dimension;
  /**
   * Dense representation.  Coordinates can be anything expressed by floats.
   */
  private float[] coordinates;
  /** 
   * Sparse representation.  Coordinates can only be +/-1.  Array of short signed integers, 
   * indices to the array locations where a +/-1 entry is located.
   * See also {@link #generateRandomVector}.
   */ 
  private short[] sparseOffsets;
  private boolean isSparse;

  protected RealVector(int dimension) {
    this.dimension = dimension;
    this.sparseOffsets = new short[0];
    this.isSparse = true;
  }

  /**
   * Returns a new copy of this vector, in dense format.
   */
  public RealVector copy() {
    if (isSparse) {
      RealVector copy = new RealVector(dimension);
      copy.sparseOffsets = new short[sparseOffsets.length];
      for (int i = 0; i < sparseOffsets.length; ++i) {
        copy.sparseOffsets[i] = sparseOffsets[i];
      }
      return copy;
    } else {
      float[] coordinatesCopy = new float[dimension];
      for (int i = 0; i < dimension; ++i) {
        coordinatesCopy[i] = coordinates[i];
      }
      return new RealVector(coordinatesCopy);
    }
  }

  public String toString() {
    StringBuilder debugString = new StringBuilder("RealVector.");
    // TODO(widdows): Add heap location?
    if (isSparse) {
      debugString.append("  Sparse.  Offsets are:\n");
      for (short sparseOffset : sparseOffsets) debugString.append(sparseOffset + " ");
      debugString.append("\n");
    } else {
      debugString.append("  Dense.  Coordinates are:\n");
      for (float coordinate : coordinates) debugString.append(coordinate + " ");
      debugString.append("\n");
    }
    return debugString.toString();
  }

  @Override
  public int getDimension() {
    return dimension;
  }

  public RealVector createZeroVector(int dimension) {
    return new RealVector(dimension);
  }

  @Override
  public boolean isZeroVector() {
    if (isSparse) {
      return sparseOffsets.length == 0;
    } else {
      for (float coordinate: coordinates) {
        if (coordinate != 0) {
          return false;
        }
      }
      return true;
    }
  }

  /**
   * Generates a basic sparse vector
   * with mainly zeros and some 1 and -1 entries (seedLength/2 of each)
   * each vector is an array of length seedLength containing 1+ the index of a non-zero
   * value, signed according to whether this is a + or -1.
   * <br>
   * e.g. +20 indicates a +1 in position 19, +1 would indicate a +1 in position 0.
   *      -20 indicates a -1 in position 19, -1 would indicate a -1 in position 0.
   * <br>
   * The extra offset of +1 is because position 0 would be unsigned,
   * and would therefore be wasted. Consequently we've chosen to make
   * the code slightly more complicated to make the implementation
   * slightly more space efficient.
   *
   * If seedlength == dimension, a dense real vector is generated instead, with each
   * dimension initialized to a real value between -1 and 1 
   *
   * @return Sparse representation of basic ternary vector.
   */
  public RealVector generateRandomVector(int dimension, int seedLength, Random random) {
    RealVector randomVector = new RealVector(dimension);

    //allow for dense random vectors, with each value initalized at random between -1 and 1
    if (seedLength == dimension)
      return generateDenseRandomVector(dimension, seedLength, random);

    boolean[] occupiedPositions = new boolean[dimension];
    randomVector.sparseOffsets = new short[seedLength];

    int testPlace, entryCount = 0;

    // Put in +1 entries.
    while (entryCount < seedLength / 2) {
      testPlace = random.nextInt(dimension);
      if (!occupiedPositions[testPlace]) {
        occupiedPositions[testPlace] = true;
        randomVector.sparseOffsets[entryCount] =
            new Integer(testPlace + 1).shortValue();
        entryCount++;
      }
    }

    // Put in -1 entries.
    while (entryCount < seedLength) {
      testPlace = random.nextInt (dimension);
      if (!occupiedPositions[testPlace]) {
        occupiedPositions[testPlace] = true;
        randomVector.sparseOffsets[entryCount] =
            new Integer((1 + testPlace) * -1).shortValue();
        entryCount++;
      }
    }

    return randomVector;
  }


  /**
   * Generates a basic dense vector
   * with values assigned at random to a real value between -1 and 1
   *
   * @return Dense representation of basic real vector.
   */

  public RealVector generateDenseRandomVector(int dimension, int seedLength, Random random) {

    RealVector randomVector = new RealVector(dimension);
    randomVector.sparseToDense();

    for (int q =0; q < dimension; q++)
      randomVector.coordinates[q] = (float) random.nextDouble();

    for (int q =0; q < dimension; q++)
      if (random.nextBoolean()) randomVector.coordinates[q] *=-1;

    randomVector.normalize();  
    return randomVector;
  }

  @Override
  /**
   * Measures overlap of two vectors using cosine similarity.
   * 
   * Causes this and other vector to be converted to dense representation.
   */
  public double measureOverlap(Vector other) {
    IncompatibleVectorsException.checkVectorsCompatible(this, other);
    if (isZeroVector()) return 0;
    RealVector realOther = (RealVector) other;
    if (realOther.isZeroVector()) return 0;
    if (isSparse) {
      sparseToDense();
    }
    if (realOther.isSparse) {
      realOther.sparseToDense();
    }
    double result = 0;
    double norm1 = 0;
    double norm2 = 0;
    for (int i = 0; i < dimension; ++i) {
      result += coordinates[i] * realOther.coordinates[i];
      norm1 += coordinates[i] * coordinates[i];
      norm2 += realOther.coordinates[i] * realOther.coordinates[i];
    }
    return result / Math.sqrt(norm1 * norm2);
  }

  @Override
  /**
   * Adds the other vector to this one.  This vector is cast to dense format; other vector is
   * left in sparse format if originally sparse.
   */
  public void superpose(Vector other, double weight, int[] permutation) {
    IncompatibleVectorsException.checkVectorsCompatible(this, other);
    RealVector realOther = (RealVector) other;

    if (isSparse) sparseToDense();
    if (realOther.isSparse) {
      for (int i = 0; i < realOther.sparseOffsets.length; ++i) {
        int entry = Integer.signum(realOther.sparseOffsets[i]);
        int positionToAdd = Math.abs(realOther.sparseOffsets[i]) - 1;
        if (permutation != null) {
          positionToAdd = permutation[positionToAdd];
        }
        coordinates[positionToAdd] += entry * weight;
      }
    } else {
      boolean anyNans = false;
      for (int i = 0; i < dimension; ++i) {
        if (Float.isNaN(realOther.coordinates[i])) {
          anyNans = true;
          break;
        }
      }
      if (anyNans) return;
      for (int i = 0; i < dimension; ++i) {
        int positionToAdd = i;
        if (permutation != null) {
          positionToAdd = permutation[positionToAdd];
        }
        coordinates[positionToAdd] += realOther.coordinates[i] * weight;
      }
    }
  }

  @Override
  /**
   * Implements binding depending on {@link #BIND_TYPE}
   */
  public void bind(Vector other) {
    IncompatibleVectorsException.checkVectorsCompatible(this, other);
    RealVector realOther = (RealVector) other;
    if (isSparse) sparseToDense();
    switch(BIND_METHOD) {
    case PERMUTATION:
      bindWithPermutation(realOther);
      return;
    case CONVOLUTION:
      bindWithConvolution(realOther);
      return;
    }
  }
  
  @Override
  /**
   * Implements release depending on {@link #BIND_TYPE}
   */
  public void release(Vector other) {
    IncompatibleVectorsException.checkVectorsCompatible(this, other);
    RealVector realOther = (RealVector) other;
    if (isSparse) sparseToDense();
    switch(BIND_METHOD) {
    case PERMUTATION:
      releaseWithPermutation(realOther);
      return;
    case CONVOLUTION:
      releaseWithConvolution(realOther);
      return;
    }
  }
    
  public void bindWithConvolution(RealVector realOther) {
    RealVector result = RealVectorUtils.fftConvolution(this, realOther);
    this.coordinates = result.coordinates;
  }

  /**
   * Implements release using {@link RealVectorUtils#fftApproxInvConvolution}
   */
  public void releaseWithConvolution(RealVector other) {
    RealVector result = RealVectorUtils.fftApproxInvConvolution(other, this);
    this.coordinates = result.coordinates;
  }

  /**
   * Implements binding as a single-shift permutation.  Currently wasteful; allocates
   * the permutation array each time.
   */
  public void bindWithPermutation(RealVector other) {
    RealVector result = createZeroVector(dimension);    
    result.superpose(
        other, 1, PermutationUtils.getShiftPermutation(VectorType.REAL, dimension, 1));
    result.superpose(
        this, 1, PermutationUtils.getShiftPermutation(VectorType.REAL, dimension, -1));
    this.coordinates = result.coordinates;
  }

  /**
   * Implements release using the {@link #bindWithPermutation}.
   */
  public void releaseWithPermutation(RealVector other) {
    RealVector result = createZeroVector(dimension);
    this.superpose(
        other, -1, PermutationUtils.getShiftPermutation(VectorType.REAL, dimension, 1));
    result.superpose(
        this, 1, PermutationUtils.getShiftPermutation(VectorType.REAL, dimension, 1));
    this.coordinates = result.coordinates;
  }

  @Override
  /**
   * Normalizes the vector, converting sparse to dense representations in the process.
   */
  public void normalize() {
    if (this.isSparse) {
      this.sparseToDense();
    }
    double normSq = 0;
    for (int i = 0; i < dimension; ++i) {
      normSq += coordinates[i] * coordinates[i];
    }
    float norm = (float) Math.sqrt(normSq);
    for (int i = 0; i < dimension; ++i) {
      coordinates[i] = coordinates[i] / norm;
    }
  }

  @Override
  /**
   * Writes vector out in dense format.  If vector is originally sparse, writes out a copy so
   * that vector remains sparse.
   */
  public void writeToLuceneStream(IndexOutput outputStream) {
    float[] coordsToWrite;
    if (isSparse) {
      RealVector copy = copy();
      copy.sparseToDense();
      coordsToWrite = copy.coordinates;
    } else {
      coordsToWrite = coordinates;
    }
    for (int i = 0; i < dimension; ++i) {
      try {
        outputStream.writeInt(Float.floatToIntBits(coordsToWrite[i]));
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  /**
   * Reads a (dense) version of a vector from a Lucene input stream. 
   */
  public void readFromLuceneStream(IndexInput inputStream) {
    if (isSparse) {
      coordinates = new float[dimension];
      sparseOffsets = null;
      isSparse = false;
    }
    for (int i = 0; i < dimension; ++i) {
      try {
        coordinates[i] = Float.intBitsToFloat(inputStream.readInt());
      } catch (IOException e) {
        logger.severe("Failed to parse vector from Lucene stream.  This signifies a "
            + "programming or runtime error, e.g., a dimension mismatch.");
        e.printStackTrace();
      }
    }
  }

  @Override
  /**
   * Writes vector to a string of the form x1|x2|x3| ... where the x's are the coordinates.
   * 
   * No terminating newline or | symbol.
   */
  public String writeToString() {
    StringBuilder builder = new StringBuilder();
    float[] denseCoordinates = this.getCoordinates();
    for (int i = 0; i < dimension; ++i) {
      builder.append(Float.toString(denseCoordinates[i]));
      if (i != dimension - 1) {
        builder.append("|");
      }
    }
    return builder.toString();
  }

  @Override
  /**
   * Writes vector from a string of the form x1|x2|x3| ... where the x's are the coordinates.
   */
  public void readFromString(String input) {
    String[] entries = input.split("\\|");
    if (entries.length != dimension) {
      throw new IllegalArgumentException("Found " + (entries.length) + " possible coordinates: "
          + "expected " + dimension);
    }
    if (isSparse) {
      coordinates = new float[dimension];
      sparseOffsets = null;
      isSparse = false;
    }
    for (int i = 0; i < dimension; ++i) {
      coordinates[i] = Float.parseFloat(entries[i]);
    }
  }

  /**
   * Automatically translate sparse format (listing of offsets) into full float vector.
   * 
   * The sparse vector is in condensed (signed index + 1) representation, and is converted to a
   * full float vector by adding -1 or +1 to the location (index - 1) according to the sign of the
   * index. (The -1 and +1 are necessary because there is no signed version of 0, so we'd have no
   * way of telling that that zeroth position in the array should be plus or minus 1.)
   */
  protected void sparseToDense() {
    if (!isSparse) {
      return;
    }
    coordinates = new float[dimension];
    for (int i = 0; i < dimension; ++i) {
      coordinates[i] = 0;
    }
    for (int i = 0; i < sparseOffsets.length; ++i) {
      coordinates[Math.abs(sparseOffsets[i]) - 1] = Math.signum(sparseOffsets[i]);
    }
    isSparse = false;
  }

  /**
   * Available to support access to coordinates for legacy operations.  Try not to use in new code!
   */
  public float[] getCoordinates() {
    if (isSparse) {
      RealVector copy = this.copy();
      copy.sparseToDense();
      return copy.coordinates;
    } else {
      return coordinates;
    }
  }

  /**
   *  Available for testing and copying.  Try not to use in new code!
   */
  public RealVector(float[] coordinates) {
    this.dimension = coordinates.length;
    this.coordinates = coordinates;
  }

  /**
   *  Available for testing and copying.  Try not to use in new code!
   */
  public RealVector(int dimension, short[] sparseOffsets) {
    this.isSparse = true;
    this.dimension = dimension;
    for (Short offset : sparseOffsets) {
      if ((offset == 0) || (offset > dimension) || (offset < -1 * dimension)) {
        throw new IllegalArgumentException("Offsets too large for dimension!");
      }
    }
    this.sparseOffsets = sparseOffsets;
  }
}
