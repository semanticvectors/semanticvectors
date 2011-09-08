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

/**
 * Complex number implementation of Vector.
 * 
 * Vectors come in three representations, sparse polar, dense polar, and Cartesian.
 * 
 * Superposition and similarity measurement are different for Cartesian and dense polar vectors.
 * The preferred operators and measures is currently chosen by setting the {@link #DOMINANT_MODE}
 * at compile time.
 *
 * @author devinel
 */
public class ComplexVector extends Vector {
  public static final Logger logger = Logger.getLogger(ComplexVector.class.getCanonicalName());
  
  /**
   * We use the 'MODE' enumeration to keep track of which mode the complex vector is in. By 'MODE'
   * we mean whether the vector is using POLAR_SPARSE, POLAR_DENSE or CARTESIAN coordinates.
   * 
   * CARTESIAN uses two 32 bit floats for each element, one for the real coordinate
   * and one for the imaginary.
   */
  public static enum Mode { 
    /** Uses a nonnegative 16 bit short for each phase angle.  The value -1 is reserved for
     * representing the complex number zero, i.e., there is no entry in this dimension. */
    POLAR_DENSE,
    /** Uses a pair of 16 bit shorts for each (offset, phase angle) pair. */
    POLAR_SPARSE,
    /** Uses a pair of 32 bit floats for each (real, imaginary) complex coordinate. */
    CARTESIAN };

  /** 
   * The dominant mode used for normalizing and comparing vectors.
   * 
   * TODO(widdows): Figure out how clients should be able to set this properly.
   */
  private static Mode DOMINANT_MODE = Mode.CARTESIAN;
  public static void setDominantMode(Mode mode) {
    if (mode == Mode.POLAR_SPARSE) {
      throw new IllegalArgumentException("POLAR_SPARSE cannot be used as dominant mode.");
    }
    DOMINANT_MODE = mode;
  }
  public static Mode getDominantMode() {
    return DOMINANT_MODE;
  }
    
  /**
   * The actual number of float coordinates is 'dimension' X 2 because of real and
   * imaginary components.
   */
  private final int dimension;
  /**
   * Dense Cartesian representation.  Coordinates can be anything expressed by floats.
   */
  private float[] coordinates;
  /**
   * Dense Polar representation.  Coordinates can be anything expressed by 16 bit chars.
   * The complex elements are assumed to all lie on the unit circle, ie. all amplitudes
   * equal 1.
   */
  private short[] phaseAngles;
  /**
   * Sparse representation using a 16 bit Java char for storing an offset (in position 2i)
   * and a corresponding phase angle (in position 2i + 1) for each element.
   * The offset is the index into the array and the phase angle is a random
   * value between 0 and 65535 representing angles between 0 and 2PI.
   * See also {@link generateRandomVector}.
   */
  private short[] sparseOffsets;
  private Mode opMode;

  protected ComplexVector(int dimension, Mode opMode) {
    this.opMode = opMode;
    this.dimension = dimension;
    switch(opMode) {
    case POLAR_SPARSE:
      return;
    case POLAR_DENSE:
      this.phaseAngles = new short[dimension];
      for (int i = 0; i < dimension; ++i) phaseAngles[i] = -1;  // Initialize to complex zero vector.
    case CARTESIAN:
      this.coordinates = new float[2*dimension];
    }
  }

  /**
   * Returns a new copy of this vector, in dense format.
   */
  public ComplexVector copy() {
    ComplexVector copy = new ComplexVector(dimension, opMode);
    switch (opMode) {
    case POLAR_SPARSE :
      copy.sparseOffsets = new short[sparseOffsets.length];
      for (int i = 0; i < sparseOffsets.length; ++i) {
        copy.sparseOffsets[i] = sparseOffsets[i];
      }
      copy.opMode = Mode.POLAR_SPARSE;
      break;
    case POLAR_DENSE :
      for (int i = 0; i < dimension; ++i) {
          copy.phaseAngles[i] = phaseAngles[i];
      }
      break;
    case CARTESIAN :
      for (int i = 0; i < 2*dimension; ++i) {
        copy.coordinates[i] = coordinates[i];
      }
      break;
    }
    return copy;
  }

