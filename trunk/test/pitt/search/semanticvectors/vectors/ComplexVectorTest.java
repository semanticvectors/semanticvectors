package pitt.search.semanticvectors.vectors;

import java.util.Random;

import org.junit.Test;

import junit.framework.TestCase;

public class ComplexVectorTest extends TestCase {
 @Test
 public void testCreateLookupTable() {
   float[] realLUT = ComplexVectorUtils.getRealLUT();
   float[] imLUT = ComplexVectorUtils.getImagLUT();

   char[] angles = { 0, 16300, 32800 };

   for (int i=0; i<angles.length; i++) {
     System.out.println( ""+(int)angles[i]+"  "+realLUT[angles[i]]+"  "+imLUT[angles[i]]);
   }
 }

 @Test
 public void testComplexVectorCreation() {
   ComplexVector vector = (ComplexVector) VectorFactory.createZeroVector(VectorType.COMPLEX, 10);
   assertTrue(vector.isZeroVector());
   assertEquals(10, vector.getDimensions());
   
   Random random = new Random(0);
   vector = (ComplexVector) VectorFactory.generateRandomVector(VectorType.COMPLEX, 10, 2, random);
   System.out.println(vector.toString());
   vector.toCartesian();
   System.out.println("Cartesian: " + vector.toString());
 }

 @Test
 public void testWriteToString() {
   int dim = 10;
   int seedLength = 3;
   ComplexVector complexInstance = new ComplexVector(0);
   ComplexVector cv = complexInstance.generateRandomVector(dim, seedLength, new Random());
   System.out.println(cv.writeToString());
 }

 @Test
 public void testDenseVectorCartesianConstructor() {
   float[] coords = { 12.3f, 3.2f, 2.6f, -1.3f, -0.01f, -1000.2f };
   ComplexVector cv = new ComplexVector(coords);
   System.out.println(cv.toString());
 }

 @Test
 public void testGenerateRandomVector() {
   int dim = 10;
   int seedLength = 3;
   ComplexVector complexInstance = new ComplexVector(0);
   ComplexVector cv = complexInstance.generateRandomVector(dim, seedLength, new Random());
   System.out.println(cv.toString());
 }

 @Test
 public void testGenerateDenseCartesianRandomVector() {
   int dim = 3;
   ComplexVector complexInstance = new ComplexVector(0);
   ComplexVector cv = complexInstance.generateDenseCartesianRandomVector(dim, new Random());
   System.out.println(cv.toString());
 }

 @Test
 public void testSuperposeSparseOnZero() {
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

 @Test
 public void testSuperposeZeroOnSparse() {
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

 @Test
 public void testVectorCopy() {
   float[] coords = { 12.3f, 3.2f, 2.6f, -1.3f, -0.01f, -1000.2f};
   ComplexVector cv1 = new ComplexVector(coords);
   ComplexVector cv2 = cv1.copy();
   System.out.println(cv2.toString());
 }

 @Test
 public void testNormalize() {
   float[] coords = { 12.3f, 3.2f, 2.6f, -1.3f, -0.01f, -1000.2f};
   ComplexVector cv = new ComplexVector(coords);
   System.out.println(cv.toString());
   cv.normalize();
   System.out.println(cv.toString());
 }

 @Test
 public void testMeasureOverlap() {
   double score;
   char[] angles1 = { 0, 16000, 32000 };
   char[] angles2 = { 0, 48000, 33000 };
   char[] angles3 = { 32000, 16000, 37000 };

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
}
