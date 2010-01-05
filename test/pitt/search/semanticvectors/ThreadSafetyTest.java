/**
   @author Yevgeniy Treyvus.
   TODO: Confirm copyright with author.
**/

package pitt.search.semanticvectors;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.junit.*;
import static org.junit.Assert.*;
import java.io.IOException;

public class ThreadSafetyTest {

  @Before
    public void setUp() {
    assert(RunTests.prepareTestData());
    Flags.searchtype = "sum";
  }

  @Test
    public void TestSearchThreadSafety() throws Exception {
    List<Thread> threads = new ArrayList<Thread>();
    final String queries[] = new String[]{"jesus", "mary", "peter", "light", "word"};
    final boolean[] done = new boolean[queries.length];
    for(final String query : queries) {
      Thread t = new Thread(new Runnable(){
	  public void run() {
	    try {
	      outputSuggestions(query);
	      done[Arrays.asList(queries).indexOf(query)] = true;
	    } catch (Exception e) {
	      throw new RuntimeException(e.getMessage(), e);
	    }
	  }}, "query: " + query);
      t.start();
      threads.add(t);
    }
    int counter = 0;
    while(counter < queries.length) {
      for(boolean b : done) {
	if(b) {
	  counter++;
	}
      }
    }
    for(Thread t : threads) {
      t.join();
    }
  }

  synchronized /*** Comment out and it will break. ***/
  private static void outputSuggestions(String query) throws Exception  {
    int maxResults = 10;
    String[] args = new String[] { "-searchvectorfile", "termvectors.bin",
				   "-queryvectorfile", "termvectors.bin",
				   "-luceneindexpath", RunTests.lucenePositionalIndexDir,
				   query };
    LinkedList<SearchResult> results = Search.RunSearch(args, maxResults);

    boolean verbose = false;
    if (verbose && results.size() > 0) {
      for (SearchResult result: results) {
	String suggestion = ((ObjectVector)result.getObject()).getObject().toString();
	System.out.println("query:"+query + " suggestion:" + suggestion + " score:" + result.getScore());
      }
    }
  }
}