  public String toString() {
    StringBuilder debugString = new StringBuilder("ComplexVector.");
    switch(opMode) {
    case POLAR_SPARSE :
      debugString.append("  Sparse polar.  Offsets are:\n");
      for (short sparseOffset : sparseOffsets) debugString.append((int)sparseOffset + " ");
      debugString.append("\n");
      break;
    case POLAR_DENSE :
      debugString.append("  Dense polar. Coordinates are:\n");
      for (int coordinate : phaseAngles) debugString.append(coordinate + " ");
      debugString.append("\n");
      break;
    case CARTESIAN :
      debugString.append("  Cartesian. Coordinates are:\n");
      for (float coordinate : coordinates) debugString.append(coordinate + " ");
      debugString.append("\n");
      break;
    }
    return debugString.toString();
  }

  @Override
  public boolean isZeroVector() {
    switch(opMode) {
    case POLAR_SPARSE :
      return sparseOffsets == null || sparseOffsets.length == 0;
    case POLAR_DENSE :
      return phaseAngles == null;
    case CARTESIAN :
      if (coordinates == null) return true;
      for (float coordinate: coordinates) {
        if (coordinate != 0) return false;  // If this is ever buggy look for rounding errors.
      }
      return true;
    }
    throw new IllegalArgumentException("Unrecognized mode: " + opMode);
  }

  /**
   * Generates a basic sparse vector in Polar form with the format
   * { offset, phaseAngle, offset, phaseAngle, ... }
   * Consequently the length of the offsets array is 2 X {@code numEntries}.
   *
   * @return Sparse representation of vector in Polar form.
   */
  public ComplexVector generateRandomVector(int dimension, int numEntries, Random random) {
    ComplexVector randomVector = new ComplexVector(dimension, Mode.POLAR_SPARSE);
    boolean[] occupiedPositions = new boolean[dimension];
    randomVector.sparseOffsets = new short[numEntries*2];

    int testPlace, entryCount = 0, offsetIdx;
    short randomPhaseAngle;

    while (entryCount < numEntries) {
      testPlace = random.nextInt(dimension);
      randomPhaseAngle = (short) random.nextInt(CircleLookupTable.PHASE_RESOLUTION);
      if (!occupiedPositions[testPlace]) {
        offsetIdx = entryCount << 1;
        occupiedPositions[testPlace] = true;
        randomVector.sparseOffsets[offsetIdx] = (short)testPlace;
        randomVector.sparseOffsets[offsetIdx + 1] = randomPhaseAngle;
        entryCount++;
      }
    }
    return randomVector;
  }
  
  @Override
  /**
   * Implementation of measureOverlap that switches depending on {@code DOMINANT_MODE}.
   * 
   * Transforms both vectors into {@code DOMINANT_MODE}.
   */
  public double measureOverlap(Vector other) {
    IncompatibleVectorsException.checkVectorsCompatible(this, other);
    if (isZeroVector()) return 0;
    ComplexVector complexOther = (ComplexVector) other;
    if (complexOther.isZeroVector()) return 0;
    switch (DOMINANT_MODE) {
      case CARTESIAN:
        return measureCartesianOverlap(complexOther);
      case POLAR_DENSE:
        return measurePolarDenseOverlap(complexOther);
      case POLAR_SPARSE:
        throw new IllegalArgumentException("POLAR_DENSE is not allowed as DOMINANT_MODE.");
      default:
        return 0;
    }
  }
  
  /**
   * Measure overlap using the scalar product of cartesian form.
   */
  protected double measureCartesianOverlap(ComplexVector other) {
    toCartesian();
    other.toCartesian();
    double result = 0;
    double norm1 = 0;
    double norm2 = 0;
    for (int i = 0; i < dimension*2; ++i) {
      result += coordinates[i] * other.coordinates[i];
      norm1 += coordinates[i] * coordinates[i];
      norm2 += other.coordinates[i] * other.coordinates[i];
    }
    return result / Math.sqrt(norm1 * norm2);
  }
  
