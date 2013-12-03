package pitt.search.lucene;
import static pitt.search.semanticvectors.LuceneUtils.LUCENE_VERSION;

import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;

import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.utils.VerbatimLogger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/** 
 * This class takes as input a single text file with each line following the format 
 * <subject>\t<predicate>\t<object> and produces a Lucene index, in which each 
 * "document" is a single tab-delimited predication (or triple) with the fields subject, 
 * predicate, and object.
 */
public class LuceneIndexFromTriples {

  private LuceneIndexFromTriples() {}
  
  static File INDEX_DIR = new File("predication_index");

  /** Index all text files under a directory. */
  public static void main(String[] args) {
    String usage = "java pitt.search.lucene.LuceneIndexFromTriples [triples text file] ";
    if (args.length == 0) {
      System.err.println("Usage: " + usage);
      System.exit(1);
    }
    FlagConfig flagConfig = FlagConfig.getFlagConfig(args);
    // Allow for the specification of a directory to write the index to.
    if (flagConfig.luceneindexpath().length() > 0) {
      INDEX_DIR = new File(flagConfig.luceneindexpath());
    }
    if (INDEX_DIR.exists()) {
       throw new IllegalArgumentException(
           "Cannot save index to '" + INDEX_DIR.getAbsolutePath() + "' directory, please delete it first");
    }

    try {
      // Create IndexWriter using WhiteSpaceAnalyzer without any stopword list.
      IndexWriterConfig writerConfig = new IndexWriterConfig(
          LUCENE_VERSION, new WhitespaceAnalyzer(LUCENE_VERSION));
      IndexWriter writer = new IndexWriter(FSDirectory.open(INDEX_DIR), writerConfig);

      final File triplesTextFile = new File(args[0]);
      if (!triplesTextFile.exists() || !triplesTextFile.canRead()) {
        writer.close();
        throw new IOException("Document file '" + triplesTextFile.getAbsolutePath() +
            "' does not exist or is not readable, please check the path");
      }

      System.out.println("Indexing to directory '" +INDEX_DIR+ "'...");
      indexDoc(writer, triplesTextFile);
      writer.close();       
    } catch (IOException e) {
      System.out.println(" caught a " + e.getClass() +
          "\n with message: " + e.getMessage());
    }
  }

  /**
   * This class indexes the file passed as a parameter, writing to the index passed as a parameter.
   * Each predication is indexed as an individual document, with the fields "subject", "predicate", and "object"

   * @throws IOException
   */
  static void indexDoc(IndexWriter fsWriter, File triplesTextFile) throws IOException {
    BufferedReader theReader = new BufferedReader(new FileReader(triplesTextFile));
    int linecnt = 0;
    String lineIn;
    while ((lineIn = theReader.readLine()) != null)  {   
      java.util.StringTokenizer theTokenizer = new java.util.StringTokenizer(lineIn,"\t");
      // Output progress counter.
      if( ( ++linecnt % 10000 == 0 ) || ( linecnt < 10000 && linecnt % 1000 == 0 ) ){
        VerbatimLogger.info((linecnt) + " ... ");
      }
      try {
        if (theTokenizer.countTokens() < 3) {
          VerbatimLogger.warning(
              "Line in predication file does not have three delimited fields: " + lineIn + "\n");
          continue;
        }

        String subject = theTokenizer.nextToken().trim().toLowerCase().replaceAll(" ", "_");
        String predicate = theTokenizer.nextToken().trim().toUpperCase().replaceAll(" ", "_");
        String object = theTokenizer.nextToken().trim().toLowerCase().replaceAll(" ", "_");

        Document doc = new Document();
        doc.add(new TextField("subject", subject, Field.Store.YES));
        doc.add(new TextField("predicate", predicate, Field.Store.YES));
        doc.add(new TextField("object", object, Field.Store.YES));
        doc.add(new TextField("predication",subject+predicate+object, Field.Store.NO));
        fsWriter.addDocument(doc);
      }
      catch (Exception e) {
        System.out.println(lineIn);
        e.printStackTrace();
      }
    }
    VerbatimLogger.info("\n");  // Newline after line counter prints.
    theReader.close();
  }
}
