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
}