  /**
   * Measures overlap of two vectors using mean cosine of difference
   * of phase angles.
   *
   * Transforms this and other vector to POLAR_DENSE representations.
   */
  protected double measurePolarDenseOverlap(ComplexVector other) {
    toDensePolar();
    other.toDensePolar();
	short[] phaseAnglesOther = other.getPhaseAngles();
	float sum = 0.0f;
	for (short i=0; i < dimension; i++) {
	  if (phaseAngles[i] == CircleLookupTable.ZERO_INDEX
	      || phaseAnglesOther[i] == CircleLookupTable.ZERO_INDEX) continue;
	  sum += CircleLookupTable.getRealEntry((short) Math.abs(phaseAngles[i] - phaseAnglesOther[i]));
	}
	return sum / dimension;
  }

  @Override
  /**
   * Normalizes vector based on {@code DOMINANT_MODE}.
   */
  public void normalize() {
    if (isZeroVector()) return;
    switch (DOMINANT_MODE) {
      case CARTESIAN:
        normalizeCartesian();
        return;
      case POLAR_DENSE:
        toDensePolar();
        return;
      case POLAR_SPARSE:
        throw new IllegalArgumentException("POLAR_SPARSE is not allowed as DOMINANT_MODE.");
      default:
        return;
    } 
  }
  
  /**
   * Normalizes the cartesian form of the vector so that it has unit length.  Same as the
   * real vector implementation (except that dimension is doubled).
   */
  protected void normalizeCartesian() {
    toCartesian();
    double normSq = 0;
    for (int i = 0; i < dimension*2; ++i) {
      normSq += coordinates[i] * coordinates[i];
    }
    float norm = (float) Math.sqrt(normSq);
    for (int i = 0; i < dimension*2; ++i) {
      coordinates[i] = coordinates[i] / norm;
    }
  }
  
  @Override
  /**
   * Superposes other vector with this one, putting this vector into cartesian mode.
   */
  public void superpose(Vector other, double weight, int[] permutation) {
    IncompatibleVectorsException.checkVectorsCompatible(this, other);    
    IncompatibleVectorsException.checkVectorsCompatible(this, other);
    ComplexVector complexOther = (ComplexVector) other;
    if (opMode != Mode.CARTESIAN) { toCartesian(); }

    switch (complexOther.opMode) {
    case CARTESIAN :
      ComplexVectorUtils.superposeWithCoord(this, complexOther, (float)weight, permutation);
      break;
    case POLAR_SPARSE :
      ComplexVectorUtils.superposeWithSparseAngle(this, complexOther, (float)weight, permutation);
      break;
    case POLAR_DENSE :
      ComplexVectorUtils.superposeWithAngle(this, complexOther, (float)weight, permutation);
    }
  }

  /**
   * Transform from any mode to cartesian coordinates.
   */
  public void toCartesian() {
    switch (opMode) {
    case CARTESIAN :
      return;  // Nothing to do.
    case POLAR_SPARSE :
      sparsePolarToCartesian();
      return;
    case POLAR_DENSE :
      densePolarToCartesian();
    }
  }
  
  private void sparsePolarToCartesian() {
    assert(opMode == Mode.POLAR_SPARSE);
    sparsePolarToDensePolar();
    densePolarToCartesian();
  }
  
  private void densePolarToCartesian() {
    assert(opMode == Mode.POLAR_DENSE);
    coordinates = new float[dimension*2];
    for (int i = 0; i < dimension; i++) {
      coordinates[2*i] = CircleLookupTable.getRealEntry(phaseAngles[i]);
      coordinates[2*i + 1] = CircleLookupTable.getImagEntry(phaseAngles[i]);
    }
    opMode = Mode.CARTESIAN;
    phaseAngles = null;
  }
  
  /**
   * Transform from any mode to cartesian coordinates.
   */
  public void toDensePolar() {
    switch (opMode) {
    case POLAR_DENSE :
      return;  // Nothing to do.
    case POLAR_SPARSE :
      sparsePolarToDensePolar();
      return;
    case CARTESIAN :
      cartesianToDensePolar();
    }
  }
  
