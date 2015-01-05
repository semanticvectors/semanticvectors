package pitt.search.semanticvectors.vectors;

import org.junit.Assert;
import org.junit.Test;
import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.VectorStoreOrthographical;

/**
 * Created by dwiddows on 7/4/14.
 */
public class VectorStoreOrthographicalTest {

  private static double TOL = 0.01;

  @Test
  public void initAndRetrieveTest() {
    FlagConfig flagConfig = FlagConfig.getFlagConfig(null);
    VectorStoreOrthographical store = new VectorStoreOrthographical(flagConfig);
    Vector fooVector = store.getVector("foo");
    Vector fooVector2 = store.getVector("foo");
    Assert.assertEquals(1, fooVector.measureOverlap(fooVector2), TOL);

    Vector footVector = store.getVector("foot");
    Assert.assertTrue(1 > fooVector.measureOverlap(footVector));

    Vector barVector = store.getVector("bar");
    Assert.assertTrue(fooVector.measureOverlap(barVector) < fooVector.measureOverlap(footVector));

  }
}
