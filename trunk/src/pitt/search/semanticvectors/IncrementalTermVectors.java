/**
   Copyright (c) 2008, Arizona State University.

   All rights reserved.

   Redistribution and use in source and binary forms, with or without
   modification, are permitted provided that the following conditions are
   met:

   * Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.

   * Redistributions in binary form must reproduce the above
   copyright notice, this list of conditions and the following
   disclaimer in the documentation and/or other materials provided
   with the distribution.

   * Neither the name of the University of Pittsburgh nor the names
   of its contributors may be used to endorse or promote products
   derived from this software without specific prior written
   permission.

   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
   A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
   CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
   EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
   PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
   PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
   LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
   NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
   SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
**/

package pitt.search.semanticvectors;


import java.io.File;
import java.io.IOException;
import org.apache.lucene.index.*;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.FSDirectory;

import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorType;

import java.util.Enumeration;
import java.util.logging.Logger;

/**
 * generates term vectors incrementally (i.e. one document at a time)
 * @author Trevor Cohen, Dominic Widdows
 */
public class IncrementalTermVectors implements VectorStore {
  /** Usage message printed if {@link #main} is called with ill-formed arguments. */
  public static String usageMessage = "\nIncrementalTermVectors class in package pitt.search.semanticvectors"
    + "\nUsage: java pitt.search.semanticvectors.IncrementalTermVectors [document vector file] [lucene index]"
    + "\nIncrementalTermVectors creates termvectors files in local directory from docvectors file."
    + "\nOther parameters that can be changed include vector type (real, binary, complex), "
    + "\n    vector length (number of dimensions), seed length (number of non-zero"
    + "\n    entries in basic vectors), minimum term frequency,"
    + "\n    and number of iterative training cycles."
    + "\nTo change these use the command line arguments "
    + "\n  -vectortype [real|binary|complex]"
    + "\n  -dimension [number of dimension]"
    + "\n  -seedlength [seed length]"
    + "\n  -minfrequency [minimum term frequency]"
    + "\n  -maxnonalphabetchars [number non-alphabet characters (-1 for any number)]"
    + "\n  -filternumbers [true or false]"
    + "\n  -trainingcycles [training cycles]"
    + "\n  -docindexing [incremental|inmemory|none] Switch between building doc vectors incrementally"
    + "\n        (requires positional index), all in memory (default case), or not at all";
  
  private static final Logger logger = Logger.getLogger(
      IncrementalTermVectors.class.getCanonicalName());
  
  // TODO: Refactor to use more of this.
  private FlagConfig flagConfig;
  
  private VectorType vectorType;
  private int dimension;
  
  private VectorStoreRAM termVectorData;
  private IndexReader indexReader;
  private String[] fieldsToIndex = null;
  private String luceneIndexDir;
  private LuceneUtils lUtils = null;
  private String docVectorFileName;
  
  @Override
  public VectorType getVectorType() { return vectorType; }
  
  @Override
  public int getDimension() { return dimension; }
  
  /**
   * Constructor that gets everything it needs from a
   * TermVectorsFromLucene object and writes to a named file.
   * @param luceneIndexDir Directory of the Lucene Index used to generate termVectorData
   * @param fieldsToIndex String[] containing fields indexed when generating termVectorData
   * @param docVectorFileName Filename containing the input document vectors
   */
  public IncrementalTermVectors(FlagConfig flagConfig,
      String luceneIndexDir, VectorType vectorType, int dimension,
      String[] fieldsToIndex, String docVectorFileName)
      throws IOException {
    this.flagConfig = flagConfig;
    this.vectorType = vectorType;
    this.dimension = dimension;
    this.indexReader = IndexReader.open(FSDirectory.open(new File(luceneIndexDir)));
    this.fieldsToIndex = fieldsToIndex;
    this.luceneIndexDir = luceneIndexDir;
    this.docVectorFileName = docVectorFileName;
    if (this.lUtils == null)
      this.lUtils = new LuceneUtils(flagConfig);
    createIncrementalTermVectorsFromLucene();
  }

