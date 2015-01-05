package pitt.search.semanticvectors.tables;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.SearchResult;
import pitt.search.semanticvectors.VectorStore;
import pitt.search.semanticvectors.vectors.Vector;

import java.util.Arrays;
import java.util.List;

/**
 * Tests for class {@link pitt.search.semanticvectors.tables.Table}.
 */
public class TableTest {

  private String[] columnNames;
  private List dataRows;
  private Table table;

  @Before
  public void setUp() {
    columnNames = new String[] {"Name", "Start", "End"};
    dataRows = Arrays.asList(new String[][]{
        new String[]{"a", "1600", "1700"},
        new String[]{"b", "1600", "1800"},
        new String[]{"c", "1700", "1800"}
    });
    table = new Table(FlagConfig.getFlagConfig("-vectortype complex -dimension 1000 -seedlength 500".split(" ")), columnNames, dataRows);
  }

  @Test
  public void makeQueryVectorTest() {
    VectorStore vectors = table.getRowVectorStore();
    Assert.assertTrue(vectors.getVector("a").measureOverlap(vectors.getVector("b")) >
        vectors.getVector("a").measureOverlap(vectors.getVector("c")));
    Assert.assertTrue(vectors.getVector("c").measureOverlap(vectors.getVector("b")) >
        vectors.getVector("a").measureOverlap(vectors.getVector("c")));

    Vector queryVector = table.makeCellVector(1, "1600");
    for (SearchResult result : table.searchRowVectors(queryVector)) {
      // System.out.println(result.getScore() + ":" + result.getObjectVector().getObject());
    }

    for (int i = 1600; i <= 1700; i += 20) {
      Vector queryVector2 = table.makeCellVector(1, "" + i);
      System.out.println(i + " : " + queryVector.measureOverlap(queryVector2));
    }
  }
}
