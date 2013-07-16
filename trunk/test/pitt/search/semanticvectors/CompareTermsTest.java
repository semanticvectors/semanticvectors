package pitt.search.semanticvectors;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.Test;

public class CompareTermsTest {
  private static final double TOL = 0.01;
  
  @Test
  public void testCompareTermsOrthographic() throws IOException {
    FlagConfig flagConfig = FlagConfig.getFlagConfig(
        new String[] {"-queryvectorfile", "orthographic", "foo", "foo"});
    assertEquals(1.0, CompareTerms.RunCompareTerms(flagConfig), TOL);
    
    flagConfig = FlagConfig.getFlagConfig(
        new String[] {"-queryvectorfile", "orthographic", "foo", "foot"});
    double outcome = CompareTerms.RunCompareTerms(flagConfig);
    assertTrue(0.85 < outcome);
    
    flagConfig = FlagConfig.getFlagConfig(
        new String[] {"-queryvectorfile", "orthographic", "foo", "bar"});
    outcome = CompareTerms.RunCompareTerms(flagConfig);
    assertTrue(0.1 > outcome);
  }
}
