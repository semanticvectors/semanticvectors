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
    assertEquals(0.925, CompareTerms.RunCompareTerms(flagConfig), TOL);
  }
}
