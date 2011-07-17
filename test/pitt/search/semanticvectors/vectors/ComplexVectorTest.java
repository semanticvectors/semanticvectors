package pitt.search.semanticvectors.vectors;

import java.util.Random;

import org.junit.Test;

import junit.framework.TestCase;

public class ComplexVectorTest extends TestCase {

  @Test
  public void testComplexVectorCreation() {
    ComplexVector vector = (ComplexVector) VectorFactory.createZeroVector(VectorType.COMPLEX, 10);
    assertTrue(vector.isZeroVector());
    assertEquals(10, vector.getDimensions());
    
    Random random = new Random(0);
    vector = (ComplexVector) VectorFactory.generateRandomVector(VectorType.COMPLEX, 10, 2, random);
    System.out.println(vector.toString());
    ComplexVectorUtils.toCartesian(vector);
    System.out.println("Cartesian: " + vector.toString());
  }
  
}
