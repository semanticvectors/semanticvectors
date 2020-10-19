package pitt.search.lucene;

import static pitt.search.semanticvectors.LuceneUtils.LUCENE_VERSION;

import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.index.IndexOptions;
import pitt.search.semanticvectors.utils.VerbatimLogger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

/** 
 * This class takes as input a single text file exported from the SemMedDB database structure
 * using the following command:
 * 
 * mysql [database_name] -e 'select s_name, s_cui, s_type, predicate, o_name, o_cui, o_type,  SENTENCE.PMID, 
 * sentence from PREDICATION_AGGREGATE,SENTENCE where PREDICATION_AGGREGATE.SID=SENTENCE.SENTENCE_ID' > textfile.txt
 * 
 * The resulting fields are:
 * subject: the preferred form of the subject (e.g. haloperidol)
 * subject_CUI: the UMLS concept unique identifier  
 * subject_semtype: the UMLS semantic type of the subject 
 * predicate: the predicate, e.g. TREATS 
 * object, object_CUI, object_semtype : as above but for the object
 * pmid		: the PubMed identifier for the article a predication was extracted from
 * source	: the source sentence for this predication
 * 
 * 
 */
public class LuceneIndexFromSemrepTriplesPL {

    private LuceneIndexFromSemrepTriplesPL() {}
    //static final File INDEX_DIR = new File("predication_index");
    static Path INDEX_DIR =  FileSystems.getDefault().getPath("predication_index");

    /** Index all text files under a directory. */
    public static void main(String[] args) {
	String usage = "java pitt.search.lucene.LuceneIndexFromTriples [triples text file] ";
	if (args.length == 0) {
	    System.err.println("Usage: " + usage);
	    System.exit(1);
	}
       
	if (Files.exists(INDEX_DIR)) {
		throw new IllegalArgumentException(
						   "Cannot save index to '" + INDEX_DIR + "' directory, please delete it first");
	    }
	

	try {
	    // Create IndexWriter using WhiteSpaceAnalyzer without any stopword list.
	    //IndexWriterConfig writerConfig = new IndexWriterConfig(
	    //							   LUCENE_VERSION, new WhitespaceAnalyzer(LUCENE_VERSION));

	    IndexWriterConfig writerConfig = new IndexWriterConfig(new WhitespaceAnalyzer());

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
		    lineIn = theReader.readLine();
		    continue;
		}

		String[] subject = theTokenizer.nextToken().trim().toLowerCase().replaceAll(" ", "_").split("\\|");
		String[] subject_CUI = theTokenizer.nextToken().trim().toLowerCase().replaceAll(" ", "_").split("\\|");
		String subject_semtype = theTokenizer.nextToken().trim().toLowerCase().replaceAll(" ", "_");
        
		String predicate = theTokenizer.nextToken().trim().toUpperCase().replaceAll(" ", "_");
		String[] object = theTokenizer.nextToken().trim().toLowerCase().replaceAll(" ", "_").split("\\|");
		String[] object_CUI = theTokenizer.nextToken().trim().toLowerCase().split("\\|");
		String object_semtype = theTokenizer.nextToken().trim().toLowerCase().replaceAll(" ", "_");
        
		String PMID = theTokenizer.nextToken();
		String source = theTokenizer.nextToken();
        
		Document doc = new Document();
		
		for (int si = 0; si < subject.length; si++)
		for (int oi = 0; oi < object.length; oi++)
		{
		doc.add(new TextField("subject", subject[si], Field.Store.YES));
		doc.add(new TextField("subject_CUI", subject_CUI[si], Field.Store.YES));
		doc.add(new TextField("subject_semtype", subject_semtype, Field.Store.YES));
		doc.add(new TextField("predicate", predicate, Field.Store.YES));
		doc.add(new TextField("object", object[oi], Field.Store.YES));
		doc.add(new TextField("object_CUI", object_CUI[oi], Field.Store.YES));
		doc.add(new TextField("object_semtype", object_semtype, Field.Store.YES));
		doc.add(new TextField("predication",subject+predicate+object, Field.Store.NO));
		doc.add(new TextField("PMID",PMID, Field.Store.YES));
          
		//create new FieldType to store term positions (TextField is not sufficiently configurable)
		FieldType ft = new FieldType();
		//the next line was commented out when the original index was buildt (v1.0)
		//ft.setIndexed(true);
		ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
		ft.setStored(true);
		ft.setTokenized(true);
		ft.setStoreTermVectors(true);
		ft.setStoreTermVectorPositions(true);
		Field contentsField = new Field("source", source, ft);
		doc.add(contentsField);
          
		
		fsWriter.addDocument(doc);
		}
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
