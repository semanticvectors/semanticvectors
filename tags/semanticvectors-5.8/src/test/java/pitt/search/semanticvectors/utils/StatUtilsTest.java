package pitt.search.semanticvectors.utils;

import org.junit.Assert;
import junit.framework.TestCase;

import java.util.List;
import java.util.Arrays;

/**
 * Tests for {@link StatUtils} class.
 */
public class StatUtilsTest extends TestCase {
  public static double TOL = 0.00001;

  public void testGetMean() throws Exception {
    List<Double> numbers = Arrays.asList(new Double[] {0d, 1d, 2d, 3d, 4d});
    Assert.assertEquals(2d, StatUtils.getMean(numbers), TOL);
  }

  public void testGetVariance() throws Exception {
    List<Double> numbers = Arrays.asList(new Double[] {0d, 1d, 2d, 3d, 4d});
    Assert.assertEquals(2d, StatUtils.getVariance(numbers), TOL);
  }
}
