package pitt.search.semanticvectors.vectors;

import static org.junit.Assert.*;

import java.util.Random;

import org.junit.Test;

public class RealVectorUtilsTest {
  static double TOL = 0.0001;
  static double APPROX_TOL = 0.1;

  @Test
  public void testFftConvolution2d() {
    RealVector vector1 = new RealVector(new float[] {1, 0});
    RealVector vector2 = new RealVector(new float[] {1, 0});
    
    RealVector convolution = RealVectorUtils.fftConvolution(vector1, vector2);
    assertTrue(convolution.toString().contains("1.0 0.0"));
  }
  
  @Test
  public void testFftConvolution3d() {
    RealVector vector1 = new RealVector(new float[] {1, 0, 1});
    RealVector vector2 = new RealVector(new float[] {1, 1, 0});
    
    RealVector convolution = RealVectorUtils.fftConvolution(vector1, vector2);
    System.out.println(convolution.toString());
    assertTrue(convolution.toString().contains("2.0 1.0 1.0"));
  }
  
  @Test
  public void testConvolutionCommutes() {
    Random random = new Random(0);
    RealVector vector1 = (RealVector) VectorFactory.generateRandomVector(
        VectorType.REAL, 200, 20, random);
    RealVector vector2 = (RealVector) VectorFactory.generateRandomVector(
        VectorType.REAL, 200, 20, random);
    RealVector conv12 = RealVectorUtils.fftConvolution(vector1, vector2);
    RealVector conv21 = RealVectorUtils.fftConvolution(vector2, vector1);
    assertEquals(1, conv12.measureOverlap(conv21), TOL);
  }
  
  @Test
  public void testInvolution() {
    RealVector vector = new RealVector(new float[] {0, 1, 2, 3});
    RealVector involution = RealVectorUtils.getInvolution(vector);
    float[] invCoords = involution.getCoordinates();
    assertEquals(0, invCoords[0], TOL);
    assertEquals(1, invCoords[3], TOL);
    assertEquals(2, invCoords[2], TOL);
    assertEquals(3, invCoords[1], TOL);
    
    vector = new RealVector(new float[] {0, 1, 2, 3, 4});
    involution = RealVectorUtils.getInvolution(vector);
    invCoords = involution.getCoordinates();
    assertEquals(0, invCoords[0], TOL);
    assertEquals(1, invCoords[4], TOL);
    assertEquals(2, invCoords[3], TOL);
    assertEquals(3, invCoords[2], TOL);
    assertEquals(4, invCoords[1], TOL);
  }
    
  @Test
  public void testInverseConvolutionHighDimensions() {
    Random random = new Random(0);
    RealVector vector1 = (RealVector) VectorFactory.generateRandomVector(
        VectorType.REAL, 200, 50, random);
    //vector1.normalize();
    RealVector vector2 = (RealVector) VectorFactory.generateRandomVector(
        VectorType.REAL, 200, 50, random);
    //vector2.normalize();
    assertEquals(0, vector1.measureOverlap(vector2), APPROX_TOL);
    RealVector convolution = RealVectorUtils.fftConvolution(vector1, vector2);
    assertEquals(0, vector1.measureOverlap(convolution), APPROX_TOL);
    RealVector inverseConvolution = RealVectorUtils.fftApproxInvConvolution(vector2, convolution);
    assertEquals(1, vector1.measureOverlap(inverseConvolution), 0.25);
    System.out.println(vector1.measureOverlap(inverseConvolution));
  }
}
