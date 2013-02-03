package pitt.search.semanticvectors;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.FSDirectory;

import pitt.search.semanticvectors.vectors.RealVector;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorType;

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
        + "\nUsage: java pitt.search.semanticvectors.LSA -luceneindexpath PATH_TO_LUCENE_INDEX"
        + "\nBuildIndex creates svd_termvectors and svd_docvectors files in local directory."
        + "\nOther parameters that can be changed include vector length,"
        + "\n    (number of dimension), seed length (number of non-zero"
        + "\n    entries in basic vectors), and minimum term frequency."
        + "\nTo change these use the command line arguments "
        + "\n  -dimension [number of dimension]"
        + "\n  -minfrequency [minimum term frequency]"
        + "\n  -maxnonalphabetchars [number non-alphabet characters (-1 for any number)]"
        + "\n  -filternumbers [true or false]";

  private FlagConfig flagConfig;
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
  private LSA(String luceneIndexDir, FlagConfig flagConfig) {
    this.flagConfig = flagConfig;
    LuceneUtils.compressIndex(luceneIndexDir);
    
    try {
      this.indexReader = IndexReader.open(FSDirectory.open(new File(luceneIndexDir)));
      this.lUtils = new LuceneUtils(flagConfig);
    } catch (IOException e) {
      e.printStackTrace();
    }
    
    // Find the number of docs, and if greater than dimension, set the dimension
    // to be this number.
    this.numDocs = indexReader.numDocs();
    if (flagConfig.dimension() > numDocs) {
      logger.warning("Dimension for SVD cannot be greater than number of documents ... "
          + "Setting dimension to " + numDocs);
      flagConfig.setDimension(numDocs);
    }

    if (flagConfig.termweight().equals("logentropy")) {
      VerbatimLogger.info("Term weighting: log-entropy.\n");
    }

    // Log some of the basic properties. This could be altered to be more informative if
    // our users ever ask for different properties.
    VerbatimLogger.info("Set up LSA indexer.\n" +
    		"Dimension: " + flagConfig.dimension() + " Minimum frequency = " + flagConfig.minfrequency()
        + " Maximum frequency = " + flagConfig.maxfrequency()
        + " Number non-alphabet characters = " + flagConfig.maxnonalphabetchars() +  "\n");
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
      if (lUtils.termFilter(terms.term()))
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
      if (lUtils.termFilter(term)) {
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
      if (lUtils.termFilter(term)) {
        TermDocs td = indexReader.termDocs(term);
        S.pointr[tc] = nn;  // Index of first non-zero entry (document) of each column (term).

        while (td.next()) {
          /** public int[] pointr; For each col (plus 1), index of
            *  first non-zero entry.  we'll represent the matrix as a
            *  document x term matrix such that terms are columns
            *  (otherwise it would be difficult to extract this
            *  information from the lucene index)
            */
          float value = td.freq() * lUtils.getGlobalTermWeight(term);

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
    FlagConfig flagConfig;
    try {
      flagConfig = FlagConfig.getFlagConfig(args);
      args = flagConfig.remainingArgs;
    } catch (IllegalArgumentException e) {
      System.out.println(usageMessage);
      throw e;
    }
    if (flagConfig.vectortype() != VectorType.REAL) {
      logger.warning("LSA is only supported for real vectors ... setting vectortype to 'real'."); 
    }
    
    if (flagConfig.luceneindexpath().isEmpty()) {
      throw (new IllegalArgumentException("-luceneindexpath must be set."));
    }
    
    // Create an instance of the LSA class.
    // TODO: given the more object oriented instantiation pattern, consider calling this class LSAIndexer.
    LSA lsaIndexer = new LSA(flagConfig.luceneindexpath(), flagConfig);
    SMat A = lsaIndexer.smatFromIndex();
    Svdlib svd = new Svdlib();

    VerbatimLogger.info("Starting SVD using algorithm LAS2 ...\n");

    SVDRec svdR = svd.svdLAS2A(A, flagConfig.dimension());
    DMat vT = svdR.Vt;
    DMat uT = svdR.Ut;

    // Open file and write headers.
    FSDirectory fsDirectory = FSDirectory.open(new File("."));
    IndexOutput outputStream = fsDirectory.createOutput(
        VectorStoreUtils.getStoreFileName(flagConfig.termvectorsfile(), flagConfig));

    // Write header giving number of dimensions for all vectors and make sure type is real.
    outputStream.writeString(VectorStoreWriter.generateHeaderString(flagConfig));
    int cnt;
    // Write out term vectors
    for (cnt = 0; cnt < vT.cols; cnt++) {
      outputStream.writeString(lsaIndexer.termList[cnt]);
      Vector termVector = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());

      float[] tmp = new float[flagConfig.dimension()];
      for (int i = 0; i < flagConfig.dimension(); i++)
        tmp[i] = (float) vT.value[i][cnt];
      termVector = new RealVector(tmp);
      termVector.normalize();

      termVector.writeToLuceneStream(outputStream);
    }
    outputStream.flush();
    outputStream.close();
    VerbatimLogger.info(
        "Wrote " + cnt + " term vectors incrementally to file " + flagConfig.termvectorsfile() + ".\n");

    // Write document vectors.
    // Open file and write headers.
    outputStream = fsDirectory.createOutput(
        VectorStoreUtils.getStoreFileName(flagConfig.docvectorsfile(), flagConfig));

    // Write header giving number of dimensions for all vectors and make sure type is real.
    outputStream.writeString(VectorStoreWriter.generateHeaderString(flagConfig));
    File file = new File(flagConfig.luceneindexpath());
    IndexReader indexReader = IndexReader.open(FSDirectory.open(file));

    // Write out document vectors
    for (cnt = 0; cnt < uT.cols; cnt++) {
      String thePath = indexReader.document(cnt).get(flagConfig.docidfield());
      outputStream.writeString(thePath);
      float[] tmp = new float[flagConfig.dimension()];

      for (int i = 0; i < flagConfig.dimension(); i++)
        tmp[i] = (float) uT.value[i][cnt];
      RealVector docVector = new RealVector(tmp);
      docVector.normalize();
      
      docVector.writeToLuceneStream(outputStream);
    }
    outputStream.flush();
    outputStream.close();
    VerbatimLogger.info("Wrote " + cnt + " document vectors incrementally to file "
                        + flagConfig.docvectorsfile() + ". Done.\n");
  }
}
