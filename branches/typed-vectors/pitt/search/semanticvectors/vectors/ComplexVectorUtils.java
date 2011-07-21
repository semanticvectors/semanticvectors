package pitt.search.semanticvectors.vectors;

import java.util.logging.Logger;


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
   * Retrieve lookup tables if required.
   */
  public static float[] getRealLUT() { return CircleLookupTable.getRealLUT(); }
  public static float[] getImagLUT() { return CircleLookupTable.getImagLUT(); }

  /**
   * Superposes vec2 with vec1.
   * vec1 is in CARTESIAN mode.
   * vec2 is in POLAR mode.
   */
  public static void superposeWithAngle( ComplexVector vec1, ComplexVector vec2  ) {
    int dim =  vec1.getDimension();
    assert( dim == vec2.getDimension() );
    assert( vec1.getOpMode() == ComplexVector.MODE.CARTESIAN );
    assert( vec2.getOpMode() == ComplexVector.MODE.POLAR );

    char c[] = vec2.getPhaseAngles();
    float[] coordinates = vec1.getCoordinates();

    for (int i=0, j=0; i<dim; i++, j+=2) {
	  coordinates[j] += getRealLUT()[c[i]];
	  coordinates[j+1] += getImagLUT()[c[i]];
    }
  }

  /**
   * Superposes vec2 with vec1 with weight and permutation.
   * vec1 is in CARTESIAN mode.
   * vec2 is in POLAR mode.
   */
  public static void superposeWithAngle( ComplexVector vec1, ComplexVector vec2, float weight, int[] permutation ) {
	int positionToAdd;
    int dim =  vec1.getDimension();

    char c[] = vec2.getPhaseAngles();
    float[] coordinates = vec1.getCoordinates();

    if (permutation != null) {
      for (int i=0; i<dim; i++) {
        positionToAdd = permutation[i] << 1;
        // Real part
        coordinates[positionToAdd] += getRealLUT()[c[i]] * weight;
        // Imaginary Part
        coordinates[positionToAdd+1] += getImagLUT()[c[i]] * weight;
      }
    }
    else {
      for (int i=0; i<dim; i++) {
        positionToAdd = i << 1;
        // Real part
        coordinates[positionToAdd] += getRealLUT()[c[i]] * weight;
        // Imaginary Part
        coordinates[positionToAdd+1] += getImagLUT()[c[i]] * weight;
      }
    }
  }

  /**
   * Superposes vec2 with vec1 with weight and permutation.
   * vec1 is in CARTESIAN mode.
   * vec2 is in sparse POLAR mode.
   */
  public static void superposeWithSparseAngle( ComplexVector vec1, ComplexVector vec2, float weight, int[] permutation ) {
	int positionToAdd, phaseAngleIdx;

    char offsets[] = vec2.getSparseOffsets();
    float[] coordinates = vec1.getCoordinates();

    if (permutation != null) {
      for (int i=0; i<offsets.length; i+=2) {
        positionToAdd = permutation[offsets[i]] << 1;
        phaseAngleIdx = i+1;
        // Real part
        coordinates[positionToAdd] += getRealLUT()[offsets[phaseAngleIdx]] * weight;
        // Imaginary Part
        coordinates[positionToAdd+1] += getImagLUT()[offsets[phaseAngleIdx]] * weight;
      }
    }
    else {
      for (int i=0; i<offsets.length; i+=2) {
        positionToAdd = offsets[i] << 1;
        phaseAngleIdx = i+1;
        // Real part
        coordinates[positionToAdd] += getRealLUT()[offsets[phaseAngleIdx]] * weight;
        // Imaginary Part
        coordinates[positionToAdd+1] += getImagLUT()[offsets[phaseAngleIdx]] * weight;
      }
    }
  }

  /**
   * Superposes vec2 with vec1.
   * Both vectors are in CARTESIAN mode.
   */
  public static void superposeWithCoord( ComplexVector vec1, ComplexVector vec2  ) {
    int arrayDim =  vec1.getDimension()*2;
    assert( vec1.getDimension() == vec2.getDimension() );
    assert( vec1.getOpMode() == ComplexVector.MODE.CARTESIAN );
    assert( vec2.getOpMode() == ComplexVector.MODE.CARTESIAN );

    float[] coordinates1 = vec1.getCoordinates();
    float[] coordinates2 = vec2.getCoordinates();

    for (int i=0; i<arrayDim; i++) coordinates1[i] += coordinates2[i];
  }

  /**
   * Superposes vec2 with vec1 with weight and permutation.
   * Both vectors are in CARTESIAN mode.
   */
  public static void superposeWithCoord( ComplexVector vec1, ComplexVector vec2, float weight, int[] permutation ) {
    int positionToAdd;
    int dim =  vec1.getDimension()*2;
    assert( vec1.getDimension() == vec2.getDimension() );
    assert( vec1.getOpMode() == ComplexVector.MODE.CARTESIAN );
    assert( vec2.getOpMode() == ComplexVector.MODE.CARTESIAN );

    float[] coordinates1 = vec1.getCoordinates();
    float[] coordinates2 = vec2.getCoordinates();

    for (int i=0; i<dim; i++) {
      positionToAdd = permutation[i] << 1;
      // Real
      coordinates1[positionToAdd] += coordinates2[i] * weight;
      // Imaginary
      coordinates1[positionToAdd+1] += coordinates2[i+1] * weight;
    }
  }

  /**
   * Convert from cartesian coordinates to phase angle using trig
   * function.
   */
  public static char angleFromCartesianTrig( float real, float im )  {
    float theta = (float)Math.acos(real);
    char c, d = (char)(CircleLookupTable.phaseResolution - 1);
    c = (char)(theta * CircleLookupTable.pi2);
    //System.out.println(""+(int)c+"  "+(int)d);
    //System.out.println(""+real+"  "+im);
    //System.out.println(""+theta);
    if (im<0) c = (char)(d-c);

    return c;
  }

  public static void setFloatArrayToZero( float[] array ) {
	  for (int i=0; i<array.length; i++) array[i] = 0.0f;
  }

  public static void scaleFloatArray( float[] array, float weight) {
	  for (int i=0; i<array.length; i++) array[i] = array[i]*weight;
  }
}





