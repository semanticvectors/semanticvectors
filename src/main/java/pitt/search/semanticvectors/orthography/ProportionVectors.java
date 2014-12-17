package pitt.search.semanticvectors.orthography;

import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.utils.VerbatimLogger;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorUtils;

import java.util.Random;

/**
 * Gets vectors that represent some proportion between 0 and 1.
 */
public class ProportionVectors {

  /** Random seed used for starting demarcator vectors. */
  private static final long randomSeed = 0;

  /** Vector representing the proportion 0. */
  private Vector vectorStart;
  /** Vector representing the proportion 1. */
  private Vector vectorEnd;

  /** Constructs an instance from the given flag config. */
  public ProportionVectors(FlagConfig flagConfig) {
    Random random = new Random(randomSeed);
    while (true) {
      this.vectorStart = VectorFactory.generateRandomVector(
          flagConfig.vectortype(), flagConfig.dimension(), flagConfig.seedlength(), random);
      this.vectorEnd = VectorFactory.generateRandomVector(
          flagConfig.vectortype(), flagConfig.dimension(), flagConfig.seedlength(), random);
      if (this.vectorStart.measureOverlap(this.vectorEnd) < 0.1) break;
      VerbatimLogger.info("Bookend vectors too similar to each other ... repeating generation.\n");
    }
  }

  /**
   * Returns a vector representing the proportion of the distance between zero and one.
   * @param proportion Must be in the range [0, 1].
   * */
  public Vector getProportionVector(double proportion) {
    if (proportion < 0 || proportion > 1) {
      throw new IllegalArgumentException("Proportion must be a number in the range [0, 1]. Not: " + proportion);
    }
    Vector proportionVector = VectorUtils.weightedSuperposition(
        vectorStart, proportion, vectorEnd, 1 - proportion);
    return proportionVector;
  }
}
