package pitt.search.semanticvectors.tables;

import java.util.ArrayList;
import java.util.Random;

import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.ObjectVector;
import pitt.search.semanticvectors.VectorStore;
import pitt.search.semanticvectors.VectorStoreOrthographical;
import pitt.search.semanticvectors.VectorStoreRAM;
import pitt.search.semanticvectors.utils.Bobcat;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;

/**
 * Represents a table of data. This includes:
 * <ul>
 * <li> Column labels </li>
 * <li> Column types </li>
 * <li> Homogeneous rows </li>
 * </ul>
 * 
 * @author dwiddows
 */
public class Table {
  private FlagConfig flagConfig;
  private ObjectVector[] columnHeaders;
  private TypeSpec[] columnTypes;
  private ArrayList<TableRow> rows;
  private VectorStoreRAM rowSummaryVectors;
  private VectorStoreOrthographical orthographicVectorStore;

  public Table(FlagConfig flagConfig, String[] columnNames, ArrayList<String[]> dataRows) {
    this.flagConfig = flagConfig;
    this.orthographicVectorStore = new VectorStoreOrthographical(flagConfig);
    this.columnHeaders = new ObjectVector[columnNames.length];
    this.columnTypes = new TypeSpec[columnNames.length];
    for (int i = 0; i < columnNames.length; ++i) {
      Vector columnNameVector = VectorFactory.generateRandomVector(
          flagConfig.vectortype(), flagConfig.dimension(), flagConfig.seedlength,
          new Random(Bobcat.asLong(columnNames[i])));
      this.columnHeaders[i] = new ObjectVector(columnNames[i], columnNameVector);
    }
    this.prepareTypeSchema(dataRows);
    
    for (TypeSpec type : columnTypes) {
      System.out.println(type);
    }
    
    this.rowSummaryVectors = new VectorStoreRAM(flagConfig);
    this.rows = new ArrayList<>();
    for (String[] dataRow: dataRows) {
      this.addRow(dataRow);
    }
  }
  
  public VectorStore getRowVectorStore() {
    return this.rowSummaryVectors;
  }
  
  private void addRow(String[] rowValues) {
    TableRow newRow = new TableRow(
        flagConfig, orthographicVectorStore, rowValues, columnHeaders);
    rows.add(newRow);
    rowSummaryVectors.putVector(newRow.rowVector.getObject(), newRow.rowVector.getVector());
  }

  private void prepareTypeSchema(ArrayList<String[]> dataRows) {
    for (int i = 0; i < columnTypes.length; ++i) {
      columnTypes[i] = TypeSpec.getEmptyType();
    }
    for (String[] entries: dataRows) {
      for (int i = 0; i < entries.length; ++i) {
        columnTypes[i].addExample(entries[i]);
      }
    }
  }
}
