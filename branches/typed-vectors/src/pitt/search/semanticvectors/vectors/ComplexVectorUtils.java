package pitt.search.semanticvectors.vectors;

import java.util.logging.Logger;

import pitt.search.semanticvectors.vectors.ComplexVector.Mode;


/**
 * Complex number utilities class.
 *
 * Contains static methods for various operation on complex vectors.
 *
 * @author devinel
 */
public class ComplexVectorUtils {
  public static final Logger logger = Logger.getLogger(RealVector.class.getCanonicalName());

  /**
   * Superposes vec2 with vec1.
   * vec1 is in CARTESIAN mode.
   * vec2 is in POLAR mode.
   */
  public static void superposeWithAngle( ComplexVector vec1, ComplexVector vec2  ) {
    int dim =  vec1.getDimension();
    assert(dim == vec2.getDimension());
    assert(vec1.getOpMode() == ComplexVector.Mode.CARTESIAN);
    assert(vec2.getOpMode() == ComplexVector.Mode.POLAR_DENSE);

    short c[] = vec2.getPhaseAngles();
    float[] coordinates = vec1.getCoordinates();

    for (int i=0, j=0; i<dim; i++, j+=2) {
	  coordinates[j] += CircleLookupTable.getRealEntry(c[i]);
	  coordinates[j+1] += CircleLookupTable.getImagEntry(c[i]);
    }
  }

  /**
   * Superposes vec2 with vec1 with weight and permutation.
   * vec1 is in CARTESIAN mode.
   * vec2 is in POLAR mode.
   */
  public static void superposeWithAngle(
      ComplexVector vec1, ComplexVector vec2, float weight, int[] permutation) {
	int positionToAdd;
    int dim =  vec1.getDimension();

    short c[] = vec2.getPhaseAngles();
    float[] coordinates = vec1.getCoordinates();

    if (permutation != null) {
      for (int i=0; i<dim; i++) {
        positionToAdd = permutation[i] << 1;
        // Real part
        coordinates[positionToAdd] += CircleLookupTable.getRealEntry(c[i]) * weight;
        // Imaginary Part
        coordinates[positionToAdd+1] += CircleLookupTable.getImagEntry(c[i]) * weight;
      }
    }
    else {
      for (int i=0; i<dim; i++) {
        positionToAdd = i << 1;
        // Real part
        coordinates[positionToAdd] += CircleLookupTable.getRealEntry(c[i]) * weight;
        // Imaginary Part
        coordinates[positionToAdd+1] += CircleLookupTable.getImagEntry(c[i]) * weight;
      }
    }
  }

  /**
   * Superposes vec2 with vec1 with weight and permutation.
   * vec1 is in CARTESIAN mode.
   * vec2 is in sparse POLAR mode.
   */
  public static void superposeWithSparseAngle(
      ComplexVector vec1, ComplexVector vec2, float weight, int[] permutation) {
    assert(vec1.getOpMode() == Mode.CARTESIAN);
    assert(vec1.getOpMode() == Mode.POLAR_SPARSE);
    short offsets[] = vec2.getSparseOffsets();
    float[] coordinates = vec1.getCoordinates();

    for (int i = 0; i < offsets.length; i += 2) {
      int positionToAdd = offsets[i] << 1;
      if (permutation != null) positionToAdd = permutation[offsets[i]] << 1;
      int phaseAngleIdx = i+1;
      coordinates[positionToAdd] += CircleLookupTable.getRealEntry(offsets[phaseAngleIdx]) * weight;
      coordinates[positionToAdd+1] += CircleLookupTable.getImagEntry(offsets[phaseAngleIdx]) * weight;
    }
  }


  /**
   * Superposes vec2 with vec1.
   * Both vectors are in CARTESIAN mode.
   */
  public static void superposeWithCoord(ComplexVector vec1, ComplexVector vec2) {
    int arrayDim =  vec1.getDimension()*2;
    IncompatibleVectorsException.checkVectorsCompatible(vec1, vec2);
    assert(vec1.getOpMode() == ComplexVector.Mode.CARTESIAN);
    assert(vec2.getOpMode() == ComplexVector.Mode.CARTESIAN);

    float[] coordinates1 = vec1.getCoordinates();
    float[] coordinates2 = vec2.getCoordinates();

    for (int i=0; i<arrayDim; i++) coordinates1[i] += coordinates2[i];
  }

  /**
   * Superposes vec2 with vec1 with weight and permutation.
   * Both vectors are in CARTESIAN mode.
   */
  public static void superposeWithCoord(
      ComplexVector vec1, ComplexVector vec2, float weight, int[] permutation) {
    IncompatibleVectorsException.checkVectorsCompatible(vec1, vec2);
    assert(vec1.getOpMode() == ComplexVector.Mode.CARTESIAN);
    assert(vec2.getOpMode() == ComplexVector.Mode.CARTESIAN);
    
    int positionToAdd;

    float[] coordinates1 = vec1.getCoordinates();
    float[] coordinates2 = vec2.getCoordinates();

    for (int i = 0; i < vec1.getDimension(); i++) {
      if (permutation == null) positionToAdd = i;
      else positionToAdd = permutation[i];
      // Real
      coordinates1[2*positionToAdd] += coordinates2[2*i] * weight;
      // Imaginary
      coordinates1[2*positionToAdd + 1] += coordinates2[2*i + 1] * weight;
    }
  }

  public static void setFloatArrayToZero(float[] array) {
	  for (int i=0; i<array.length; i++) array[i] = 0.0f;
  }

  public static void scaleFloatArray(float[] array, float weight) {
	  for (int i=0; i<array.length; i++) array[i] = array[i]*weight;
  }
}





