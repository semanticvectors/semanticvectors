package pitt.search.lucene;

import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.core.LowerCaseTokenizer;
import org.apache.lucene.analysis.en.PorterStemFilter;

import java.io.*;
import java.util.logging.Logger;

import static pitt.search.semanticvectors.LuceneUtils.LUCENE_VERSION;

public class PorterAnalyzer  extends Analyzer {

  @Override
  protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
    Tokenizer source = new LowerCaseTokenizer(LUCENE_VERSION, reader);
    return new TokenStreamComponents(source, new PorterStemFilter(source));
  }

  /**
   * Performs Porter stemming on a query String passed as a parameter
   * @param query string
   * @return query string with each word replaced with a stemmed version
   */
  public String stemQuery(String query) {
    Logger logger = Logger.getLogger("pitt.search.lucene");

    String stemmedQuery = "";
    TokenStream theTS = createComponents("", new StringReader(query)).getTokenStream();

    try {
      while (theTS.incrementToken()) {
        String theTS_s = theTS.toString().replaceAll(".*term=", "");
        stemmedQuery += theTS_s.substring(0, theTS_s.length()-1) + " ";
      }
    }
    catch (IOException e) {
      logger.info("Error while stemming query "+query);
    }

    return stemmedQuery;
  }


  /**
   * convenience method: takes text file name as argument, produces stemmed version of this text file
   * as command line output
   * @param args : name of text file
   */
  public static void main(String[] args) throws Exception {
    PorterAnalyzer thePorterAnalyzer = new PorterAnalyzer();
    System.err.println("Attempting to perform Porter stemming on file "+args[0]);

    BufferedReader inReader = new BufferedReader(new FileReader(args[0]));
    String inLine = inReader.readLine();

    while (inLine != null) {
      System.out.println(thePorterAnalyzer.stemQuery(inLine));
      inLine = inReader.readLine();
    }
    thePorterAnalyzer.close();
    inReader.close();
  }
}
