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
import java.lang.Integer;
import java.util.Arrays;
import java.util.logging.Logger;

import org.apache.lucene.index.*;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.FSDirectory;

import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorType;

/**
 * Generates document vectors incrementally.
 * 
 * @author Trevor Cohen, Dominic Widdows
 */
public class IncrementalDocVectors {
  private static final Logger logger = Logger.getLogger(
      IncrementalDocVectors.class.getCanonicalName());

  // TODO: Refactor to make more depend on flag config as appropriate.
  private FlagConfig flagConfig;
  
  private VectorType vectorType;
  private int dimension;

  private VectorStore termVectorData;
  private IndexReader indexReader;
  private String[] fieldsToIndex;
  private LuceneUtils lUtils;
  private String vectorFileName;

  private IncrementalDocVectors() {};

  /**
   * Creates incremental doc vectors, getting everything it needs from a
   * TermVectorsFromLucene object and a Lucene Index directory, and writing to a named file.
   * 
   * @param termVectorData Has all the information needed to create doc vectors.
   * @param flagConfig Any extra flag configurations
   * @param indexDir Directory of the Lucene Index used to generate termVectorData
   * @param fieldsToIndex String[] containing fields indexed when generating termVectorData
   * @param vectorStoreName Filename for the document vectors
   */
  public static void createIncrementalDocVectors(
      VectorStore termVectorData, FlagConfig flagConfig, String indexDir,
      String[] fieldsToIndex, String vectorStoreName) throws IOException {
    IncrementalDocVectors incrementalDocVectors = new IncrementalDocVectors();
    incrementalDocVectors.flagConfig = flagConfig;
    incrementalDocVectors.dimension = termVectorData.getDimension();
    incrementalDocVectors.vectorType = termVectorData.getVectorType();
    incrementalDocVectors.termVectorData = termVectorData;
    incrementalDocVectors.indexReader = IndexReader.open(FSDirectory.open(new File(indexDir)));
    incrementalDocVectors.fieldsToIndex = fieldsToIndex;
    incrementalDocVectors.vectorFileName = VectorStoreUtils.getStoreFileName(vectorStoreName, flagConfig);
    if (incrementalDocVectors.lUtils == null) {
      incrementalDocVectors.lUtils = new LuceneUtils(indexDir, flagConfig);
    }
    incrementalDocVectors.trainIncrementalDocVectors();
  }

  private void trainIncrementalDocVectors() throws IOException {
    int numdocs = indexReader.numDocs();

    // Open file and write headers.
    File vectorFile = new File(vectorFileName);
    String parentPath = vectorFile.getParent();
    if (parentPath == null) parentPath = "";
    FSDirectory fsDirectory = FSDirectory.open(new File(parentPath));
    IndexOutput outputStream = fsDirectory.createOutput(vectorFile.getName());

    VerbatimLogger.info("Writing vectors incrementally to file " + vectorFile + " ... ");

    // Write header giving number of dimension for all vectors.
    outputStream.writeString(VectorStoreWriter.generateHeaderString(flagConfig));

    // Iterate through documents.
    for (int dc = 0; dc < numdocs; dc++) {
      // Output progress counter.
      if ((dc > 0) && ((dc % 10000 == 0) || ( dc < 10000 && dc % 1000 == 0 ))) {
        VerbatimLogger.info("Processed " + dc + " documents ... ");
      }

      String docID = Integer.toString(dc); 
      // Use filename and path rather than Lucene index number for document vector.
      if (this.indexReader.document(dc).getField(flagConfig.getDocidfield()) != null) {
        docID = this.indexReader.document(dc).getField(flagConfig.getDocidfield()).stringValue();
        if (docID.length() == 0) {
          logger.warning("Empty document name!!! This will cause problems ...");
          logger.warning("Please set -docidfield to a nonempty field in your Lucene index.");
        }
      }

      Vector docVector = VectorFactory.createZeroVector(vectorType, dimension);

      for (String fieldName: fieldsToIndex) {
        TermFreqVector vex =
            indexReader.getTermFreqVector(dc, fieldName);

        if (vex != null) {
          // Get terms in document and term frequencies.
          String[] terms = vex.getTerms();
          int[] freqs = vex.getTermFrequencies();

          for (int b = 0; b < freqs.length; ++b) {
            String termString = terms[b];
            int freq = freqs[b];
            float localweight = freq;
            float globalweight = 1;
            float fieldweight = 1;


            if (flagConfig.getFieldweight()) {
              //field weight: 1/sqrt(number of terms in field)
              fieldweight = (float) (1/Math.sqrt(terms.length));
            }

            if (flagConfig.getTermweight().equals("logentropy")) {
              //local weighting: 1+ log (local frequency)
              localweight = new Double(1 + Math.log(localweight)).floatValue();
              Term term = new Term(fieldName, termString);
              globalweight = globalweight * lUtils.getEntropy(term);
            }
            else 
              if (flagConfig.getTermweight().equals("idf")) {
                Term term = new Term(fieldName, termString);
                globalweight = lUtils.getIDF(term);
              }	

            // Add contribution from this term, excluding terms that
            // are not represented in termVectorData.
            try {
              Vector termVector = termVectorData.getVector(termString);
              if (termVector != null && termVector.getDimension() > 0) {
                docVector.superpose(termVector, localweight * globalweight * fieldweight, null);
              }
            } catch (NullPointerException npe) {
              // Don't normally print anything - too much data!
              logger.finest("term " + termString + " not represented");
            }
          }
        }
      }
      // All fields in document have been processed.
      // Write out documentID and normalized vector.
      outputStream.writeString(docID);
      docVector.normalize();
      docVector.writeToLuceneStream(outputStream);

    } // Finish iterating through documents.

    VerbatimLogger.info("Finished writing vectors.\n");
    outputStream.flush();
    outputStream.close();
    fsDirectory.close();
  }

  public static void main(String[] args) throws Exception {
    FlagConfig flagConfig = FlagConfig.getFlagConfig(args);
    args = flagConfig.remainingArgs;

    // Only two arguments should remain, the path to the Lucene index.
    if (args.length != 2) {
      throw (new IllegalArgumentException("After parsing command line flags, there were "
          + args.length + " arguments, instead of the expected 2."));
    }

    String vectorFile = args[0].replaceAll("\\.bin","")+"_docvectors.bin";
    VectorStoreRAM vsr = new VectorStoreRAM(flagConfig);
    vsr.initFromFile(args[0]);

    logger.info("Minimum frequency = " + flagConfig.getMinfrequency());
    logger.info("Maximum frequency = " + flagConfig.getMaxfrequency());
    logger.info("Number non-alphabet characters = " + flagConfig.getMaxnonalphabetchars());
    logger.info("Contents fields are: " + Arrays.toString(flagConfig.getContentsfields()));

    createIncrementalDocVectors(vsr, flagConfig, args[1], flagConfig.getContentsfields(), vectorFile);
  }
}
