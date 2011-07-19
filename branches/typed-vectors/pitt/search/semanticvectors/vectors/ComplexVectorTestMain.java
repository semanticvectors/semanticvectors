package pitt.search.semanticvectors.vectors;

/**
 * TODO(widdows): Remove this once it's working in test.
 */

import java.util.Random;

import junit.framework.TestCase;

import org.junit.BeforeClass;
import org.junit.Test;

public class ComplexVectorTestMain extends TestCase {

  /**
    * Initialise whatever has to be initialized
   */
  @BeforeClass
  public void initialize() {
    ComplexVectorUtils.generateAngleToCartesianLUT();
  }

  /**
   * Test LUT
  */
  @Test
  public void test1() {
    float[] realLUT = ComplexVectorUtils.getRealLUT();
    float[] imLUT = ComplexVectorUtils.getImagLUT();

    char[] angles = { 0, 16300, 32800 };

    for (int i=0; i<angles.length; i++) {
      System.out.println( ""+(int)angles[i]+"  "+realLUT[angles[i]]+"  "+imLUT[angles[i]]);
    }
  }

  /**
   * Test writeToString
  */
  @Test
  public void test2() {
	int dim = 10;
    int seedLength = 3;
    ComplexVector complexInstance = new ComplexVector(0);
    ComplexVector cv = complexInstance.generateRandomVector(dim, seedLength, new Random());
    System.out.println(cv.writeToString());
  }

  /**
   * Test dense vector cartesian constructor
   */
  @Test
  public void test3() {
    float[] coords = { 12.3f, 3.2f, 2.6f, -1.3f, -0.01f, -1000.2f };
    ComplexVector cv = new ComplexVector(coords);
    System.out.println(cv.toString());
  }

  /**
   * Test generateRandomVector
   */
  @Test
  public void test4() {
    int dim = 10;
    int seedLength = 3;
    ComplexVector complexInstance = new ComplexVector(0);
	ComplexVector cv = complexInstance.generateRandomVector(dim, seedLength, new Random());
    System.out.println(cv.toString());
  }

  /**
   * Test generateDenseCartesianRandomVector
   */
  @Test
  public void test5() {
    int dim = 3;
    ComplexVector complexInstance = new ComplexVector(0);
    ComplexVector cv = complexInstance.generateDenseCartesianRandomVector(dim, new Random());
    System.out.println(cv.toString());
  }

  /**
   * Test superposition of zero vector with random sparse vector
   */
  @Test
  public void test6() {
    int dim = 5;
    int seedLength = 2;
    ComplexVector complexInstance = new ComplexVector(0);

    // Generate random vector
    ComplexVector cv1 = complexInstance.generateRandomVector(dim, seedLength, new Random());
    System.out.println(cv1.toString());

    // Create zero vector
    ComplexVector cv2 = complexInstance.createZeroVector(dim);

    // Superpose
    cv2.superpose(cv1, 1.0, null);

    System.out.println(cv2.toString());
  }

  /**
   * Test superposition of zero vector with random dense vector
   */
  @Test
  public void test7() {
    int dim = 5;
    ComplexVector complexInstance = new ComplexVector(0);
    System.out.println("....");

    // Generate random vector
    ComplexVector cv1 = complexInstance.generateDensePolarRandomVector(dim, new Random());
    System.out.println(cv1.toString());

    // Create zero vector
    ComplexVector cv2 = complexInstance.createZeroVector(dim);

    // Superpose
    cv2.superpose(cv1, 1.0, null);

    System.out.println(cv2.toString());
  }

  /**
   * Test vector copy
   */
  @Test
  public void test8() {
    float[] coords = { 12.3f, 3.2f, 2.6f, -1.3f, -0.01f, -1000.2f};
    ComplexVector complexInstance = new ComplexVector(0);
    ComplexVector cv1 = new ComplexVector(coords);
    ComplexVector cv2 = cv1.copy();
    System.out.println(cv2.toString());
  }

  /**
   * Test normalization
   */
  @Test
  public void test9() {
    float[] coords = { 12.3f, 3.2f, 2.6f, -1.3f, -0.01f, -1000.2f};
    ComplexVector complexInstance = new ComplexVector(0);
    ComplexVector cv = new ComplexVector(coords);
    System.out.println(cv.toString());
    cv.normalize();
    System.out.println(cv.toString());
  }

  /**
   * Test similarity function
   */
  @Test
  public void test10() {
    double score;
    char[] angles1 = { 0, 16000, 32000 };
    char[] angles2 = { 0, 48000, 33000 };
    char[] angles3 = { 32000, 16000, 37000 };

    ComplexVector complexInstance = new ComplexVector(0);
    ComplexVector cv1 = new ComplexVector(angles1);
    ComplexVector cv2 = new ComplexVector(angles2);
    ComplexVector cv3 = new ComplexVector(angles3);

    System.out.println("Vector 1: "+cv1.toString());
    System.out.println("Vector 2: "+cv2.toString());
    System.out.println("Vector 3: "+cv3.toString());

    score = cv1.measureOverlap(cv2);
    System.out.println("Score - v1 with v2 : "+score);

    score = cv1.measureOverlap(cv3);
    System.out.println("Score - v1 with v3 : "+score);

    score = cv2.measureOverlap(cv3);
    System.out.println("Score - v2 with v3 : "+score);
  }

  /**
   * @param args
   */
  public static void main(String[] args) {
    ComplexVectorTestMain cvt = new ComplexVectorTestMain();
    cvt.initialize();
    cvt.test1();
    cvt.test2();
    cvt.test3();
    cvt.test4();
    cvt.test5();
    cvt.test6();
    cvt.test7();
    cvt.test8();
    cvt.test9();
    cvt.test10();
  }

}


