package pitt.search.semanticvectors.tables;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.SearchResult;
import pitt.search.semanticvectors.VectorStoreWriter;
import pitt.search.semanticvectors.utils.StringUtils;
import pitt.search.semanticvectors.utils.VerbatimLogger;
import pitt.search.semanticvectors.vectors.Vector;

/**
 * Class that reads input data from a stream and organizes it into records and columns.
 * 
 * @author dwiddows
 */
public class TableIndexer {

  public static final String usageMessage =
      "Usage: java pitt.search.semanticvectors.tables.TableIndexer [--args] $TABLE_CSV_FILENAME";

  /** Experiment in querying for a particular inauguration date. */
  private static void queryForSpecialValues(Table table) {
    System.out.println("Querying for time took office 1800");
    Vector queryVector = table.makeCellVector(2, "1800");
    for (SearchResult result : table.searchRowVectors(queryVector)) {
      System.out.println(result.getScore() + ":" + result.getObjectVector().getObject());
    }
    System.out.println("Querying for year of birth 1900");
    queryVector = table.makeCellVector(5, "1900");
    for (SearchResult result : table.searchRowVectors(queryVector)) {
      System.out.println(result.getScore() + ":" + result.getObjectVector().getObject());
    }
  }

  /** Experiment in querying for a particular president's name. */
  private static void queryForName(Table table, String name) {
    System.out.println("Querying for name: '" + name + "'");
    Vector queryVector = table.getRowVectorStore().getVector(name);
    for (SearchResult result : table.searchRowVectors(queryVector)) {
      System.out.println(result.getScore() + ":" + result.getObjectVector().getObject());
    }
  }

  public static void main(String[] args) throws IOException {
    FlagConfig flagConfig = null;
    try {
      flagConfig = FlagConfig.getFlagConfig(args);
      args = flagConfig.remainingArgs;
    } catch (IllegalArgumentException e) {
      System.err.println(usageMessage);
      throw e;
    }

    if (flagConfig.remainingArgs.length != 1) {
      throw new IllegalArgumentException("Wrong number of arguments after parsing command line flags.\n" + usageMessage);
    }

    VerbatimLogger.info("Building vector index of table in file: " + args[0] + "\n");
    BufferedReader fileReader = new BufferedReader(new FileReader(args[0]));
    String[] columnHeaders = fileReader.readLine().split(",");
    ArrayList<String[]> dataRows = new ArrayList<>();
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

    queryForSpecialValues(table);
    queryForName(table, "J. Adams");
    queryForName(table, "T. Roosevelt");
  }
}
