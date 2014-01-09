package pitt.search.semanticvectors.tables;

import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.utils.Bobcat;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;

import java.util.Random;

/**
 * This class represents a "type" for a list of data items.
 * 
 * @author dwiddows
 */
public class TypeSpec {

  public enum SupportedType {
    STRING,
    DOUBLE,
    // Date would be nice, but predicting formats would be hard.
    // Integers will be treated as continuous by vector libraries, so they are not distinguished from doubles.
  }
  
  private SupportedType type;
  
  // These are only used for numeric types.
  private double minDoubleValue;
  private double maxDoubleValue;
  private Vector minBookendVector;
  private Vector maxBookendVector;
  
  /** Returns the type. */
  public SupportedType getType() {
    return type;
  }
  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Type: " + this.type);
    if (this.type == SupportedType.DOUBLE) {
      sb.append(" Min: " + minDoubleValue + " Max: " + maxDoubleValue);
    }
    return sb.toString();
  }
  
  /** Returns the minDoubleValue if type is {@link SupportedType#DOUBLE}. */
  public double getMinDoubleValue() {
    if (type != SupportedType.DOUBLE)
      throw new IllegalArgumentException("Must have type DOUBLE, not " + type);
    return minDoubleValue;
  }
  
  /** Returns the maxDoubleValue if type is {@link SupportedType#DOUBLE}. */
  public double getMaxDoubleValue() {
    if (type != SupportedType.DOUBLE)
      throw new IllegalArgumentException("Must have type DOUBLE, not " + type);
    return maxDoubleValue;
  }
  
  /** See {@link #getEmptyType()} */
  private TypeSpec() {
    this.type = SupportedType.DOUBLE;
    this.minDoubleValue = Double.MAX_VALUE;
    this.maxDoubleValue = Double.MIN_VALUE;
  }
  
  /**
   * Returns an 'empty' type, which is actually a double with very lax endpoints.
   * Every example will be used to constrain this.
   */
  public static TypeSpec getEmptyType() {
    return new TypeSpec();
  }
  
  /**
   * Adds a new example to this TypeSpec. If the new example won't parse as any other {@link SupportedType},
   * the internal type is set to {@link SupportedType#STRING}. If it parses as a compatible type, the
   * min and max values are modified accordingly.
   */
  public void addExample(String example) {
    if (example.isEmpty()) return;
    switch (this.type) {
    case STRING:
      return;
    case DOUBLE:
      try {
        Double newDouble = Double.parseDouble(example);
        if (newDouble < this.minDoubleValue)
          this.minDoubleValue = newDouble;
        if (newDouble > this.maxDoubleValue)
          this.maxDoubleValue = newDouble;
      } catch (NumberFormatException e) {
        this.type = SupportedType.STRING;
        return;
      }
    }
  }

  /**
   * For a field known to be numeric, generates appropriate bookend vectors.
   */
  public void addMinMaxVectors(FlagConfig flagConfig, String columnName) {
    if (this.getType() != SupportedType.DOUBLE) {
      throw new IllegalArgumentException("Min and max vectors only supported for type DOUBLE so far.");
    }
    long randomSeed = Bobcat.asLong(columnName);
    Random random = new Random(randomSeed);
    minBookendVector = VectorFactory.generateRandomVector(
        flagConfig.vectortype(), flagConfig.dimension(), flagConfig.seedlength(), random);
    maxBookendVector = VectorFactory.generateRandomVector(
        flagConfig.vectortype(), flagConfig.dimension(), flagConfig.seedlength(), random);
  }

  /**
   * Returns a vector appropriate for this value by interpolating endpoints.
   * Only for type {@link SupportedType#DOUBLE}.
   */
  public Vector getDoubleValueVector(FlagConfig flagConfig, double value) {
    if (this.getType() != SupportedType.DOUBLE) {
      throw new IllegalArgumentException("Bad call to getDoubleValue.");
    }
    if (value < minDoubleValue || value > maxDoubleValue) {
      throw new IllegalArgumentException("Value out of bounds: " + value);
    }
    Vector result = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());
    double doubleRange = maxDoubleValue - minDoubleValue;
    result.superpose(minBookendVector, (value - minDoubleValue) / doubleRange, null);
    result.superpose(maxBookendVector, (maxDoubleValue - doubleRange) / doubleRange, null);
    return result;
  }
}
