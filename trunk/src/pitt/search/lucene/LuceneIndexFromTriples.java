package pitt.search.lucene;
import org.apache.lucene.analysis.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.store.FSDirectory;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/** 
 * This class takes as input a single text file with each line following the format 
 * <subject>\t<predicate>\t<object> and produces a Lucene index, in which each 
 * "document" is a single tab-delimited predication (or triple) with the fields subject, 
 * predicate, and object
 * 
 */
public class LuceneIndexFromTriples {
  
  private LuceneIndexFromTriples() {}
  static final File INDEX_DIR = new File("predication_index");
 
  
  
  /** Index all text files under a directory. */
  public static void main(String[] args) {
	
	  
	  String usage = "java pitt.search.lucene.LuceneIndexFromTriples [triples text file] ";
    if (args.length == 0) {
      System.err.println("Usage: " + usage);
      System.exit(1);
    }
    if (INDEX_DIR.exists()) {
			System.out.println(INDEX_DIR.getAbsolutePath());
    	System.out.println("Cannot save index to '" + INDEX_DIR + "' directory, please delete it first");
		//	System.exit(1);
		}
   
    try {
    	
    	/** create IndexWriter using WhiteSpaceAnalyzer without any stopword list**/
		IndexWriter writer = new IndexWriter(FSDirectory.open(INDEX_DIR),
                                       new WhitespaceAnalyzer(),
                                       true, MaxFieldLength.UNLIMITED);
			
    	final File docDir = new File(args[0]);
			if (!docDir.exists() || !docDir.canRead()) {
				System.err.println("Document file '" + docDir.getAbsolutePath() +
													 "' does not exist or is not readable, please check the path");
				System.exit(1);
			}
    
			

      System.out.println("Indexing to directory '" +INDEX_DIR+ "'...");
      indexDoc(writer, docDir);
      System.out.println("Optimizing...");
     
      
      writer.optimize();
      writer.close();
       
    } catch (IOException e) {
      System.out.println(" caught a " + e.getClass() +
												 "\n with message: " + e.getMessage());
    }
  }


  /**
   * This class indexes the file passed as a parameter, writing to the index passed as a parameter.
   * Each predication is indexed as an individual document, with the fields "subject", "predicate", and "object"
   * 
   * @param fsWriter
   * @param file
   * @throws IOException
   */
  
  static void indexDoc(IndexWriter fsWriter, File file)
  throws IOException {
 
    	  BufferedReader theReader = new BufferedReader(new FileReader(file));
      	int tc = 0;
      	String lineIn = theReader.readLine();
      	lineIn = theReader.readLine();
      	int linecnt =0;
      	while (lineIn != null)
      	{   
      		
      		java.util.StringTokenizer theTokenizer = new java.util.StringTokenizer(lineIn,"\t");
      		/* output progress counter */
      		boolean semtype = false;
    
    	    	
    	    if( ( ++linecnt % 10000 == 0 ) || ( linecnt < 10000 && linecnt % 1000 == 0 ) ){
    	      	System.err.print((linecnt) + " ... ");
    	      }
    	    try {
    	    
    	    String subject = theTokenizer.nextToken().trim().toLowerCase().replaceAll(" ", "_");
    	    String predication = theTokenizer.nextToken().trim().toUpperCase().replaceAll(" ", "_");
    	    String object = theTokenizer.nextToken().trim().toLowerCase().replaceAll(" ", "_");
    	     
    	    
    	    Document doc = new Document();
    	    doc.add(new Field("subject",subject, Field.Store.YES, Field.Index.ANALYZED));
    	    doc.add(new Field("predicate",predication, Field.Store.YES, Field.Index.ANALYZED));
    	    doc.add(new Field("object",object, Field.Store.YES, Field.Index.ANALYZED));
    	   	    
    	    System.out.println(subject+" "+predication+" "+object);
    	    fsWriter.addDocument(doc);
    	    
    	    
    	    }
    	    catch (Exception e)
    	    {System.out.println(lineIn);
    	     
    	    }
      
    	    lineIn = theReader.readLine();
      		 
      		 		
      	  }
      }
  }