  private void cartesianToDensePolar() {
    assert(opMode == Mode.CARTESIAN);
    opMode = Mode.POLAR_DENSE;
    phaseAngles = new short[dimension];
    for (int i = 0; i < dimension; i++) {
      phaseAngles[i] = CircleLookupTable.phaseAngleFromCartesianTrig(
          coordinates[2*i], coordinates[2*i + 1]);
    }
    coordinates = null;  // Reclaim memory.
  }

  private void sparsePolarToDensePolar() {
    assert(opMode == Mode.POLAR_SPARSE);
    phaseAngles = new short[dimension];
    // Initialize to complex zero vector.
    for (int i = 0; i < dimension; ++i) phaseAngles[i] = CircleLookupTable.ZERO_INDEX;
    if (sparseOffsets == null) return;
    for (int i = 0; i < sparseOffsets.length; i += 2) {
      int positionToAdd = sparseOffsets[i];
      int phaseAngleIdx = i + 1;
      phaseAngles[positionToAdd] = sparseOffsets[phaseAngleIdx];
    }
    opMode = Mode.POLAR_DENSE;
    sparseOffsets = null;  // Reclaim memory.
  }
  
  /**
   * Convolves this vector with the other. If the value of direction <= 0
   * then the correlation operation is performed, ie. convolution inverse
   */
  public void convolve(ComplexVector other, int direction) {
    IncompatibleVectorsException.checkVectorsCompatible(this, other);
    if (opMode != Mode.POLAR_DENSE) normalize();
    if (other.getOpMode() != Mode.POLAR_DENSE) other.normalize();
	short[] otherAngles = other.getPhaseAngles();

	for (int i=0; i < dimension; i++) {
	  if ((phaseAngles[i] == CircleLookupTable.ZERO_INDEX)
	      || (otherAngles[i] == CircleLookupTable.ZERO_INDEX)) {
	    phaseAngles[i] = CircleLookupTable.ZERO_INDEX;
	    continue;
	  }
	  short angleToAdd = otherAngles[i];
	  if (direction <= 0) {
	    angleToAdd = (short) (CircleLookupTable.PHASE_RESOLUTION - angleToAdd);
	  }
	  phaseAngles[i] = (short) ((phaseAngles[i] + angleToAdd) % CircleLookupTable.PHASE_RESOLUTION);
	}
  }

  /**
   * Transforms this vector into its complement.
   * Assumes vector is in dense polar form.
   */
  public void complement() {
    assert(opMode == Mode.POLAR_DENSE);
    char t = (char)(CircleLookupTable.PHASE_RESOLUTION/2);
    for (int i=0; i < dimension; i++) phaseAngles[i] += t;
  }

