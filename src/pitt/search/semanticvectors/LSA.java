package pitt.search.semanticvectors;
import org.apache.lucene.index.IndexModifier;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.MMapDirectory;
import java.io.File;
import ch.akuhn.edu.mit.tedlab.*;

/**
 * Interface to Adrian Kuhn and David Erni's implementation of SVDLIBJ, a native Java version
 * of Doug Rhodes' SVDLIBC, which was in turn based on SVDPACK, by Michael Berry, Theresa Do, 
 * Gavin O'Brien, Vijay Krishna and Sowmini Varadhan.
 * 
 * This class will produce two files, svd_termvectors.bin and svd_docvectors.bin from a Lucene index
 * Command line arguments are consistent with the rest of the Semantic Vectors Package
 * 
 */

public class LSA {
	  
	 static boolean le = false;
	  static String[] theTerms;
	 
	  /**
	   * Prints the following usage message:
	   * <code>
	   * <br> LSA class in package pitt.search.semanticvectors
	   * <br> Usage: java pitt.search.semanticvectors.LSA PATH_TO_LUCENE_INDEX
	   * <br> LSA creates svd_termvectors and svd_docvectors files in local directory.
	   * <br> Other parameters that can be changed include vector length,
	   * <br>     (number of dimensions), seed length (number of non-zero
	   * <br>     entries in basic vectors) and minimum term frequency.
	   * <br>    
	   * <br> To change these use the following command line arguments:
	   * <br> -dimension [number of dimensions]
	   * <br> -minfrequency [minimum term frequency]
	   * <br> -numnonalphabetchars [number non-alphabet characters (-1 for any number)]
	    * </code>
	   */
	  
	  public static void usage() {
	    String usageMessage = "\nLSA class in package pitt.search.semanticvectors"
	        + "\nUsage: java pitt.search.semanticvectors.LSA PATH_TO_LUCENE_INDEX"
	        + "\nBuildIndex creates svd_termvectors and svd_docvectors files in local directory."
	        + "\nOther parameters that can be changed include vector length,"
	        + "\n    (number of dimensions), seed length (number of non-zero"
	        + "\n    entries in basic vectors), and minimum term frequency."
	        + "\nTo change these use the command line arguments "
	        + "\n  -dimension [number of dimensions]"
	        + "\n  -minfrequency [minimum term frequency]"
	        + "\n  -numnonalphabetchars [number non-alphabet characters (-1 for any number)]";
	    
	   System.out.println(usageMessage);
	  }

