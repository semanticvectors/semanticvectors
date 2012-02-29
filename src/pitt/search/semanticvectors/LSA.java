package pitt.search.semanticvectors;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.FSDirectory;

import pitt.search.semanticvectors.vectors.RealVector;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;

import java.io.File;
import java.io.IOException;
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

  public static String usageMessage = "\nLSA class in package pitt.search.semanticvectors"
        + "\nUsage: java pitt.search.semanticvectors.LSA PATH_TO_LUCENE_INDEX"
        + "\nBuildIndex creates svd_termvectors and svd_docvectors files in local directory."
        + "\nOther parameters that can be changed include vector length,"
        + "\n    (number of dimension), seed length (number of non-zero"
        + "\n    entries in basic vectors), and minimum term frequency."
        + "\nTo change these use the command line arguments "
        + "\n  -dimension [number of dimension]"
        + "\n  -minfrequency [minimum term frequency]"
        + "\n  -maxnonalphabetchars [number non-alphabet characters (-1 for any number)]";

  private boolean le = false;
  private String[] termList;
  private IndexReader indexReader;
  private LuceneUtils lUtils;
  private int numDocs;
  
  /**
   * Basic constructor that tries to check up front that resources are available and
   * configurations are consistent.
   * 
   * @param luceneIndexDir Relative path to directory containing Lucene index.
   */
  private LSA(String luceneIndexDir) {
    LuceneUtils.compressIndex(luceneIndexDir);
    
    try {
      this.indexReader = IndexReader.open(FSDirectory.open(new File(luceneIndexDir)));
      this.lUtils = new LuceneUtils(luceneIndexDir);
    } catch (IOException e) {
      e.printStackTrace();
    }
    
    // Find the number of docs, and if greater than dimension, set the dimension
    // to be this number.
    this.numDocs = indexReader.numDocs();
    if (Flags.dimension > numDocs) {
      logger.warning("Dimension for SVD cannot be greater than number of documents ... "
          + "Setting dimension to " + numDocs);
      Flags.dimension = numDocs;
    }
    
    if (Flags.termweight.equals("logentropy")) {
      le = true;
      VerbatimLogger.info("Term weighting: log-entropy.\n");
    }
    
    // Log some of the basic properties. This could be altered to be more informative if
    // our users ever ask for different properties.
    VerbatimLogger.info("Set up LSA indexer.\n" +
    		"Dimension: " + Flags.dimension + " Minimum frequency = " + Flags.minfrequency
        + " Maximum frequency = " + Flags.maxfrequency
        + " Number non-alphabet characters = " + Flags.maxnonalphabetchars +  "\n");
  }

  /**
   * Converts the Lucene index into a sparse matrix.
   * Also populates termList as a side-effect.
   * 
   * @returns sparse term-document matrix in the format expected by SVD library
   */
  private SMat smatFromIndex() throws IOException {
    SMat S;
    
    // Calculate norm of each doc vector so as to normalize these before SVD.
    int[][] index;

    TermEnum terms = indexReader.terms();
    int tc = 0;
    while(terms.next()){
      if (lUtils.termFilter(terms.term(), Flags.contentsfields,
          Flags.minfrequency, Flags.maxfrequency, Flags.maxnonalphabetchars))
        tc++;
    }

    VerbatimLogger.info("There are " + tc + " terms (and " + indexReader.numDocs() + " docs).\n");
    termList = new String[tc];
    index = new int[tc][];

    terms = indexReader.terms();
    tc = 0;
    int nonzerovals = 0;

    while(terms.next()){
      org.apache.lucene.index.Term term = terms.term();
      if (lUtils.termFilter(term, Flags.contentsfields,
          Flags.minfrequency, Flags.maxfrequency, Flags.maxnonalphabetchars)) {
        termList[tc] = term.text();

        // Create matrix of nonzero indices.
        TermDocs td = indexReader.termDocs(term);
        int count =0;
        while (td.next()) {
          count++;
          nonzerovals++;
        }
        index[tc] = new int[count];

        // Fill in matrix of nonzero indices.
        td = indexReader.termDocs(term);
        count = 0;
        while (td.next()) {
          index[tc][count++] = td.doc();
        }
        tc++;	// Next term.
      }
    }

    // Initialize "SVDLIBJ" sparse data structure.
    S = new SMat(indexReader.numDocs(),tc, nonzerovals);

    // Populate "SVDLIBJ" sparse data structure.
    terms = indexReader.terms();
    tc = 0;
    int nn= 0;

    while (terms.next()) {
      org.apache.lucene.index.Term term = terms.term();
      if (lUtils.termFilter(term, Flags.contentsfields,
                            Flags.minfrequency, Flags.maxfrequency,
                            Flags.maxnonalphabetchars)) {
        TermDocs td = indexReader.termDocs(term);
        S.pointr[tc] = nn;  // Index of first non-zero entry (document) of each column (term).

        while (td.next()) {
          /** public int[] pointr; For each col (plus 1), index of
            *  first non-zero entry.  we'll represent the matrix as a
            *  document x term matrix such that terms are columns
            *  (otherwise it would be difficult to extract this
            *  information from the lucene index)
            */

          float value = td.freq();

          // Use log-entropy weighting if directed.
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

    return S;
  }

  public static void main(String[] args) throws IllegalArgumentException, IOException {
    try {
      args = Flags.parseCommandLineFlags(args);
    } catch (IllegalArgumentException e) {
      System.out.println(usageMessage);
      throw e;
    }
    if (!Flags.vectortype.equalsIgnoreCase("real")) {
      logger.warning("LSA is only supported for real vectors ... setting vectortype to 'real'.");
      
    }
    
    // Only one argument should remain, the path to the Lucene index.
    if (args.length != 1) {
      System.out.println(usageMessage);
      throw (new IllegalArgumentException("After parsing command line flags, there were " + args.length
                                          + " arguments, instead of the expected 1."));
    }
    
    // Create an instance of the LSA class.
    // TODO: given the more object oriented instantiation pattern, consider calling this class LSAIndexer.
    LSA lsaIndexer = new LSA(args[0]);
    SMat A = lsaIndexer.smatFromIndex();
    Svdlib svd = new Svdlib();

    VerbatimLogger.info("Starting SVD using algorithm LAS2 ...\n");

    SVDRec svdR = svd.svdLAS2A(A, Flags.dimension);
    DMat vT = svdR.Vt;
    DMat uT = svdR.Ut;

    // Open file and write headers.
    FSDirectory fsDirectory = FSDirectory.open(new File("."));
    IndexOutput outputStream = fsDirectory.createOutput(
        VectorStoreUtils.getStoreFileName(Flags.termvectorsfile));

    // Write header giving number of dimensions for all vectors and make sure type is real.
    outputStream.writeString("-dimension " + Flags.dimension + " -vectortype real");
    int cnt;
    // Write out term vectors
    for (cnt = 0; cnt < vT.cols; cnt++) {
      outputStream.writeString(lsaIndexer.termList[cnt]);
      Vector termVector = VectorFactory.createZeroVector(Flags.vectortype, Flags.dimension);

      float[] tmp = new float[Flags.dimension];
      for (int i = 0; i < Flags.dimension; i++)
        tmp[i] = (float) vT.value[i][cnt];
      termVector = new RealVector(tmp);
      termVector.normalize();

      termVector.writeToLuceneStream(outputStream);
    }
    outputStream.flush();
    outputStream.close();
    VerbatimLogger.info("Wrote " + cnt + " term vectors incrementally to file " + Flags.termvectorsfile + ".\n");

    // Write document vectors.
    // Open file and write headers.
    outputStream = fsDirectory.createOutput(VectorStoreUtils.getStoreFileName(Flags.docvectorsfile));

    // Write header giving number of dimensions for all vectors and make sure type is real.
    outputStream.writeString("-dimension " + Flags.dimension + " -vectortype real");
    File file = new File(args[0]);
    IndexReader indexReader = IndexReader.open(FSDirectory.open(file));

    // Write out document vectors
    for (cnt = 0; cnt < uT.cols; cnt++) {
      String thePath = indexReader.document(cnt).get(Flags.docidfield);
      outputStream.writeString(thePath);
      float[] tmp = new float[Flags.dimension];

      for (int i = 0; i < Flags.dimension; i++)
        tmp[i] = (float) uT.value[i][cnt];
      RealVector docVector = new RealVector(tmp);

      docVector.writeToLuceneStream(outputStream);
    }
    outputStream.flush();
    outputStream.close();
    VerbatimLogger.info("Wrote " + cnt + " document vectors incrementally to file "
                        + Flags.docvectorsfile + ". Done.\n");
  }
}
