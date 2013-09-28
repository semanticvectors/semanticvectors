package pitt.search.semanticvectors.vectors;

import java.util.ArrayList;
import java.util.logging.Logger;

import pitt.search.semanticvectors.vectors.ComplexVector.Mode;


/**
 * Complex number utilities class.
 *
 * Contains static methods for various operation on complex vectors.
 *
 * @author Lance De Vine
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
  public static boolean orthogonalizeVectors(ArrayList<Vector> vectors) {    
    int dimension = vectors.get(0).getDimension();
    // Go up through vectors in turn, parameterized by k.
    for (int k = 0; k < vectors.size(); ++k) {
      Vector kthVector = vectors.get(k);
      kthVector.normalize();
      if (kthVector.getDimension() != dimension) {
        logger.warning("In orthogonalizeVector: not all vectors have required dimension.");
        return false;
      }
      // Go up to vector k, parameterized by j.
      for (int j = 0; j < k; ++j) {
        Vector jthVector = vectors.get(j);
        renderOrthogonal( (ComplexVector) kthVector, (ComplexVector) jthVector);
        // And renormalize each time.
        kthVector.normalize();
      }
    }
    return true;
  }


  /**
   * Renders each circular component of vec1 orthogonal to the corresponding component of vec2
   * Both vectors are in put into CARTESIAN mode.
   */
  public static void renderOrthogonal(
      ComplexVector vec1, ComplexVector vec2) {
    IncompatibleVectorsException.checkVectorsCompatible(vec1, vec2);
    if (vec1.getOpMode() != ComplexVector.Mode.CARTESIAN) vec1.toCartesian();
    if (vec2.getOpMode() != ComplexVector.Mode.CARTESIAN) vec2.toCartesian();

    float[] coordinates1 = vec1.getCoordinates();
    float[] coordinates2 = vec2.getCoordinates();

    for (int i = 0; i < vec1.getDimension()*2; i+=2) {  
      double resultThisPair = coordinates1[i] * coordinates2[i];
      resultThisPair += coordinates1[i+1] * coordinates2[i+1];

      double norm1 = coordinates1[i] * coordinates1[i];
      norm1 += coordinates1[i+1] * coordinates1[i+1];

      double norm2 = coordinates2[i] * coordinates2[i];
      norm2  += coordinates2[i+1] * coordinates2[i+1];

      norm1 = Math.sqrt(norm1);
      norm2 = Math.sqrt(norm2);

      double cosine = 0;

      if (norm1 > 0 && norm2 > 0)
        cosine = resultThisPair / (norm1 * norm2);   

      coordinates1[i] = (float) (coordinates1[i] - cosine*coordinates2[i]);
      coordinates1[i+1] = (float) (coordinates1[i+1] - cosine*coordinates2[i+1]);
    }
  }

  public static void setFloatArrayToZero(float[] array) {
    for (int i=0; i<array.length; i++) array[i] = 0.0f;
  }

  public static void scaleFloatArray(float[] array, float weight) {
    for (int i=0; i<array.length; i++) array[i] = array[i]*weight;
  }
}





