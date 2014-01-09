package pitt.search.semanticvectors.tables;

import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.ObjectVector;
import pitt.search.semanticvectors.VectorStoreOrthographical;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;

/**
 * Represents a row in a {@link Table}.
 * 
 * @author dwiddows
 */
public class TableRow {

  public ObjectVector[] rowCellVectors;
    
  public ObjectVector rowVector;

  public TableRow(FlagConfig flagConfig, VectorStoreOrthographical orthographicGenerator,
      String[] stringValues, ObjectVector[] columnHeaders, TypeSpec[] columnTypes) {
    if (stringValues.length != columnHeaders.length) {
      throw new IllegalArgumentException("Arguments must have the same length.");
    }
    this.rowCellVectors = new ObjectVector[stringValues.length];
    
    Vector accumulator = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());
    for (int i = 0; i < stringValues.length; ++i) {
      if (stringValues[i].isEmpty()) continue;
      Vector valueVector = null;
      switch (columnTypes[i].getType()) {
        case STRING:
          valueVector = orthographicGenerator.getVector(stringValues[i]);
          break;
        case DOUBLE:
          valueVector = columnTypes[i].getDoubleValueVector(flagConfig, Double.parseDouble(stringValues[i]));
          break;
      }
      Vector boundColVal = columnHeaders[i].getVector().copy();
      boundColVal.bind(valueVector);
      boundColVal.normalize();
      this.rowCellVectors[i] = new ObjectVector(stringValues[i], boundColVal);
      accumulator.superpose(boundColVal, 1, null);
    }
    accumulator.normalize();
    this.rowVector = new ObjectVector(stringValues[0], accumulator);
  }
}
