package pitt.search.semanticvectors.tables;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.VectorStoreWriter;
import pitt.search.semanticvectors.utils.StringUtils;
import pitt.search.semanticvectors.utils.VerbatimLogger;

/**
 * Class that reads input data from a stream and organizes it into records and columns.
 * 
 * @author dwiddows
 */
public class TableIndexer {

  public static final String usageMessage =
      "Usage: java pitt.search.semanticvectors.tables.TableIndexer [--args] $TABLE_CSV_FILENAME";
  
  public static void main(String[] args) throws IOException {
    FlagConfig flagConfig = null;
    try {
      flagConfig = FlagConfig.getFlagConfig(args);
      args = flagConfig.remainingArgs;
    } catch (IllegalArgumentException e) {
      System.err.println(usageMessage);
      throw e;
    }
    
    VerbatimLogger.info("Building vector index of table in file: " + args[0] + "\n");
    BufferedReader fileReader = new BufferedReader(new FileReader(args[0]));
    String[] columnHeaders = fileReader.readLine().split(",");
    ArrayList<String[]> dataRows = new ArrayList<String[]>();
    String dataLine;
    while((dataLine = fileReader.readLine()) != null) {
      String[] dataEntries = dataLine.split(",");
      if (dataEntries.length != columnHeaders.length) {
        throw new IllegalArgumentException(String.format(
            "Column headers have length %d and this row has length %d. This indicates a data error or a csv parsing error."
            + "\nColumn headers:%s\nData row: %s\n",
            columnHeaders.length, dataEntries.length,
            StringUtils.join(columnHeaders), StringUtils.join(dataEntries)));
      }
      dataRows.add(dataEntries);
    }
    fileReader.close();
    
    Table table = new Table(flagConfig, columnHeaders, dataRows);
    VectorStoreWriter.writeVectors(flagConfig.termvectorsfile(), flagConfig, table.getRowVectorStore());
  }
}
