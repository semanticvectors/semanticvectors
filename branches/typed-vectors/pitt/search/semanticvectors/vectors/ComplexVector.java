package pitt.search.semanticvectors.vectors;

import java.io.IOException;
import java.util.Random;
import java.util.logging.Logger;

import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;

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
   * The actual number of float coordinates is 'dimensions' X 2 because of real and
   * imaginary components.
   */
  private final int dimensions;
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

  public char[] getPhaseAngles() {
    return phaseAngles;
  }

  public void setPhaseAngles(char[] phaseAngles) {
    this.phaseAngles = phaseAngles;
  }

  public char[] getSparseOffsets() {
    return sparseOffsets;
  }

  @Override
  public int getDimensions() {
    return dimensions;
  }

  public MODE getOpMode() {
    return opMode;
  }

  public void setOpMode(MODE opMode) {
    this.opMode = opMode;
  }

  protected ComplexVector(int dimensions)
  {
	this.opMode = MODE.POLAR;
    this.dimensions = dimensions;
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
      // If sparse then must be in Polar form
      ComplexVector copy = new ComplexVector(dimensions);
      copy.sparseOffsets = new char[sparseOffsets.length];
      for (int i = 0; i < sparseOffsets.length; ++i) {
        copy.sparseOffsets[i] = sparseOffsets[i];
      }
      return copy;
    } else {
      // Dense
      if (opMode == MODE.CARTESIAN) {
        // Cartesian Form
        int arraySize = dimensions * 2;
        float[] coordinatesCopy = new float[arraySize];
        for (int i = 0; i < arraySize; ++i) {
          coordinatesCopy[i] = coordinates[i];
        }
        return new ComplexVector(coordinatesCopy);
      }
      else {
        // Polar Form
        char[] phaseAnglesCopy = new char[dimensions];
        for (int i = 0; i < dimensions; ++i) {
        	phaseAnglesCopy[i] = phaseAngles[i];
        }
        return new ComplexVector(phaseAnglesCopy);
      }
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

  public ComplexVector createZeroVector(int dimension) {
    return new ComplexVector(dimension);
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
   * Generates a basic sparse vector in Polar form with the format
   * { offset, phaseAngle, offset, phaseAngle, ... }
   * Consequently the length of the offsets array is 2 X {@code numEntries}.
   *
   * @return Sparse representation of vector in Polar form.
   */
  public ComplexVector generateRandomVector(int dimensions, int numEntries, Random random) {
    ComplexVector randomVector = new ComplexVector(dimensions);
    boolean[] occupiedPositions = new boolean[dimensions];
    randomVector.sparseOffsets = new char[numEntries*2];
    randomVector.isSparse = true;

    int testPlace, entryCount = 0, offsetIdx;
    char randomPhaseAngle;

    while (entryCount < numEntries) {
      testPlace = random.nextInt(dimensions);
      randomPhaseAngle = (char)random.nextInt(ComplexVectorUtils.phaseResolution);
      if (!occupiedPositions[testPlace]) {
        offsetIdx = entryCount << 1;
        occupiedPositions[testPlace] = true;
        randomVector.sparseOffsets[offsetIdx] = (char)testPlace;
        randomVector.sparseOffsets[offsetIdx+1] = randomPhaseAngle;
        entryCount++;
      }
    }
    return randomVector;
  }

  /**
   * Generates a dense vector in Polar form.
   *
   * @return Dense representation of vector in Polar form.
   */
  public ComplexVector generateDensePolarRandomVector(int dimensions, Random random) {
    ComplexVector randomVector = new ComplexVector(dimensions);
    randomVector.makeDensePolarRandomVector(random);

    return randomVector;
  }

  /**
   * Generates a dense vector in Cartesian form with small magnitude elements to simulate
   * zero vector.
   *
   * @return Dense representation of vector in Cartesian form
   */
  public ComplexVector generateDenseCartesianRandomVector(int dimensions, Random random) {
    ComplexVector randomVector = new ComplexVector(dimensions);
    randomVector.makeDenseCartesianRandomVector(random);

    return randomVector;
  }

  /**
   * Makes this vector a dense random vector in Polar form.
   */
  public void makeDensePolarRandomVector(Random random) {
    phaseAngles = new char[dimensions];
    opMode = MODE.POLAR;
    coordinates = null;

    for(int i=0; i<dimensions;i++) {
      phaseAngles[i] = (char)random.nextInt(ComplexVectorUtils.phaseResolution);
    }
  }

  /**
   * Makes this vector a dense random vector in Cartesian form
   * with small magnitudes.
   */
  public void makeDenseCartesianRandomVector(Random random) {
    makeDensePolarRandomVector(random);
    ComplexVectorUtils.toCartesian(this);
    ComplexVectorUtils.scaleFloatArray(coordinates, 0.1f);
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

	assert( dimensions == other.getDimensions());

	float[] realLUT = ComplexVectorUtils.getRealLUT();
	char[] phaseAnglesOther = complexOther.getPhaseAngles();
	float sum = 0.0f;
	int dif;

	for (int i=0; i<dimensions; i++) {
		dif = Math.abs(phaseAngles[i] - phaseAnglesOther[i]);
		sum += realLUT[dif];
	}

	return (sum/dimensions);
  }

  @Override
  /**
   * Superposes other vector with this one.
   * We assume that this one is in cartesian form and that other vector is in
   * polar or sparse polar form.
   */
  public void superpose(Vector other, double weight, int[] permutation) {
    IncompatibleVectorsException.checkVectorsCompatible(this, other);
    ComplexVector complexOther = (ComplexVector) other;

    // Check if this is a zero vector
    if (isZeroVector()) {
      // This should probably be using a reference to a previously created random function.
      makeDenseCartesianRandomVector(new Random());
    }
    if (complexOther.isSparse) ComplexVectorUtils.superposeWithSparseAngle( this, complexOther, (float)weight, permutation );
    else ComplexVectorUtils.superposeWithAngle( this, complexOther, (float)weight, permutation );
  }

  @Override
  /**
   * Normalizes the vector. Assume we have a dense vector in cartesian form.
   */
  public void normalize() {
    float length, scale;
    int imIdx; // imaginary component index
    for (int i=0, j=0; i<dimensions; i++, j+=2) {
      imIdx = j+1;
      length = (float)Math.sqrt(coordinates[j]*coordinates[j] + coordinates[imIdx]*coordinates[imIdx]);
      scale = 1.0f/length;
       coordinates[j] = coordinates[j]*scale;
       coordinates[imIdx] = coordinates[imIdx]*scale;
    }
	// Convert to Polar form
    toPhaseAngle();
    opMode = MODE.POLAR;
  }

  public void toPhaseAngle() {
    // Create array for storing angles
    phaseAngles = new char[dimensions];
    if (!isZeroVector()) ComplexVectorUtils.toPhaseAngle( this );
    opMode = MODE.POLAR;
    // Free memory for storing cartesian form
    coordinates = null;
  }

  public void toCoordinate() {
    // Create arrays for storing coordinates
    coordinates = new float[dimensions*2];
    if (!isZeroVector()) ComplexVectorUtils.toCartesian( this );
    opMode = MODE.CARTESIAN;
    // Free memory for storing angles
    phaseAngles = null;
  }
  /**
   * Convolves this vector with the other. If the value of direction <=0
   * then the correlation operation is performed, ie. convolution inverse
   */
  public void convolve( ComplexVector other, int direction  )
  {
    assert( dimensions == other.getDimensions() );
    if (opMode!=MODE.POLAR) normalize();
    if (other.getOpMode()!=MODE.POLAR) other.normalize();
	char[] otherAngles = other.getPhaseAngles();

	if (direction>0) for (int i=0; i<dimensions; i++) phaseAngles[i] += otherAngles[i];
	else for (int i=0; i<dimensions; i++) phaseAngles[i] -= otherAngles[i];
  }
  /**
   * Transforms this vector into its complement.
   * Assumes vector is in Polar form.
   */
  public void complement() {
    assert( opMode == MODE.POLAR);
    char t = (char)(ComplexVectorUtils.phaseResolution/2);
    for (int i=0; i<dimensions; i++ ) phaseAngles[i] += t;
  }

  @Override
  /**
   * Writes vector out in dense format. Assumes that vector is dense
   * and in Polar form.
   */
  public void writeToLuceneStream(IndexOutput outputStream) {
    for (int i = 0; i < dimensions; ++i) {
      try {
        outputStream.writeInt(Float.floatToIntBits(phaseAngles[i]));
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  /**
   * Reads a dense vector in Polar form from a Lucene input stream.
   */
  public void readFromLuceneStream(IndexInput inputStream) {
    for (int i = 0; i < dimensions; ++i) {
      try {
        phaseAngles[i] = (char)Float.intBitsToFloat(inputStream.readInt());
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
   *
   * Writes cartesian vector as floats.
   * Writes polar vector as 16 bit integers.
   */
  public String writeToString() {
    StringBuilder builder = new StringBuilder();
    if (opMode == MODE.CARTESIAN) {
      for (int i = 0; i < coordinates.length; ++i) {
        builder.append(Float.toString(coordinates[i]));
        if (i != dimensions - 1) {
          builder.append("|");
        }
      }
    }
    else {
      for (int i = 0; i < phaseAngles.length; ++i) {
        builder.append((int)phaseAngles[i]);
        if (i != dimensions - 1) {
          builder.append("|");
        }
      }
    }
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
    String[] entries = input.split("\\|");
    if (opMode == MODE.CARTESIAN) {
      if (entries.length != dimensions*2) {
        throw new IllegalArgumentException("Found " + (entries.length) + " possible coordinates: "
          + "expected " + dimensions*2);
      }
      if (coordinates.length==0) coordinates = new float[dimensions];
      for (int i = 0; i < coordinates.length; ++i) {
        coordinates[i] = Float.parseFloat(entries[i]);
      }
    }
    else {
      // MODE = Polar
      if (entries.length != dimensions) {
        throw new IllegalArgumentException("Found " + (entries.length) + " possible coordinates: "
              + "expected " + dimensions);
      }
      if (phaseAngles.length==0) phaseAngles = new char[dimensions];
      for (int i = 0; i < phaseAngles.length; ++i) {
    	  phaseAngles[i] = (char)Integer.parseInt(entries[i]);
      }
    }
  }

  //Available for testing and copying.
  protected ComplexVector(float[] coordinates) {
    this.dimensions = coordinates.length/2;
    this.coordinates = coordinates;
    this.opMode = MODE.CARTESIAN;
  }
  //Available for testing and copying.
  protected ComplexVector(char[] phaseAngles) {
    this.dimensions = phaseAngles.length;
    this.phaseAngles = phaseAngles;
    this.opMode = MODE.POLAR;
  }
}




