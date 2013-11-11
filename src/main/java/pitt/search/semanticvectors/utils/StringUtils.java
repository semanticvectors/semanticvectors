package pitt.search.semanticvectors.utils;

import java.util.Arrays;

/**
 * Basic utility functions for string processing. There's nothing special here
 * but it avoids introducing extra dependencies for simple stuff.
 * 
 * @author dwiddows
 */
public class StringUtils {

  /**
   * Returns a comma-separated join of the strings in the input.
   */
  public static String join(Iterable<String> input) {
    StringBuilder builder = new StringBuilder();
    for (String item: input) {
      builder.append(item + ", ");
    }
    return builder.toString(); 
  }
  
  /**
   * Returns a comma-separated join of the strings in the input.
   */
  public static String join(String[] input) {
    return join(Arrays.asList(input));
  }
}
