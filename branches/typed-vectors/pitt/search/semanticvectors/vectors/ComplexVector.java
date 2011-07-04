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

  public void setCoordinates(float[] coordinates) {
    this.coordinates = coordinates;
  }

  public char[] getPhaseAngles()	{
    return phaseAngles;
  }

  @Override
  public int getDimension() {
    return dimension;
  }

  public MODE getOpMode() {
    return opMode;
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

	// TODO ...

    return null;
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

  @Override
  /**
   * Measures overlap of two vectors using mean cosine of difference
   * of phase angles.
   *
   * Only applied to dense representations.
   */
  public double measureOverlap(Vector other) {
    IncompatibleVectorsException.checkVectorsCompatible(this, other);
    ComplexVector complexOther = (ComplexVector)other;

    if (isZeroVector()) return 0;
    if (complexOther.isZeroVector()) return 0;
    if (isSparse) return 0;
    if (complexOther.isSparse) return 0;

	assert( dimension == other.getDimension());

	float[] realLUT = ComplexVectorUtils.getRealLUT();
	char[] phaseAnglesOther = complexOther.getPhaseAngles();
	float sum = 0.0f;
	int dif;

	for (int i=0; i<dimension; i++) {
		dif = Math.abs(phaseAngles[i] - phaseAnglesOther[i]);
		sum += realLUT[dif];
	}

	return (sum/dimension);
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

}




