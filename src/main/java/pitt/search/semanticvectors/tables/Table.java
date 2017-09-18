package pitt.search.semanticvectors.tables;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import pitt.search.semanticvectors.*;
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
  private VectorStoreRAM rowSummaryVectors;
  private VectorStoreOrthographical orthographicVectorStore;

  private ElementalVectorStore randomElementalVectors;

  public Table(FlagConfig flagConfig, String[] columnNames, List<String[]> dataRows) {
    this.flagConfig = flagConfig;
    this.orthographicVectorStore = new VectorStoreOrthographical(flagConfig);
    this.randomElementalVectors = new ElementalVectorStore(flagConfig);
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
    for (String[] dataRow: dataRows) {
      this.addRow(dataRow);
    }
  }
  
  public VectorStore getRowVectorStore() {
    return this.rowSummaryVectors;
  }

  /** Returns header vector for particular column in the table. */
  public Vector getColumnVector(int colIndex)
  {
	 return columnHeaders[colIndex].getVector().copy();
  }
  
  /** Returns demarcator vector for particular column in the table. */
  public Vector getColumnAlphaVector(int colIndex)
  {
	  return columnTypes[colIndex].getDoubleValueVector(flagConfig, columnTypes[colIndex].getMinDoubleValue()).copy();
  }

  public Vector getColumnOmegaVector(int colIndex)
  {
	  return columnTypes[colIndex].getDoubleValueVector(flagConfig, columnTypes[colIndex].getMaxDoubleValue()).copy();
  }
  
  public Vector getDemarcatorVector(int colIndex, double value)
  {
	  return columnTypes[colIndex].getDoubleValueVector(flagConfig, value).copy();
  }  
  
  /** Returns a vector for a particular cell in the table. */
  public Vector makeCellVector(int colIndex, String value) {
    if (value.trim().isEmpty()) {
      return VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());
    }

    Vector valueVector = null;

    boolean useTabular = true;

    if (useTabular) {
      switch (columnTypes[colIndex].getType()) {
        case STRING:
        	valueVector = randomElementalVectors.getVector(value); 
        	break;
        case DOUBLE:
                	valueVector = columnTypes[colIndex].getDoubleValueVector(
              flagConfig, Double.parseDouble(value));
            break;
      }
    } else {
      switch (flagConfig.elementalmethod()) {
        case RANDOM:
          valueVector = randomElementalVectors.getVector(value);
          break;
        case CONTENTHASH:
          //throw new NotImplementedException();
        case ORTHOGRAPHIC:
          valueVector = orthographicVectorStore.getVector(value);
          break;
      }
    }

    if (valueVector == null) {
      return VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());
    }

    Vector boundColVal = columnHeaders[colIndex].getVector().copy();
    boundColVal.bind(valueVector);
      return boundColVal;
  }

  public LinkedList<SearchResult> searchRowVectors(Vector queryVector) {
    VectorSearcher.VectorSearcherPlain searcher = new VectorSearcher.VectorSearcherPlain(
        getRowVectorStore(), queryVector, flagConfig);
    return searcher.getNearestNeighbors(flagConfig.numsearchresults());
  }
  
  public LinkedList<SearchResult>  searchProxRowVectors(Vector elementalYOB, Vector elementalTTO,
			Vector demarcatorAlpha, Vector demarcatorOmega) {
		// TODO Auto-generated method stub
	   	  VectorSearcher.VectorSearcherProximity searcher = new VectorSearcher.VectorSearcherProximity(
	   			elementalYOB, elementalTTO, demarcatorAlpha, demarcatorOmega, getRowVectorStore(), flagConfig);
	  
	   	  return searcher.getNearestNeighbors(flagConfig.numsearchresults());
	  
	}
  
  private void addRow(String[] rowValues) {
    Vector accumulator = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());
    for (int i = 0; i < columnHeaders.length; ++i) {
      accumulator.superpose(makeCellVector(i, rowValues[i]), 1, null);
    }
   accumulator.normalize();
    rowSummaryVectors.putVector(rowValues[0], accumulator);
  }

  private void prepareTypeSchema(List<String[]> dataRows) {
    for (int i = 0; i < columnTypes.length; ++i) {
      columnTypes[i] = TypeSpec.getEmptyType();
    }
    for (String[] entries: dataRows) {
      for (int i = 0; i < entries.length; ++i) {
        columnTypes[i].addExample(entries[i]);
      }
     
    }

    // Special condition for birth/term example  (to be removed)
    // Work by Trevor for graded semantic vectors paper.
    /*
    double minYear = Math.min(columnTypes[2].getMinDoubleValue(), columnTypes[5].getMinDoubleValue());
    double maxYear = Math.max(columnTypes[2].getMaxDoubleValue(), columnTypes[5].getMaxDoubleValue());
    columnTypes[5].setMaxDoubleValue(maxYear);
    columnTypes[2].setMaxDoubleValue(maxYear);
    columnTypes[5].setMinDoubleValue(minYear);
    columnTypes[2].setMinDoubleValue(minYear);
    System.err.println(columnTypes[5].getMaxDoubleValue()+"\t"+columnTypes[2].getMaxDoubleValue());
    System.err.println(columnTypes[5].getMinDoubleValue()+"\t"+columnTypes[2].getMinDoubleValue());
    */
    
    
 
    // Now we've seen all values, those we know to be numeric should be prepared with bookend vectors.
    for (int i = 0; i < columnTypes.length; ++i) {
      if (columnTypes[i].getType() == TypeSpec.SupportedType.DOUBLE) {
    	// columnTypes[i].addMinMaxVectors(flagConfig, "standard_demarcator");
       columnTypes[i].addMinMaxVectors(flagConfig, columnHeaders[i].getObject().toString());
      }
    }
  }


}
