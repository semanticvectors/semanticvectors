package pitt.search.semanticvectors.vectors;

import java.io.IOException;
import java.util.Random;
import java.util.logging.Logger;

import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;

import pitt.search.semanticvectors.ObjectVector;
import pitt.search.semanticvectors.vectors.CircRepUtils.CALCTYPE;

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
   * Resolution at which we discretise the phase angle. This is fixed at 2^16 since we are
   * using 16 bit chars to represent phase angles.
   */
  public static final int phaseResolution = 65536;
  public static final float pi = 3.1415926535f;
  public static final float pi2 = pi * phaseResolution / 2;
  public static final float pi3 = pi / phaseResolution / 2;

  /**
   * Lookup Table for mapping phase angle to cartesian coordinates.
   */
  private static float[] realLUT;
  private static float[] imLUT;

  private Random ran;

  /**
   * Retrieve lookup tables if required.
   */
  public static float[] getRealLUT() { return realLUT; }
  public static float[] getImagLUT() { return imLUT; }


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
	  coordinates[j] += realLUT[c[i]];
	  coordinates[j+1] += imLUT[c[i]];
    }
  }

  /**
   * Superposes vec2 with vec1 with weight and permutation.
   * vec1 is in CARTESIAN mode.
   * vec2 is in POLAR mode.
   */
  public static void superposeWithAngle( ComplexVector vec1, ComplexVector vec2, float weight, int[] permutation ) {
	if (permutation == null) return;
	int positionToAdd;
    int dim =  vec1.getDimension();
    assert( dim == vec2.getDimension() );
    assert( vec1.getOpMode() == ComplexVector.MODE.CARTESIAN );
    assert( vec2.getOpMode() == ComplexVector.MODE.POLAR );

    char c[] = vec2.getPhaseAngles();
    float[] coordinates = vec1.getCoordinates();

    for (int i=0; i<dim; i++) {
      positionToAdd = permutation[i] << 1;
      // Real part
      coordinates[positionToAdd] += realLUT[c[i]] * weight;
      // Imaginary Part
      coordinates[positionToAdd+1] += imLUT[c[i]] * weight;
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
   * Convert from phase angles to cartesian coordinates using LUT.
   */
  public static void toCartesian( ComplexVector vec ) {
    int dim =  vec.getDimension();
    char c[] = vec.getPhaseAngles();
    float[] coordinates = new float[dim*2];

    for (int i=0, j=0; i<dim; i++, j+=2) {
      coordinates[j] = realLUT[c[i]];
      coordinates[j+1] = imLUT[c[i]];
    }

    vec.setCoordinates(coordinates);
  }
  /**
   * Convert from cartesian coordinates to phase angles.
   * We assume that the vector is already in POLAR mode
   */
  public static void toPhaseAngle( ComplexVector vec ) {
    int dim = vec.getDimension();
    char[] c = vec.getPhaseAngles();
    float[] coordinates = vec.getCoordinates();

	for (int i=0, j=0; i<dim; i++, j+=2) {
	  c[i] = angleFromCartesianTrig( coordinates[j], coordinates[j+1] );;
    }
  }
  /**
   * Convert from cartesian coordinates to phase angle using trig
   * function.
   */
  public static char angleFromCartesianTrig( float real, float im )  {
    float theta = (float)Math.acos(real);
    char c, d = (char)phaseResolution;
    c = (char)(theta/pi2);
    if (im<0) c = (char)(d-c);

    return c;
  }

  public static void setFloatArrayToZero( float[] array ) {
	  for (int i=0; i<array.length; i++) array[i] = 0.0f;
  }

  /**
   * Generate the phase angle to cartesian LUT.
   */
  public static void generateAngleToCartesianLUT() {
    realLUT = new float[phaseResolution];
    imLUT = new float[phaseResolution];

    float theta;

    for (int i=0; i<phaseResolution; i++) {
	  theta = ((float)i) * pi3;
	  realLUT[i] = (float)Math.cos(theta);
	  imLUT[i] = (float)Math.sin(theta);
    }
  }
}