	  /**
	   * Builds term vector and document vector stores from a Lucene index, using SVD to reduce dimensions
	   * @param args
	   * @see LSA#usage
	   */
	  
	  
	   /* Converts a dense matrix to a sparse one (without affecting the dense one) */
    static SMat smatFromIndex(String fileName) throws Exception {
        SMat S;
        int i, j, n;
        
        //initiate IndexReader and LuceneUtils
        File file = new File(fileName);
    	IndexReader indexReader = IndexReader.open(file);
    	pitt.search.semanticvectors.LuceneUtils lUtils = new pitt.search.semanticvectors.LuceneUtils(fileName);
    	
     	int[][] index;
    	int nonalphabet = Flags.maxnonalphabetchars;
    	int minfreq = Flags.minfrequency;
    	String[] desiredFields = Flags.contentsfields;
    	
    	 TermEnum terms = indexReader.terms();
    	    int tc = 0;
    	    while(terms.next()){
    	    	if (lUtils.termFilter(terms.term(),desiredFields,nonalphabet,minfreq))
    	    	tc++;
    	    }
    	    
    	    System.err.println("There are " + tc + " terms (and " + indexReader.numDocs() + " docs)");
    	    theTerms = new String[tc];
    	    index = new int[tc][];
    	
    		 terms = indexReader.terms();
    		   tc = 0;
    		   int nonzerovals = 0;
    		   
    		    while(terms.next()){
    		    org.apache.lucene.index.Term term = terms.term();
    		        if (lUtils.termFilter(term,desiredFields,nonalphabet,minfreq))
    		    	{	theTerms[tc] = term.text();
    		
	    	 /**
	    	  * create matrix of nonzero indices
	    	  */
	        	
	        	TermDocs td = indexReader.termDocs(term);
	    	 	int count =0;
	    	 	while (td.next())
	    	 				{ count ++;
	    	 					nonzerovals++;
	    	 				}
	    	 	index[tc] = new int[count]; 
	    	
	    	/**
	    	 * fill in matrix of nonzero indices
	    	 */
	    		
	    	 	td = indexReader.termDocs(term);
	    	 	count = 0;
	    	 	while (td.next())
	    	 	{index[tc][count++] = td.doc();}
	    			
		     tc++;	//next term
	    	}
	    	}
    		    
    		  /**
    		   * initialize "SVDLIBJ" sparse data structure
    		   */
    		    
    		    S = new SMat(indexReader.numDocs(),tc, nonzerovals);
    		    
	     	/** 
		  	 * populate "SVDLIBJ" sparse data structure
		  	 */
		  
    		      terms = indexReader.terms();
    			  tc = 0;
    			  int nonzerocounter = 0;
    			  int nn= 0;
		  
		    while(terms.next()){
		    	
		    	org.apache.lucene.index.Term term = terms.term();
		    	if (lUtils.termFilter(term,desiredFields,nonalphabet,minfreq))
		    	{	
		    		TermDocs td = indexReader.termDocs(term);
		    		S.pointr[tc] = nn;  // index of first non-zero entry (document) of each column (term) 
		    					
		    	 	while (td.next())
		    	 	{  /** public int[] pointr;  For each col (plus 1), index of first non-zero entry. 
		    	 	      *  we'll represent the matrix as a document x term matrix
		    	 	      *  such that terms are columns (otherwise it would be difficult to extract this information from the lucene index)
		    	 		  */
		    	 	
		    	 		int rowindex = td.doc();
		    	 		float value = td.freq();
		    	 		
				    	/**
				    	 * if log-entropy weighting is to be used
				    	 */
				    	
				    	if (le)
				    		{float entropy = lUtils.getEntropy(term);
				    		float log1plus = (float) Math.log10(1+value); 
				    		value = entropy*log1plus;
				    		}
		    	 		
				    	S.rowind[nn] = td.doc();  //set row index to document number
				    	S.value[nn] = value;  	  //set value to frequency (with/without weighting) 
				    	nn++;
		    	 	}
		    	 	tc++;	 
		    	}
		    }
		    S.pointr[S.cols] = S.vals;
		    
		    return S;
    }
    	    
	
	
	
public static void main(String[] args) throws Exception
{
    try {
        args = Flags.parseCommandLineFlags(args);
      } catch (IllegalArgumentException e) {
        usage();
        throw e;
      }

      // Only one argument should remain, the path to the Lucene index.
      if (args.length != 1) {
        usage();
        throw (new IllegalArgumentException("After parsing command line flags, there were " + args.length
                                            + " arguments, instead of the expected 1."));
      }
	
      System.err.println("Seedlength = " + Flags.seedlength);
      System.err.println("Dimension = " + Flags.dimension);
      System.err.println("Minimum frequency = " + Flags.minfrequency);
      System.err.println("Number non-alphabet characters = " + Flags.maxnonalphabetchars);

      le = Flags.termweight.equals("logentropy");
      if (le)
    	  System.err.println("Term weighting: log-entropy");
      
	
	SMat A = smatFromIndex(args[0]);
	Svdlib svd = new Svdlib();
	
	System.err.println("Starting SVD using algorithm LAS2");
		
	SVDRec svdR = svd.svdLAS2A(A, Flags.dimension);
	DMat vT = svdR.Vt;
	DMat uT = svdR.Ut;
	
	System.out.println("vT rows "+vT.rows+" vT cols "+vT.cols);
	System.out.println("uT rows "+uT.rows+" uT cols "+uT.cols);
	
			    	
			    	
			    	// Open file and write headers.
					MMapDirectory dir = new MMapDirectory();
			    	String termFile = "svd_termvectors.bin";
			    	IndexOutput outputStream = dir.createOutput(termFile);
					float[] tmpVector = new float[Flags.dimension];

					int counter = 0;
					System.err.println("Write vectors incrementally to file " + termFile);

					// Write header giving number of dimensions for all vectors.
					outputStream.writeString("-dimensions");
					outputStream.writeInt(Flags.dimension);
		
					int cnt;
				// Write out term vectors
					for (cnt = 0; cnt < vT.cols; cnt++)
					{
					outputStream.writeString(theTerms[cnt]);
					
					float[] termVector = new float[Flags.dimension];
									
					for (int i = 0; i < Flags.dimension; i++)
					termVector[i] = (float) vT.value[i][cnt];
					termVector = VectorUtils.getNormalizedVector(termVector);
					
					
					for (int i = 0; i < Flags.dimension; ++i) {
						
						outputStream.writeInt(Float.floatToIntBits(termVector[i]));
					}
					}
					
					System.err.println("Wrote "+cnt+" term vectors to "+termFile);
				  	outputStream.flush();
			    	outputStream.close();
			    	
			    	
			    	/*
					 * Write document vectors
					 */
			   
			    	// Open file and write headers.
				
			    	String docFile = "svd_docvectors.bin";
			    	outputStream = dir.createOutput(docFile);
					tmpVector = new float[Flags.dimension];
					counter = 0;
					System.err.println("Write vectors incrementally to file " + docFile);

					// Write header giving number of dimensions for all vectors.
					outputStream.writeString("-dimensions");
					outputStream.writeInt(Flags.dimension);
		
					  //initiate IndexReader and LuceneUtils
			        File file = new File(args[0]);
			    	IndexReader indexReader = IndexReader.open(file);
			    				
				// Write out document vectors
					for (cnt = 0; cnt < uT.cols; cnt++)
					{	
					String thePath = indexReader.document(cnt).get("path");
					outputStream.writeString(thePath);	
					float[] docVector = new float[Flags.dimension];
									
					for (int i = 0; i < Flags.dimension; i++)
					docVector[i] = (float) uT.value[i][cnt];
					docVector = VectorUtils.getNormalizedVector(docVector);
					
					for (int i = 0; i < Flags.dimension; ++i) {
						
						outputStream.writeInt(Float.floatToIntBits(docVector[i]));
					}
					}
					System.err.println("Wrote "+cnt+" document vectors to "+docFile);	
			    	outputStream.flush();
			    	outputStream.close();
					
			    	
}

}

