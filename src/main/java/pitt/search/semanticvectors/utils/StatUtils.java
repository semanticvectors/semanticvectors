package pitt.search.semanticvectors.utils;

import java.util.List;

/**
 * Statistical utility functions.
 *
 * @author dwiddows
 */
public class StatUtils {

  /**
   * Returns the mean of all the numbers in the list.
   */
  public static double getMean(List<Double> numbers) {
    double sum = 0;
    for (double number : numbers) {
      sum += number;
    }
    return sum / numbers.size();
  }

  /**
   * Returns the variance of all the numbers in the list.
   */
  public static double getVariance(List<Double> numbers) {
    double mean = getMean(numbers);
    double sumSquareDiffs = 0;
    for (double number : numbers) {
      double diff = number - mean;
      sumSquareDiffs += diff * diff;
    }
    return sumSquareDiffs / numbers.size();
  }

  /**
   * Calculates sigmoid function
   */
  public static float sigmoid(float x) {
    float esubx = (float) Math.pow(Math.E, -x);
    return 1 / (1 + esubx);
  }

  /**
   * Gives a number representing how well a field that reaches an anchor number can also reach a target number.
   * @return 1 if target less than anchor; anchor / target if target is greater than anchor.
   */
  public static double rangeEnvelopeScore(double anchor, double target) {
    if (anchor < 0 || target < 0) {
      throw new IllegalArgumentException(
          String.format("Arguments must be positive numbers, not %f, %f", anchor, target));
    }

    if (target < anchor) {
      return 1;
    }

    return anchor / target;
  }

  /**
   * @return The ratio of the smaller to the bigger input (always 1 or less in magnitude).
   */
  public static double proportionScore(double anchor, double target) {
    if (anchor < 0 || target < 0) {
      throw new IllegalArgumentException(
          String.format("Arguments must be positive numbers, not %f, %f", anchor, target));
    }

    return (target < anchor) ? (target / anchor) : (anchor / target);
  }
}
