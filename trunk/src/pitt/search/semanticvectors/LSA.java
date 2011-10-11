package pitt.search.semanticvectors;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.FSDirectory;

import pitt.search.semanticvectors.vectors.RealVector;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorUtils;

import java.io.File;
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
  static String[] theTerms;

  /**
   * Prints the following usage message:
   * <code>
   * <br> LSA class in package pitt.search.semanticvectors
   * <br> Usage: java pitt.search.semanticvectors.LSA PATH_TO_LUCENE_INDEX
   * <br> LSA creates svd_termvectors and svd_docvectors files in local directory.
   * <br> Other parameters that can be changed include vector length,
   * <br>     (number of dimension), seed length (number of non-zero
   * <br>     entries in basic vectors) and minimum term frequency.
   * <br>
   * <br> To change these use the following command line arguments:
   * <br> -dimension [number of dimension]
   * <br> -minfrequency [minimum term frequency]
   * <br> -maxnonalphabetchars [number non-alphabet characters (-1 for any number)]
   * </code>
   */
  public static void usage() {
    String usageMessage = "\nLSA class in package pitt.search.semanticvectors"
        + "\nUsage: java pitt.search.semanticvectors.LSA PATH_TO_LUCENE_INDEX"
        + "\nBuildIndex creates svd_termvectors and svd_docvectors files in local directory."
        + "\nOther parameters that can be changed include vector length,"
        + "\n    (number of dimension), seed length (number of non-zero"
        + "\n    entries in basic vectors), and minimum term frequency."
        + "\nTo change these use the command line arguments "
        + "\n  -dimension [number of dimension]"
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

    TermEnum terms = indexReader.terms();
    int tc = 0;
    while(terms.next()){
      if (lUtils.termFilter(terms.term(), desiredFields,
          Flags.minfrequency, Flags.maxfrequency, Flags.maxnonalphabetchars))
        tc++;
    }

    logger.info("There are " + tc + " terms (and " + indexReader.numDocs() + " docs)");
    theTerms = new String[tc];
    index = new int[tc][];

    terms = indexReader.terms();
    tc = 0;
    int nonzerovals = 0;

    while(terms.next()){
      org.apache.lucene.index.Term term = terms.term();
      if (lUtils.termFilter(term, desiredFields,
          Flags.minfrequency, Flags.maxfrequency, Flags.maxnonalphabetchars)) {
        theTerms[tc] = term.text();

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
      if (lUtils.termFilter(term, desiredFields,
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
    float[] tmpVector = new float[Flags.dimension];

    logger.info("Write vectors incrementally to file " + termFile);

    // Write header giving number of dimension for all vectors.
    outputStream.writeString("-dimension " + Flags.dimension + " -vectortype real");

    int cnt;
    // Write out term vectors
    for (cnt = 0; cnt < vT.cols; cnt++) {
      outputStream.writeString(theTerms[cnt]);

      Vector termVector = VectorFactory.createZeroVector(Flags.vectortype, Flags.dimension);

      float[] tmp = new float[Flags.dimension];
      for (int i = 0; i < Flags.dimension; i++)
        tmp[i] = (float) vT.value[i][cnt];
      termVector = new RealVector(tmp);
      termVector.normalize();

      termVector.writeToLuceneStream(outputStream);
    }

    logger.info("Wrote "+cnt+" term vectors to "+termFile);
    outputStream.flush();
    outputStream.close();

    // Write document vectors.
    // Open file and write headers.
    String docFile = "svd_docvectors.bin";
    outputStream = fsDirectory.createOutput(docFile);
    tmpVector = new float[Flags.dimension];
    logger.info("Write vectors incrementally to file " + docFile);

    // Write header giving number of dimension for all vectors.
    outputStream.writeString("-dimension");
    outputStream.writeInt(Flags.dimension);

    // initilize IndexReader and LuceneUtils
    File file = new File(args[0]);
    IndexReader indexReader = IndexReader.open(FSDirectory.open(file));

    // Write out document vectors
    for (cnt = 0; cnt < uT.cols; cnt++) {
      String thePath = indexReader.document(cnt).get("path");
      outputStream.writeString(thePath);
      float[] tmp = new float[Flags.dimension];

      for (int i = 0; i < Flags.dimension; i++)
        tmp[i] = (float) uT.value[i][cnt];
      RealVector docVector = new RealVector(tmp);

      docVector.writeToLuceneStream(outputStream);
    }

    logger.info("Wrote "+cnt+" document vectors to "+docFile);
    outputStream.flush();
    outputStream.close();
  }
}
