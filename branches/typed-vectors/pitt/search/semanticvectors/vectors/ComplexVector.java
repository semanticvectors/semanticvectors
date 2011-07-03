package pitt.search.semanticvectors.vectors;

import java.io.IOException;
import java.util.Random;
import java.util.logging.Logger;

import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;

import pitt.search.semanticvectors.ObjectVector;
import pitt.search.semanticvectors.vectors.CircRep.MODE;

/**
 * Complex number implementation of Vector.
 *
 * @author devinel
 */
public class ComplexVector extends Vector {
  public static final Logger logger = Logger.getLogger(ComplexVector.class.getCanonicalName());

  /**
   * We use the 'MODE' enumeration to keep track of which mode the complex vector
   * is in. By 'MODE' we mean whether the vector is using POLAR or CARTESIAN coordinates.
   * MODE.POLAR uses a 16 bit char for each element.
   * MODE.CARTESIAN uses two 32 bit floats for each element, one for the real coordinate
   * and one for the imaginary.
   */
  public static enum MODE { POLAR, CARTESIAN };

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
  private char[] phaseAngles;
  /**
   * Sparse representation using a 16 bit Java char for storing an offset and a phase angle
   * for each element. The offset is the index into the array and the phase angle is a random
   * value between 0 and 65535 representing angles between 0 and 2PI.
   * See also {@link generateRandomVector}.
   */
  private char[] sparseOffsets;
  private boolean isSparse;
  private MODE opMode;

  public float[] getCoordinates() {
    return coordinates;
  }

  public char[] getPhaseAngles()	{
    return phaseAngles;
  }


  protected ComplexVector(int dimension)
  {
	this.opMode = MODE.POLAR;
    this.dimension = dimension;
    this.sparseOffsets = new char[0];
    this.isSparse = true;
  }

  /**
   * Create an initial vector ready for training.
   */
  protected void initialize()
  {
    if (isZeroVector()) {

    }
    else {

    }
  }

  /**
   * Returns a new copy of this vector, in dense format.
   */
  public ComplexVector copy() {
    if (isSparse) {
    ComplexVector copy = new ComplexVector(dimension);
      copy.sparseOffsets = new char[sparseOffsets.length];
      for (int i = 0; i < sparseOffsets.length; ++i) {
        copy.sparseOffsets[i] = sparseOffsets[i];
      }
      return copy;
    } else {
      int arrayDimension = dimension*2;
      float[] coordinatesCopy = new float[arrayDimension];
      for (int i = 0; i < arrayDimension; ++i) {
        coordinatesCopy[i] = coordinates[i];
      }
      return new ComplexVector(coordinatesCopy);
    }
  }

  public String toString() {
    StringBuilder debugString = new StringBuilder("ComplexVector.");
    if (isSparse) {
      debugString.append("  Sparse.  Offsets are:\n");
      for (char sparseOffset : sparseOffsets) debugString.append(sparseOffset + " ");
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

  public ComplexVector createZeroVector(int dimension) {
    return new ComplexVector(dimension);
  }

  private boolean isZeroVector() {
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
   * @return Sparse representation of basic vector.
   */
  public ComplexVector generateRandomVector(int dimension, int seedLength, Random random) {
    ComplexVector randomVector = new ComplexVector(dimension);
    boolean[] occupiedPositions = new boolean[dimension];
    randomVector.sparseOffsets = new char[seedLength*2];

    // TODO ...


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
    ComplexVector realOther = (ComplexVector) other;
    if (realOther.isZeroVector()) return 0;
    if (isSparse) {
      sparseToDense();
    }
    if (realOther.isSparse) {
      realOther.sparseToDense();
    }

    // TODO ...

    return 0;
  }

  @Override
  /**
   * Adds the other vector to this one.  This vector is cast to dense format; other vector is
   * left in sparse format if originally sparse.
   */
  public void superpose(Vector other, double weight, int[] permutation) {
    IncompatibleVectorsException.checkVectorsCompatible(this, other);
    ComplexVector complexOther = (ComplexVector) other;

    // TODO ...

  }

  @Override
  /**
   * Normalizes the vector, converting sparse to dense representations in the process.
   */
  public void normalize() {
    if (this.isSparse) {
      this.sparseToDense();
    }

    // TODO ...
  }

  @Override
  /**
   * Writes vector out in dense format.  If vector is originally sparse, writes out a copy so
   * that vector remains sparse.
   */
  public void writeToLuceneStream(IndexOutput outputStream) {
    float[] coordsToWrite;
    if (isSparse) {
      ComplexVector copy = copy();
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
   * Assume that the vector is in dense polar form.
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
    for (int i = 0; i < dimension; ++i) {
      builder.append(Float.toString(coordinates[i]));
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
      logger.warning("Tryied to transform a sparse vector which is not in fact sparse."
          + "This may be a programming error.");
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

  // Available for testing and copying.
  protected ComplexVector(float[] coordinates) {
    this.dimension = coordinates.length/2;
    this.coordinates = coordinates;
  }
  // Available for testing and copying.
  protected ComplexVector(int dimension, char[] sparseOffsets) {
    this.isSparse = true;
    this.dimension = dimension;
    for (char offset : sparseOffsets) {
      if ((offset == 0) || (offset > dimension) || (offset < -1 * dimension)) {
        throw new IllegalArgumentException("Offsets too large for dimension!");
      }
    }
    this.sparseOffsets = sparseOffsets;
  }
}




