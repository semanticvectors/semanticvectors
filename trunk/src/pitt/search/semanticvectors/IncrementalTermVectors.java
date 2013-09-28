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
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

import pitt.search.semanticvectors.utils.VerbatimLogger;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorType;

import java.util.Enumeration;
import java.util.logging.Logger;

/**
 * Generates term vectors incrementally (i.e. one document at a time)
 * This saves memory compared with other implementations.
 * The training procedure still iterates through all the documents in the Lucene index,
 * so currently this class is purely an optimization, not an implementation of
 * incremental indexing in the sense of being able to add extra terms and documents later after
 * an initial model has been built.
 *
 * @author Trevor Cohen, Dominic Widdows
 */
public class IncrementalTermVectors implements VectorStore {
  /** Usage message printed if {@link #main} is called with ill-formed arguments. */
  public static String usageMessage = "\nIncrementalTermVectors class in package pitt.search.semanticvectors"
      + "\nUsage: java pitt.search.semanticvectors.IncrementalTermVectors [-docvectorsfile ...] [-luceneindexpath ...]"
      + "\nIncrementalTermVectors creates termvectors files in local directory from docvectors file.";

  private static final Logger logger = Logger.getLogger(
      IncrementalTermVectors.class.getCanonicalName());

  private FlagConfig flagConfig;

  private VectorStoreRAM termVectorData;
  private LuceneUtils luceneUtils = null;

  @Override
  public VectorType getVectorType() { return flagConfig.vectortype(); }

  @Override
  public int getDimension() { return flagConfig.dimension(); }

  /**
   * Constructs new instance and creates term vectors.
   */
  public IncrementalTermVectors(FlagConfig flagConfig, LuceneUtils luceneUtils)
      throws IOException {
    this.flagConfig = flagConfig;
    this.luceneUtils = luceneUtils;
    createIncrementalTermVectorsFromLucene();
  }

  private void initializeVectorStore() throws IOException {
    termVectorData = new VectorStoreRAM(flagConfig);

    for (String fieldName : this.flagConfig.contentsfields()) {
      Terms terms = this.luceneUtils.getTermsForField(fieldName);
      TermsEnum termEnum = terms.iterator(null);
      int tc = 0;

      BytesRef bytes;
      while ((bytes = termEnum.next()) != null) {
        Term term = new Term(fieldName, bytes);

        if (termVectorData.getVector(term.text()) != null) continue;
        if (!luceneUtils.termFilter(term)) continue;
        tc++;
        Vector termVector = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());

        // Place each term vector in the vector store.
        termVectorData.putVector(term.text(), termVector);
      }
      VerbatimLogger.info(String.format(
          "There are %d terms (and %d docs)", tc, this.luceneUtils.getNumDocs()));
    }
  }

  private void createIncrementalTermVectorsFromLucene() throws IOException {
    int numdocs = luceneUtils.getNumDocs();

    // Open file and write headers.
    File vectorFile = new File(flagConfig.docvectorsfile());
    String parentPath = vectorFile.getParent();
    if (parentPath == null) parentPath = "";
    FSDirectory fsDirectory = FSDirectory.open(new File(parentPath));
    IndexInput inputStream = fsDirectory.openInput(
        VectorStoreUtils.getStoreFileName(flagConfig.docvectorsfile(), flagConfig), IOContext.DEFAULT);

    logger.info("Read vectors incrementally from file " + vectorFile);

    // Read number of dimensions from document vectors.
    String header = inputStream.readString();
    FlagConfig.mergeWriteableFlagsFromString(header, flagConfig);

    initializeVectorStore();

    // Iterate through documents.
    for (int dc = 0; dc < numdocs; dc++) {
      /* output progress counter */
      if (( dc % 10000 == 0 ) || ( dc < 10000 && dc % 1000 == 0 )) {
        VerbatimLogger.info(dc + " ... ");
      }

      Vector docVector = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());

      docVector.readFromLuceneStream(inputStream);

      for (String fieldName : this.flagConfig.contentsfields()) {
        Terms docTerms = this.luceneUtils.getTermVector(new Integer(dc), fieldName);
        if (docTerms == null) {logger.severe("No term vector for document "+dc); continue; }
        
        TermsEnum termsEnum = docTerms.iterator(null); 

        BytesRef bytes;
        while ((bytes = termsEnum.next()) != null) {
          Vector termVector = null;

          try{
            termVector = termVectorData.getVector(bytes.utf8ToString());
          } catch (NullPointerException npe) {
            // Don't normally print anything - too much data!
            logger.finest(String.format("term %s not represented", bytes.utf8ToString()));
          }
          // Exclude terms that are not represented in termVectorData
          if (termVector != null && termVector.getDimension() > 0) {
        	  DocsEnum docs = termsEnum.docs(null, null);
              docs.nextDoc();
    	      float freq = luceneUtils.getLocalTermWeight(docs.freq());  
        	  
    	      termVector.superpose(docVector, freq, null);
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

    VectorStore termVectors = new IncrementalTermVectors(flagConfig, new LuceneUtils(flagConfig));
    VectorStoreWriter.writeVectors(flagConfig.termvectorsfile(), flagConfig, termVectors);
  }
}
