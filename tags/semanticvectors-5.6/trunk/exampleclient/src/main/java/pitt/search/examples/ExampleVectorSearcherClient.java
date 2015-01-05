package pitt.search.examples;

import pitt.search.semanticvectors.*;
import pitt.search.semanticvectors.vectors.ZeroVectorException;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Scanner;

/**
 * Example of an external project that uses {@link pitt.search.semanticvectors.VectorSearcher} directly.
 *
 * For links to more examples, see https://code.google.com/p/semanticvectors/wiki/ExampleClients.
 */
public class ExampleVectorSearcherClient {

  public static String DEFAULT_VECTOR_FILE = "src/test/resources/termvectors.bin";

  /**
   * Opens vector store from given arg, or if this is empty uses {@link #DEFAULT_VECTOR_FILE}.
   * Then prompts the user for terms to search for.
   *
   * @throws IOException If vector store can't be found.
   */
  public static void main(String[] args) throws IOException {
    String vectorStoreName;
    if (args.length == 0) {
      vectorStoreName = DEFAULT_VECTOR_FILE;
    } else {
      vectorStoreName = args[0];
    }

    FlagConfig defaultFlagConfig = FlagConfig.getFlagConfig(null);
    VectorStoreRAM searchVectorStore = VectorStoreRAM.readFromFile(defaultFlagConfig, vectorStoreName);
    LuceneUtils luceneUtils = null;  // Not needed for this simple demo.

    while(true) {
      System.out.println("Enter a query term:");
      Scanner sc = new Scanner(System.in);
      String queryTerm = sc.next();
      try {
        VectorSearcher searcher = new VectorSearcher.VectorSearcherCosine(
            searchVectorStore, searchVectorStore, luceneUtils, defaultFlagConfig, new String[] {queryTerm});
        LinkedList<SearchResult> results = searcher.getNearestNeighbors(10);
        for (SearchResult result : results) {
          System.out.println(result.getScore() + ":" + result.getObjectVector().getObject());
        }
      } catch (ZeroVectorException e) {
        System.out.println("No vector for term: '" + queryTerm + "'.");
      }
    }
  }
}
