package pitt.search.lucene;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;

/** Index all text files under a directory. This class makes minor
 * modifications to <code>org.apache.lucene.demos.IndexFiles</code>
 * using a new document handler.
 * @see FilePositionDoc
 */
public class IndexFilePositions {

  private IndexFilePositions() {}

  static final File INDEX_DIR = new File("positional_index");

  /** Index all text files under a directory. */
  public static void main(String[] args) {
    String usage = "java pitt.search.lucene.IndexFilePositions <root_directory> " +
        "[optional stoplist file]";
    if (args.length == 0) {
      System.err.println("Usage: " + usage);
      System.exit(1);
    }
    if (INDEX_DIR.exists()) {
      System.out.println(INDEX_DIR.getAbsolutePath());
      System.out.println("Cannot save index to '" + INDEX_DIR + "' directory, please delete it first");
      System.exit(1);
    }
    try {
      //  Create StandardAnalyzer with the default StandardAnalyzer stopword list.
      IndexWriter writer = new IndexWriter(INDEX_DIR, new StandardAnalyzer(),true);

      if (args.length ==2) {
        // Use a stop-list passed as a parameter.
        String stopfile = args[1];
        try {
          File stoplist = new File(stopfile);
          writer = new IndexWriter(INDEX_DIR, new StandardAnalyzer(stoplist), true);
          System.out.println("Using stoplist: "+stopfile);
        } catch (IOException e){
          e.printStackTrace();
        }
      }

      final File docDir = new File(args[0]);
      if (!docDir.exists() || !docDir.canRead()) {
        System.err.println("Document directory '" + docDir.getAbsolutePath() +
                           "' does not exist or is not readable, please check the path");
        System.exit(1);
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
            indexDocs(writer, new File(file, files[i]));
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
