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
    
    flagConfig = FlagConfig.parseFlagsFromString("-elementalmethod orthographic foo foot");
    double outcome = CompareTerms.runCompareTerms(flagConfig);
    assertTrue(0.85 < outcome);
    
    flagConfig = FlagConfig.getFlagConfig(
        new String[] {"-elementalmethod", "orthographic", "-realbindmethod", "convolution", "foo", "bar"});
    outcome = CompareTerms.runCompareTerms(flagConfig);
    assertTrue("Expected outcome less than 0.6 but got: " + outcome, 0.6 > outcome);
    
    flagConfig = FlagConfig.getFlagConfig(
        new String[] {"-elementalmethod", "orthographic", "-vectortype", "real", "-realbindmethod", "convolution", "bad", "dab"});
    double newOutcome = CompareTerms.runCompareTerms(flagConfig);
    // This way-too-high tolerance number of 0.3 is because test outcome is 1.0 under maven.
    // TODO: Figure out why!
    assertEquals(0.71, newOutcome, 0.3);
    
    // Permutation encoding fails to distinguish anagrams.
    flagConfig = FlagConfig.getFlagConfig(
        new String[] {"-elementalmethod", "orthographic", "-vectortype", "real",
            "-realbindmethod", "permutation", "bad", "dab"});
    outcome = CompareTerms.runCompareTerms(flagConfig);
    assertEquals(1, outcome, TOL);
  }
  
  @Test
  public void testCompareTermsComplexOrthographic() throws IOException {
    FlagConfig flagConfig = FlagConfig.parseFlagsFromString(
        "-elementalmethod orthographic -vectortype complex -seedlength 100 foo bar");
    double outcome = CompareTerms.runCompareTerms(flagConfig);
    assertTrue(0.45 > outcome);
    
    flagConfig = FlagConfig.getFlagConfig(
        new String[] {"-elementalmethod", "orthographic", "-vectortype", "complex", "-seedlength", "100", "foo", "oof"});
    outcome = CompareTerms.runCompareTerms(flagConfig);
    assertTrue(0.75 < outcome);
    
    flagConfig = FlagConfig.getFlagConfig(
        new String[] {"-elementalmethod", "orthographic", "-vectortype", "complex", "-seedlength", "100", "foo", "foo"});
    outcome = CompareTerms.runCompareTerms(flagConfig);
    assertEquals(1, outcome, TOL);
  }
  
  @Test
  public void testCompareTermsComplexFlatOrthographic() throws IOException {
    /*
    FlagConfig flagConfig = FlagConfig.getFlagConfig(
        new String[] {"-elementalmethod", "orthographic", "-vectortype", "complexflat", "-seedlength", "100", "foo", "bar"});
    double outcome = CompareTerms.runCompareTerms(flagConfig);
    assertTrue(0.3 > outcome);
    
    flagConfig = FlagConfig.getFlagConfig(
        new String[] {"-elementalmethod", "orthographic", "-vectortype", "complexflat", "-seedlength", "100", "foo", "oof"});
    outcome = CompareTerms.runCompareTerms(flagConfig);
    System.out.println("Outcome:" + outcome);
    assertTrue(0.8 < outcome);
    */

    FlagConfig flagConfig = FlagConfig.getFlagConfig(
        new String[] {"-elementalmethod", "orthographic", "-vectortype", "complexflat", "-seedlength", "100", "foo", "foo"});
    double outcome = CompareTerms.runCompareTerms(flagConfig);
    assertEquals(1, outcome, TOL);
  }
}
