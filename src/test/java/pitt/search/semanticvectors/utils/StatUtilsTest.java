package pitt.search.semanticvectors.utils;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Arrays;

/**
 * Tests for {@link StatUtils} class.
 */
public class StatUtilsTest {
  public static double TOL = 0.00001;

  @Test
  public void testGetMean() throws Exception {
    List<Double> numbers = Arrays.asList(new Double[] {0d, 1d, 2d, 3d, 4d});
    Assert.assertEquals(2d, StatUtils.getMean(numbers), TOL);
  }

  @Test
  public void testGetVariance() throws Exception {
    List<Double> numbers = Arrays.asList(new Double[] {0d, 1d, 2d, 3d, 4d});
    Assert.assertEquals(2d, StatUtils.getVariance(numbers), TOL);
  }

  @Test
  public void testSigmoid() {
    Assert.assertEquals(0.5, StatUtils.sigmoid(0), TOL);
    Assert.assertEquals(0, StatUtils.sigmoid(-10), 0.01);
    Assert.assertEquals(1, StatUtils.sigmoid(10), 0.01);
  }
}
