
package pitt.search.lucene;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;
import java.util.TreeSet;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.apache.lucene.analysis.PorterStemFilter;

import pitt.search.semanticvectors.FlagConfig;


/** Index all text files under a directory. This class makes minor
 * modifications to <code>org.apache.lucene.demos.IndexFiles</code>
 * using a new document handler.
 * @see FilePositionDoc
 */
public class IndexFilePositions {

  private IndexFilePositions() {}

  static File INDEX_DIR = new File("positional_index");

  /** Index all text files under a directory. */
  public static void main(String[] args) {
    FlagConfig flagConfig = null;
    String usage = "java pitt.search.lucene.IndexFilePositions <root_directory> ";
    if (args.length == 0) {
      System.err.println("Usage: " + usage);
      System.exit(1);
    }
    if (args.length > 0) {
      flagConfig = FlagConfig.getFlagConfig(args);
      // Allow for the specification of a directory to write the index to.
      if (flagConfig.luceneindexpath().length() > 0)
        INDEX_DIR = new File(flagConfig.luceneindexpath() + INDEX_DIR.getName());
    }
    if (INDEX_DIR.exists()) {
      throw new IllegalArgumentException(
          "Cannot save index to '" + INDEX_DIR.getAbsolutePath() + "' directory, please delete it first");
    }
    try {
    	IndexWriter writer;
    	if (flagConfig.porterstemmer()) {
    	  // Create IndexWriter using porter stemmer without any stopword list.
    		writer = new IndexWriter(FSDirectory.open(INDEX_DIR),
    		    new PorterAnalyzer(),
    		    true, MaxFieldLength.UNLIMITED);
    	} 
    	else	{
    	  // Create IndexWriter using StandardAnalyzer without any stopword list.
    		writer = new IndexWriter(FSDirectory.open(INDEX_DIR),
    		    new StandardAnalyzer(Version.LUCENE_30, new TreeSet()),
    		    true, MaxFieldLength.UNLIMITED);
    	}

    	final File docDir = new File(flagConfig.remainingArgs[0]);
      if (!docDir.exists() || !docDir.canRead()) {
        throw new IOException ("Document directory '" + docDir.getAbsolutePath() +
            "' does not exist or is not readable, please check the path");
      }

      Date start = new Date();

      System.out.println("Indexing to directory '" +INDEX_DIR+ "'...");
      indexDocs(writer, docDir);
      System.out.println("Optimizing...");
      writer.optimize();
      writer.close();

      Date end = new Date();
      System.out.println(end.getTime() - start.getTime() + " total milliseconds");

    } catch (IOException e) {
      System.out.println(" caught a " + e.getClass() +
                         "\n with message: " + e.getMessage());
    }
  }

  static void indexDocs(IndexWriter writer, File file)
      throws IOException {
    // Do not try to index files that cannot be read.
    if (file.canRead()) {
      if (file.isDirectory()) {
        String[] files = file.list();
        // An IO error could occur.
        if (files != null) {
          for (int i = 0; i < files.length; i++) {
            // Skip dot files.
            if (!files[i].startsWith(".")) {
              indexDocs(writer, new File(file, files[i]));
            }
          }
        }
      } else {
        System.out.println("adding " + file);
        try {
          // Use FilePositionDoc rather than FileDoc such that term
          // positions are indexed also.
          writer.addDocument(FilePositionDoc.Document(file));
        }
        // At least on windows, some temporary files raise this
        // exception with an "access denied" message. Checking if the
        // file can be read doesn't help
        catch (FileNotFoundException fnfe) {
          fnfe.printStackTrace();
        }
      }
    }
  }
}
