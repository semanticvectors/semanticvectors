package pitt.search.semanticvectors.orthography;

import static org.junit.Assert.*;

import org.junit.Test;

import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.VectorStoreRAM;

public class NumberRepresentationTest {
  /** Tolerance of errors for float and double comparisons. */
  public static final double TOL = 0.01;

  @Test
  public void testCreateAndGetNumberVectors() {
    FlagConfig flagConfig = FlagConfig.getFlagConfig(
        new String[] {"-vectortype", "real", "-dimension", "200"});
    NumberRepresentation numberRepresentation = new NumberRepresentation(flagConfig);
    
    VectorStoreRAM vsr2 = numberRepresentation.getNumberVectors(0, 2);
    assertEquals(5, vsr2.getNumVectors());
    
    VectorStoreRAM vsr4 = numberRepresentation.getNumberVectors(0, 4);
    assertEquals(7, vsr4.getNumVectors());
    
    // The beginning and end vectors should be the same in all cases.
    assertEquals(1.0, vsr2.getVector(0).measureOverlap(vsr4.getVector(0)), TOL);
    assertEquals(1.0, vsr2.getVector(2).measureOverlap(vsr4.getVector(4)), TOL);
  }
  
  @Test
  public void testVectorsNearerToBeginningOrEnd() {
    FlagConfig flagConfig = FlagConfig.getFlagConfig(
        new String[] {"-vectortype", "binary", "-dimension", "2048"});
    NumberRepresentation numberRepresentation = new NumberRepresentation(flagConfig);
    
    VectorStoreRAM vsr = numberRepresentation.getNumberVectors(0, 4);
    assertTrue(vsr.getVector(0).measureOverlap(vsr.getVector(1))
        > vsr.getVector(4).measureOverlap(vsr.getVector(1)));
    assertTrue(vsr.getVector(4).measureOverlap(vsr.getVector(3))
        > vsr.getVector(3).measureOverlap(vsr.getVector(0)));
    /** This "half-way" equality isn't exact, demonstrating that I don't exactly understand
     * the process. -DW. */
    assertEquals(vsr.getVector(4).measureOverlap(vsr.getVector(2)),
        vsr.getVector(2).measureOverlap(vsr.getVector(0)), TOL);
  }
  
  @Test
  public void testBinaryVectorsChangeGradually() {
    FlagConfig flagConfig = FlagConfig.getFlagConfig(
        new String[] {"-vectortype", "binary", "-dimension", "2048"});
    NumberRepresentation numberRepresentation = new NumberRepresentation(flagConfig);
    
    VectorStoreRAM vsr = numberRepresentation.getNumberVectors(0, 4);
    assertEquals(0.75, vsr.getVector(0).measureOverlap(vsr.getVector(1)), TOL);
  }
  
  @Test
  public void testAbsoluteValuesOfEndpointsDontMatter() {
    FlagConfig flagConfig = FlagConfig.getFlagConfig(
        new String[] {"-vectortype", "binary", "-dimension", "2048"});
    NumberRepresentation numberRepresentation = new NumberRepresentation(flagConfig);
    
    VectorStoreRAM vsr1 = numberRepresentation.getNumberVectors(0, 4);    
    VectorStoreRAM vsr2 = numberRepresentation.getNumberVectors(8, 12);
    assertEquals(1.0, vsr1.getVector(0).measureOverlap(vsr2.getVector(8)), TOL);
    assertEquals(1.0, vsr1.getVector(2).measureOverlap(vsr2.getVector(10)), TOL); 
  }
}