  @Override
  /**
   * Transforms vector to cartesian form and writes vector out in dense format.
   */
  public void writeToLuceneStream(IndexOutput outputStream) {
    toCartesian();
    for (int i = 0; i < dimension*2; ++i) {
      try {
        outputStream.writeInt(Float.floatToIntBits(coordinates[i]));
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    
    /* DORMANT CODE!
    assert(opMode != MODE.POLAR_SPARSE);
    if (opMode == MODE.CARTESIAN) {
      cartesianToDensePolar();
    }
    for (int i = 0; i < dimension; ++i) {
      try {
        outputStream.writeInt((int)(phaseAngles[i]));
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    */
  }

  @Override
  /**
   * Reads a vector in Cartesian form from a Lucene input stream.
   */
  public void readFromLuceneStream(IndexInput inputStream) {
    opMode = Mode.CARTESIAN;
    coordinates = new float[dimension*2];
    for (int i = 0; i < dimension*2; ++i) {
      try {
        coordinates[i] = Float.intBitsToFloat(inputStream.readInt());
      } catch (IOException e) {
        logger.severe("Failed to parse vector from Lucene stream.  This signifies a "
            + "programming or runtime error, e.g., a dimension mismatch.");
        e.printStackTrace();
      }
    }
    
    /* DORMANT CODE!
    phaseAngles = new short[dimension];
    coordinates = null;
    for (int i = 0; i < dimension; ++i) {
      try {
        phaseAngles[i] = (short) inputStream.readInt();
      } catch (IOException e) {
        logger.severe("Failed to parse vector from Lucene stream.  This signifies a "
            + "programming or runtime error, e.g., a dimension mismatch.");
        e.printStackTrace();
      }
    }
    */
  }

  @Override
  /**
   * Writes vector as cartesian form to a string of the form x1|x2|x3| ... where the x's are the coordinates.
   *
   * No terminating newline or | symbol.
   *
   * Writes cartesian vector as floats.
   * Writes polar vector as 16 bit integers.
   */
  public String writeToString() {
    // TODO(widdows): Discuss whether cartesian should be the main serialization representation.
    // The toCartesian call renders the switching below redundant, so we should pick one.
    toCartesian();
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < coordinates.length; ++i) {
      builder.append(Float.toString(coordinates[i]));
      if (i != coordinates.length - 1) {
        builder.append("|");
      }
    }
    
    /* DORMANT CODE!
    switch(opMode) {
    case CARTESIAN :
      for (int i = 0; i < coordinates.length; ++i) {
        builder.append(Float.toString(coordinates[i]));
        if (i != coordinates.length - 1) {
          builder.append("|");
        }
      }
      break;
    case POLAR_SPARSE:
      for (int i = 0; i < sparseOffsets.length; ++i) {
        builder.append((int) sparseOffsets[i]);
        if (i != sparseOffsets.length - 1) {
          builder.append("|");
        }
      }
      break;
    case POLAR_DENSE:
      for (int i = 0; i < phaseAngles.length; ++i) {
        builder.append((int) phaseAngles[i]);
        if (i != phaseAngles.length - 1) {
          builder.append("|");
        }
      }
    }
    */
    return builder.toString();
  }

  @Override
  /**
   * Reads vector from a string of the form x1|x2|x3| ... where the x's are the coordinates.
   * No terminating newline or | symbol.
   *
   * Reads cartesian vector as floats.
   * Reads polar vector as 16 bit integers.
   */
  public void readFromString(String input) {
    toCartesian();  // Big assumption, renders some code below dormant.
    String[] entries = input.split("\\|");
    
    switch (opMode) {
    case CARTESIAN :
      if (entries.length != dimension*2) {
        throw new IllegalArgumentException("Found " + (entries.length) + " possible coordinates: "
          + "expected " + dimension*2);
      }
      if (coordinates.length==0) coordinates = new float[dimension];
      for (int i = 0; i < coordinates.length; ++i) {
        coordinates[i] = Float.parseFloat(entries[i]);
      }
      break;
    case POLAR_DENSE :
      if (entries.length != dimension) {
        throw new IllegalArgumentException("Found " + (entries.length) + " possible coordinates: "
              + "expected " + dimension);
      }
      if (phaseAngles == null || phaseAngles.length==0) phaseAngles = new short[dimension];
      for (int i = 0; i < phaseAngles.length; ++i) {
    	phaseAngles[i] = (short)Integer.parseInt(entries[i]);
      }
      break;
    case POLAR_SPARSE :
      logger.info("Reading sparse complex vector from string is not supported.");
      break;
    }
  }

  //Available for testing and copying.
  protected ComplexVector(float[] coordinates) {
    this.dimension = coordinates.length/2;
    this.coordinates = coordinates;
    this.opMode = Mode.CARTESIAN;
  }
  //Available for testing and copying.
  protected ComplexVector(short[] phaseAngles) {
    this.dimension = phaseAngles.length;
    this.phaseAngles = phaseAngles;
    this.opMode = Mode.POLAR_DENSE;
  }
  
  protected float[] getCoordinates() {
    return coordinates;
  }

  protected void setCoordinates(float[] coordinates) {
    this.coordinates = coordinates;
  }

  public short[] getPhaseAngles() {
    return phaseAngles;
  }

  protected void setPhaseAngles(short[] phaseAngles) {
    this.phaseAngles = phaseAngles;
  }
  
  protected short[] getSparseOffsets() {
    return sparseOffsets;
  }

  protected void setSparseOffsets(short[] sparseOffsets) {
    this.sparseOffsets = sparseOffsets;
  }

  @Override
  public int getDimension() {
    return dimension;
  }

  protected Mode getOpMode() {
    return opMode;
  }

  protected void setOpMode(Mode opMode) {
    this.opMode = opMode;
  }
}




