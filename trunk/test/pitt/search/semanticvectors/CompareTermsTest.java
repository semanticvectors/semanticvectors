package pitt.search.semanticvectors;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.Test;

public class CompareTermsTest {
  private static final double TOL = 0.01;
  
  @Test
  public void testCompareTermsOrthographic() throws IOException {
    FlagConfig flagConfig = FlagConfig.getFlagConfig(
        new String[] {"-elementalmethod", "orthographic", "foo", "foo"});
    assertEquals(1.0, CompareTerms.runCompareTerms(flagConfig), TOL);
    
    flagConfig = FlagConfig.getFlagConfig(
        new String[] {"-elementalmethod", "orthographic", "foo", "foot"});
    double outcome = CompareTerms.runCompareTerms(flagConfig);
    assertTrue(0.85 < outcome);
    
    flagConfig = FlagConfig.getFlagConfig(
        new String[] {"-elementalmethod", "orthographic", "foo", "bar"});
    outcome = CompareTerms.runCompareTerms(flagConfig);
    assertTrue(0.1 > outcome);
    
    flagConfig = FlagConfig.getFlagConfig(
        new String[] {"-elementalmethod", "orthographic", "-vectortype", "real", "bad", "dab"});
    outcome = CompareTerms.runCompareTerms(flagConfig);
    assertEquals(0.71, outcome, TOL);
    
    // Permutation encoding fails to distinguish anagrams.
    flagConfig = FlagConfig.getFlagConfig(
        new String[] {"-elementalmethod", "orthographic", "-vectortype", "real",
            "-realbindmethod", "permutation", "bad", "dab"});
    outcome = CompareTerms.runCompareTerms(flagConfig);
    assertEquals(1, outcome, TOL);
  }
  
  @Test
  public void testCompareTermsComplexOrthographic() throws IOException {
    FlagConfig flagConfig = FlagConfig.getFlagConfig(
        new String[] {"-elementalmethod", "orthographic", "-vectortype", "complex", "-seedlength", "100", "foo", "bar"});
    double outcome = CompareTerms.runCompareTerms(flagConfig);
    assertTrue(0.3 > outcome);
    
    flagConfig = FlagConfig.getFlagConfig(
        new String[] {"-elementalmethod", "orthographic", "-vectortype", "complex", "-seedlength", "100", "foo", "oof"});
    outcome = CompareTerms.runCompareTerms(flagConfig);
    assertTrue(0.9 < outcome);
    
    flagConfig = FlagConfig.getFlagConfig(
        new String[] {"-elementalmethod", "orthographic", "-vectortype", "complex", "-seedlength", "100", "foo", "foo"});
    outcome = CompareTerms.runCompareTerms(flagConfig);
    assertEquals(1, outcome, TOL);
  }
  
  @Test
  public void testCompareTermsComplexFlatOrthographic() throws IOException {    
    FlagConfig flagConfig = FlagConfig.getFlagConfig(
        new String[] {"-elementalmethod", "orthographic", "-vectortype", "complexflat", "-seedlength", "100", "foo", "bar"});
    double outcome = CompareTerms.runCompareTerms(flagConfig);
    assertTrue(0.3 > outcome);
    
    flagConfig = FlagConfig.getFlagConfig(
        new String[] {"-elementalmethod", "orthographic", "-vectortype", "complexflat", "-seedlength", "100", "foo", "oof"});
    outcome = CompareTerms.runCompareTerms(flagConfig);
    assertTrue(0.9 < outcome);
    
    flagConfig = FlagConfig.getFlagConfig(
        new String[] {"-elementalmethod", "orthographic", "-vectortype", "complexflat", "-seedlength", "100", "foo", "foo"});
    outcome = CompareTerms.runCompareTerms(flagConfig);
    assertEquals(1, outcome, TOL);
  }
}
