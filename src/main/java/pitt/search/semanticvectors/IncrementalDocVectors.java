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
import java.util.Arrays;
import java.util.logging.Logger;

import org.apache.lucene.index.*;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

import pitt.search.semanticvectors.utils.VerbatimLogger;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;

/**
 * Generates document vectors incrementally, writing each document vector to disk after
 * it is created. This saves memory compared with the implementation in {@link DocVectors}.
 * The training procedure still iterates through all the documents in the Lucene index,
 * so currently this class is purely an optimization, not an implementation of
 * incremental indexing in the sense of being able to add extra documents later after
 * an initial model has been built.   
 *
 * @author Trevor Cohen, Dominic Widdows
 */
public class IncrementalDocVectors {
  private static final Logger logger = Logger.getLogger(
      IncrementalDocVectors.class.getCanonicalName());

  private FlagConfig flagConfig;
  private VectorStore termVectorData;
  private LuceneUtils luceneUtils;

  private IncrementalDocVectors() {};

  /**
   * Creates incremental doc vectors, getting everything it needs from a
   * TermVectorsFromLucene object and a Lucene Index directory, and writing to a named file.
   * 
   * @param termVectorData Vector store containing terms create doc vectors.
   * @param flagConfig Any extra flag configurations
   * @param luceneUtils Lucene Utils used for reading Lucene index
   */
  public static void createIncrementalDocVectors(
      VectorStore termVectorData, FlagConfig flagConfig, LuceneUtils luceneUtils) 
          throws IOException {
    IncrementalDocVectors incrementalDocVectors = new IncrementalDocVectors();
    incrementalDocVectors.flagConfig = flagConfig;
    incrementalDocVectors.termVectorData = termVectorData;
    incrementalDocVectors.luceneUtils = luceneUtils;
    incrementalDocVectors.trainIncrementalDocVectors();
  }

  private void trainIncrementalDocVectors() throws IOException {
    int numdocs = luceneUtils.getNumDocs();

    // Open file and write headers.
    File vectorFile = new File(
        VectorStoreUtils.getStoreFileName(flagConfig.docvectorsfile(), flagConfig));
    String parentPath = vectorFile.getParent();
    if (parentPath == null) parentPath = "";
    FSDirectory fsDirectory = FSDirectory.open(new File(parentPath));
    IndexOutput outputStream = fsDirectory.createOutput(vectorFile.getName(), IOContext.DEFAULT);

    VerbatimLogger.info("Writing vectors incrementally to file " + vectorFile + " ... ");

    // Write header giving number of dimension for all vectors.
    outputStream.writeString(VectorStoreWriter.generateHeaderString(flagConfig));
    
    // Iterate through documents.
    for (int dc = 0; dc < numdocs; dc++) {
      // Output progress counter.
      if ((dc > 0) && ((dc % 10000 == 0) || ( dc < 10000 && dc % 1000 == 0 ))) {
        VerbatimLogger.info("Processed " + dc + " documents ... ");
      }

      // Get filename and path to be used as document vector ID, defaulting to doc number only if
      // docidfield is not pupoulated.
      String docID = Integer.toString(dc);
      if (this.luceneUtils.getDoc(dc).getField(flagConfig.docidfield()) != null) {
        docID = this.luceneUtils.getDoc(dc).getField(flagConfig.docidfield()).stringValue();
        if (docID.length() == 0) {
          logger.severe("Empty document name!!! This will cause problems ...");
          logger.severe("Please set -docidfield to a nonempty field in your Lucene index.");
          continue;
        }
      }

      Vector docVector = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());
      
      for (String fieldName: flagConfig.contentsfields()) {
        Terms terms = luceneUtils.getTermVector(dc, fieldName);

        if (terms == null) {
          VerbatimLogger.fine(
              String.format(
                  "When building document vectors, no term vector for field: '%s' in document %d.",
                  fieldName, dc));
          continue;
        }

        TermsEnum tmp = null;
        TermsEnum termsEnum = terms.iterator(tmp);
        BytesRef bytes;
        while ((bytes = termsEnum.next()) != null) {
          Term term = new Term(fieldName, bytes);
          String termString = term.text();
          DocsEnum docs = termsEnum.docs(null, null);
          docs.nextDoc();
	      int freq = docs.freq();
	     
	      try {
            Vector termVector = termVectorData.getVector(termString);
            if (termVector != null && termVector.getDimension() > 0) {
              float localweight = luceneUtils.getLocalTermWeight(freq);
              float globalweight = luceneUtils.getGlobalTermWeight(new Term(fieldName,termString));
              float fieldweight = 1;

              if (flagConfig.fieldweight()) {
                //field weight: 1/sqrt(number of terms in field)
                fieldweight = (float) (1/Math.sqrt(terms.size()));
              }

              // Add contribution from this term, excluding terms that
              // are not represented in termVectorData.
              docVector.superpose(termVector, localweight * globalweight * fieldweight, null);
            }
          } catch (NullPointerException npe) {
            // Don't normally print anything - too much data!
            logger.finest("term " + termString + " not represented");
          }
        }
      }

      if (docVector.isZeroVector()) {
        logger.severe(String.format(
            "Document vector is zero for document '%s'. This probably means that none of " +
                "the -contentsfields were populated. this is a bad sign and should be investigated.",
            docID));
        //nonetheless, write out a zero document vector so the document order in the document store
        //remains consistent with the Lucene index
      }

      // All fields in document have been processed. Write out documentID and normalized vector.
      docVector.normalize();
      outputStream.writeString(docID);
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

    // Only two arguments should remain, the name of the initial term vectors and the
    // path to the Lucene index.
    if (args.length != 2) {
      throw (new IllegalArgumentException("After parsing command line flags, there were "
          + args.length + " arguments, instead of the expected 2."));
    }

    VectorStoreRAM vsr = new VectorStoreRAM(flagConfig);
    vsr.initFromFile(args[0]);

    logger.info("Minimum frequency = " + flagConfig.minfrequency());
    logger.info("Maximum frequency = " + flagConfig.maxfrequency());
    logger.info("Number non-alphabet characters = " + flagConfig.maxnonalphabetchars());
    logger.info("Contents fields are: " + Arrays.toString(flagConfig.contentsfields()));

    createIncrementalDocVectors(vsr, flagConfig, new LuceneUtils(flagConfig));
  }
}
