package pitt.search.semanticvectors;

import org.apache.lucene.document.Field.TermVector;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.FSDirectory;
import java.io.File;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.logging.Logger;

import ch.akuhn.edu.mit.tedlab.*;

/**
 * Interface to Adrian Kuhn and David Erni's implementation of SVDLIBJ, a native Java version
 * of Doug Rhodes' SVDLIBC, which was in turn based on SVDPACK, by Michael Berry, Theresa Do,
 * Gavin O'Brien, Vijay Krishna and Sowmini Varadhan.
 *
 * This class will produce two files, svd_termvectors.bin and svd_docvectors.bin from a Lucene index
 * Command line arguments are consistent with the rest of the Semantic Vectors Package
 */
public class LSA {
  private static final Logger logger = Logger.getLogger(LSA.class.getCanonicalName());
  
  static boolean le = false;
  static boolean moreTerms = false;
  static String[] theTerms;
  static String[] theDocs;

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
   * <br> -maxnonalphabetchars [number non-alphabet characters (-1 for any number)]
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
        + "\n  -maxnonalphabetchars [number non-alphabet characters (-1 for any number)]";

    System.out.println(usageMessage);
  }

  /* Converts a dense matrix to a sparse one (without affecting the dense one) */
  static SMat smatFromIndex(String fileName) throws Exception {
    SMat S;
    
    //initiate IndexReader and LuceneUtils
    File file = new File(fileName);
    IndexReader indexReader = IndexReader.open(FSDirectory.open(file));
    LuceneUtils.compressIndex(fileName);
    LuceneUtils lUtils = new LuceneUtils(fileName);
    
    //calculate norm of each doc vector so as to normalize these before SVD
    int[][] index;
    String[] desiredFields = Flags.contentsfields;
    int nonzerovals = 0;
    
    TermEnum terms = indexReader.terms();
    int tc = 0;
    while(terms.next()){
      if (lUtils.termFilter(terms.term(), desiredFields, 
          Flags.minfrequency, Flags.maxfrequency, Flags.maxnonalphabetchars))
        tc++;
    }

    logger.info("There are " + tc + " terms (and " + indexReader.numDocs() + " docs)");
    
    if (tc > indexReader.numDocs())
    {
    //create term by document matrix
    moreTerms = true;
    theTerms = new String[tc];
    index = new int[tc][];
    
    terms = indexReader.terms();
    tc = 0;
   
    logger.info("Populating sparse matrix");
    logger.info("More terms than documents");

    while(terms.next()){
     
      org.apache.lucene.index.Term term = terms.term();
      if (lUtils.termFilter(term, desiredFields,
          Flags.minfrequency, Flags.maxfrequency, Flags.maxnonalphabetchars)) {
        theTerms[tc] = term.text();
        
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
        {
        	index[tc][count++] = td.doc();
         }

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
    int nn= 0;

    while(terms.next()){

      org.apache.lucene.index.Term term = terms.term();
      if (lUtils.termFilter(term, desiredFields,
          Flags.minfrequency, Flags.maxfrequency, Flags.maxnonalphabetchars)) {
        TermDocs td = indexReader.termDocs(term);
        S.pointr[tc] = nn;  // index of first non-zero entry (document) of each column (term)

        while (td.next()) {
          /** public int[] pointr; For each col (plus 1), index of
            *  first non-zero entry.  we'll represent the matrix as a
            *  term x document matrix such that terms are rows
            *  (otherwise it would be difficult to extract this
            *  information from the lucene index)
            */

          float value = td.freq();

          /**
           * if log-entropy weighting is to be used
           */

          if (le) { 
            float entropy = lUtils.getEntropy(term);
            float log1plus = (float) Math.log10(1+value);
            value = entropy*log1plus;
          }

          S.rowind[nn] = td.doc();  // set row index to document number
          S.value[nn] = value;  // set value to frequency (with/without weighting)
          nn++;
        }
        tc++;
      }
    }
    S.pointr[S.cols] = S.vals;
  
    }
    else
    {
    //more documents than terms
    	logger.info("More documents than terms");

       index = new int[indexReader.numDocs()][];	
    	
    Hashtable<String, Integer> termIndex = new Hashtable<String, Integer>();
    int numdocs = indexReader.numDocs();
    terms = indexReader.terms();
    int cnt = 0;
    
    //assign column value to each unique term
    while (terms.next())
    {
    	Term term = terms.term();
    	 if (lUtils.termFilter(term, desiredFields,
    	            Flags.minfrequency, Flags.maxfrequency, Flags.maxnonalphabetchars)
    	     && ! (termIndex.containsKey(term.text()))        
    	 	) {
    	          termIndex.put(term.text(), new Integer(cnt++));
    	 		}
    }
    
    //record terms for writing later
    theTerms = new String[termIndex.size()];
    Enumeration<String> enume = termIndex.keys();
  
    while (enume.hasMoreElements())
    {
    	String next = enume.nextElement();
    	theTerms[termIndex.get(next).intValue()] = next;
    }
    
    
    logger.info("Measuring sparse matrix");
	
    
    for (int doc = 0; doc < numdocs; doc ++)
    {	
    
    	
    	if (doc % 10000 == 0)
    		logger.info("Document number "+doc);
    	
    	for (String field:desiredFields)
    	{
          
          /**
           * create matrix of nonzero indices
           */
    		 
    	  TermFreqVector termFreqVector = indexReader.getTermFreqVector(doc, field);
    	  if (termFreqVector == null) continue;
    	  String[] docTerms = termFreqVector.getTerms();
    	  
          int count =0;
          for (int x=0; x < docTerms.length; x++)
          { 
        	if (termIndex.containsKey(docTerms[x])) 
        	{
        	count ++;
            nonzerovals++;}
          }
          
          index[doc] = new int[count];

          /**
           * fill in matrix of nonzero indices
           */
 
          count = 0;
          for (int x=0; x < docTerms.length; x++)
          {
        	  if (termIndex.containsKey(docTerms[x]))
        	  index[doc][count++] = termIndex.get(docTerms[x]).intValue(); 
          }

         
        }
      }
    

    /**
     * initialize "SVDLIBJ" sparse data structure
     */

    S = new SMat(tc, indexReader.numDocs(), nonzerovals);

    /**
     * populate "SVDLIBJ" sparse data structure
     */

    
    logger.info("Filling sparse matrix");
	
    
    int nn= 0;

    for (int doc = 0; doc < numdocs; doc ++)
    {	
    	if (doc % 10000 == 0)
    	logger.info("Document number "+doc);

    	
    	for (String field:desiredFields)
    	{
          	 
    	  TermFreqVector termFreqVector = indexReader.getTermFreqVector(doc, field);
    	  if (termFreqVector == null) continue;
    	  String[] docTerms = termFreqVector.getTerms();
    	  int[] docFreqs = termFreqVector.getTermFrequencies();

    	  for (int x=0; x < docTerms.length; x++)
    	  if (termIndex.containsKey(docTerms[x])) 
      	{
    	     S.pointr[doc] = nn;  // index of first non-zero entry (document) of each column (term)

          /** public int[] pointr; For each col (plus 1), index of
            *  first non-zero entry.  we'll represent the matrix as a
            *  document x term matrix such that documents are rows
            */

          float value = docFreqs[x];

          /**
           * if log-entropy weighting is to be used
           */

          if (le) { 
            float entropy = lUtils.getEntropy(new Term(field,docTerms[x]));
            float log1plus = (float) Math.log10(1+value);
            value = entropy*log1plus;
          }

          S.rowind[nn] = termIndex.get(docTerms[x]).intValue();  // set row index to term number
          S.value[nn] = value;  // set value to frequency (with/without weighting)
          nn++;
        }
        
      }
    }
    S.pointr[S.cols] = S.vals;

    
    }
    

    return S;
  }

  public static void main(String[] args) throws Exception {
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

    logger.info("Dimension = " + Flags.dimension);
    logger.info("Minimum frequency = " + Flags.minfrequency);
    logger.info("Maximum frequency = " + Flags.maxfrequency);
    logger.info("Number non-alphabet characters = " + Flags.maxnonalphabetchars);

    if (Flags.termweight.equals("logentropy")) le = true;
    else le = false;
    
    if (le)
      logger.info("Term weighting: log-entropy");

    SMat A = smatFromIndex(args[0]);
    Svdlib svd = new Svdlib();

    logger.info("Starting SVD using algorithm LAS2");

    SVDRec svdR = svd.svdLAS2A(A, Flags.dimension);
    DMat vT = svdR.Vt;
    DMat uT = svdR.Ut;

    // Open file and write headers.
    String termFile = "svd_termvectors.bin";
    FSDirectory fsDirectory = FSDirectory.open(new File("."));
    IndexOutput outputStream = fsDirectory.createOutput(termFile);
    
    logger.info("Write vectors incrementally to file " + termFile);

    // Write header giving number of dimensions for all vectors.
    outputStream.writeString("-dimensions");
    outputStream.writeInt(Flags.dimension);

   
    if (moreTerms)
    {
    int cnt;
    // Write out term vectors
    for (cnt = 0; cnt < vT.cols; cnt++) {
      outputStream.writeString(theTerms[cnt]);

      float[] termVector = new float[Flags.dimension];

      for (int i = 0; i < Flags.dimension; i++)
        termVector[i] = (float) vT.value[i][cnt];
      termVector = VectorUtils.getNormalizedVector(termVector);

      for (int i = 0; i < Flags.dimension; ++i) {
        outputStream.writeInt(Float.floatToIntBits(termVector[i]));
      }
    }

    logger.info("Wrote "+cnt+" term vectors to "+termFile);
    outputStream.flush();
    outputStream.close();

    /*
     * Write document vectors
     */
    // Open file and write headers.
    String docFile = "svd_docvectors.bin";
    outputStream = fsDirectory.createOutput(docFile);
    float[] tmpVector = new float[Flags.dimension];
    logger.info("Write vectors incrementally to file " + docFile);

    // Write header giving number of dimensions for all vectors.
    outputStream.writeString("-dimensions");
    outputStream.writeInt(Flags.dimension);

    // initilize IndexReader and LuceneUtils
    File file = new File(args[0]);
    IndexReader indexReader = IndexReader.open(FSDirectory.open(file));

    // Write out document vectors
    for (cnt = 0; cnt < uT.cols; cnt++) {
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
    logger.info("Wrote "+cnt+" document vectors to "+docFile);
    outputStream.flush();
    outputStream.close();
    }
    else 
    { //more docs than terms
        int cnt;
        // Write out term vectors
        for (cnt = 0; cnt < uT.cols; cnt++) {
          outputStream.writeString(theTerms[cnt]);

          float[] termVector = new float[Flags.dimension];

          for (int i = 0; i < Flags.dimension; i++)
            termVector[i] = (float) uT.value[i][cnt];
          termVector = VectorUtils.getNormalizedVector(termVector);

          for (int i = 0; i < Flags.dimension; ++i) {
            outputStream.writeInt(Float.floatToIntBits(termVector[i]));
          }
        }

        logger.info("Wrote "+cnt+" term vectors to "+termFile);
        outputStream.flush();
        outputStream.close();

        /*
         * Write document vectors
         */
        // Open file and write headers.
        String docFile = "svd_docvectors.bin";
        outputStream = fsDirectory.createOutput(docFile);
        float[] tmpVector = new float[Flags.dimension];
        logger.info("Write vectors incrementally to file " + docFile);

        // Write header giving number of dimensions for all vectors.
        outputStream.writeString("-dimensions");
        outputStream.writeInt(Flags.dimension);

        // initilize IndexReader and LuceneUtils
        File file = new File(args[0]);
        IndexReader indexReader = IndexReader.open(FSDirectory.open(file));

        // Write out document vectors
        for (cnt = 0; cnt < vT.cols; cnt++) {
          String thePath = indexReader.document(cnt).get("path");
          outputStream.writeString(thePath);
          float[] docVector = new float[Flags.dimension];

          for (int i = 0; i < Flags.dimension; i++)
            docVector[i] = (float) vT.value[i][cnt];
          docVector = VectorUtils.getNormalizedVector(docVector);

          for (int i = 0; i < Flags.dimension; ++i) {

            outputStream.writeInt(Float.floatToIntBits(docVector[i]));
          }
        }
        logger.info("Wrote "+cnt+" document vectors to "+docFile);
        outputStream.flush();
        outputStream.close();
        }
    }
}
