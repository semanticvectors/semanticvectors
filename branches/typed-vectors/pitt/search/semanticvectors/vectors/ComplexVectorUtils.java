package pitt.search.semanticvectors.vectors;

import java.io.IOException;
import java.util.Random;
import java.util.logging.Logger;

import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;

import pitt.search.semanticvectors.ObjectVector;

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
  public final int phaseResolution = 65536;
  /**
   * Size of lookup table for mapping cartesian coordinates to phase angles.
   */
  public final int coordLUTSize = 10000;
  /**
   * Lookup Table for mapping cartesian coordinates to phase angle.
   */
  private static char[] coordLUT;
  float beta = coordLUTSize / 2;

  /**
   * Lookup Table for mapping phase angle to cartesian coordinates.
   */
  private static float[] realLUT;
  private static float[] imLUT;


}