  private void createIncrementalTermVectorsFromLucene() throws IOException {
    int numdocs = indexReader.numDocs();

    // Open file and write headers.
    File vectorFile = new File(docVectorFileName);
    String parentPath = vectorFile.getParent();
    if (parentPath == null) parentPath = "";
    FSDirectory fsDirectory = FSDirectory.open(new File(parentPath));
    IndexInput inputStream = fsDirectory.openInput(docVectorFileName.replaceAll(".*/", ""));

    logger.info("Read vectors incrementally from file " + vectorFile);

    // Read number of dimensions from document vectors.
    String header = inputStream.readString();
    FlagConfig.mergeWriteableFlagsFromString(header, flagConfig);

    logger.info("Opening index at " + luceneIndexDir);
    termVectorData = new VectorStoreRAM(flagConfig);
    TermEnum terms = this.indexReader.terms();
    int tc = 0;

    while(terms.next()){
      Term term = terms.term();

      // Skip terms that don't pass the filter.
      if (!lUtils.termFilter(terms.term(), fieldsToIndex,
          flagConfig.getMinfrequency(), flagConfig.getMaxfrequency(), flagConfig.getMaxnonalphabetchars()))
        continue;
      tc++;
      Vector termVector = VectorFactory.createZeroVector(vectorType, dimension);

      // Place each term vector in the vector store.
      termVectorData.putVector(term.text(), termVector);
    }
    VerbatimLogger.info("There are " + tc + " terms (and " + indexReader.numDocs() + " docs)");

    // Iterate through documents.
    for (int dc=0; dc < numdocs; dc++) {
      /* output progress counter */
      if (( dc % 10000 == 0 ) || ( dc < 10000 && dc % 1000 == 0 )) {
        VerbatimLogger.info(dc + " ... ");
      }

      int dcount = dc;
      Vector docVector = VectorFactory.createZeroVector(vectorType, dimension);

      try {
    	 /**
    	  * read ID for each document first 
    	  */
    	 String docID = inputStream.readString(); 
         docVector.readFromLuceneStream(inputStream);
      }
      catch (Exception e) {
    	System.out.println("Doc vectors less than total number of documents");
        dc = numdocs + 1;
        continue;
      }

      for (String fieldName: fieldsToIndex) {
        TermFreqVector vex = indexReader.getTermFreqVector(dcount, fieldName);

        if (vex !=null) {
          // Get terms in document and term frequencies.
          String[] docterms = vex.getTerms();
          int[] freqs = vex.getTermFrequencies();

          //For each term in doc (and its frequency)
          for (int b = 0; b < freqs.length; ++b) {
            String term = docterms[b];
            int freq = freqs[b];
            Vector termVector = null;

            try{
              termVector = termVectorData.getVector(term);
            } catch (NullPointerException npe) {
              // Don't normally print anything - too much data!
              logger.finest("term " + term + " not represented");
            }
            // Exclude terms that are not represented in termVectorData
            if (termVector != null && termVector.getDimension() > 0) {
              termVector.superpose(docVector, freq, null);
            }
          }
        }

      }
    } // Finish iterating through documents.

    // Normalize vectors
    Enumeration<ObjectVector> allVectors = termVectorData.getAllVectors();
    while (allVectors.hasMoreElements()) {
      ObjectVector obVec = allVectors.nextElement();
      Vector termVector = obVec.getVector();  
      termVector.normalize();
      obVec.setVector(termVector);
    }

    inputStream.close();
    indexReader.close();
  }
  
  // Basic VectorStore interface methods implemented through termVectors.
  public Vector getVector(Object term) {
    return termVectorData.getVector(term);
  }

  public Enumeration<ObjectVector> getAllVectors() {
    return termVectorData.getAllVectors();
  }

  public int getNumVectors() {
    return termVectorData.getNumVectors();
  }

  public static void main(String[] args) throws IOException {
    FlagConfig flagConfig;
    try {
      flagConfig = FlagConfig.getFlagConfig(args);
      args = flagConfig.remainingArgs;
    } catch (IllegalArgumentException e) {
      System.err.println(usageMessage);
      throw e;
    }

    // Only one argument should remain, the path to the Lucene index.
    if (args.length != 2) {
      System.err.println(usageMessage);
      throw (new IllegalArgumentException("After parsing command line flags, there were " + args.length
                                          + " arguments, instead of the expected 2."));
    }

    String vectorFile = args[0];
    String luceneIndex = args[1];

    VectorStore termVectors = new IncrementalTermVectors(
        flagConfig,
        luceneIndex, flagConfig.getVectortype(), flagConfig.getDimension(),
        flagConfig.getContentsfields(), vectorFile);
    VectorStoreWriter.writeVectors("incremental_termvectors.bin", flagConfig, termVectors);
  }
}
