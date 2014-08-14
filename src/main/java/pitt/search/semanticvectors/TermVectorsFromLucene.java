/**
   Copyright (c) 2007, University of Pittsburgh

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

import java.io.IOException;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;

import pitt.search.semanticvectors.utils.VerbatimLogger;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;

/**
 * Implementation of vector store that creates term vectors by
 * iterating through all the terms in a Lucene index.  Uses a sparse
 * representation for the basic document vectors, which saves
 * considerable space for collections with many individual documents.
 *
 * @author Dominic Widdows, Trevor Cohen.
 */
public class TermVectorsFromLucene {
  private static final Logger logger = Logger.getLogger(
      TermVectorsFromLucene.class.getCanonicalName());

  private FlagConfig flagConfig;
  private VectorStore termVectors;
  private LuceneUtils luceneUtils;
  private VectorStore elementalDocVectors;

  private TermVectorsFromLucene(FlagConfig flagConfig) throws IOException {
    this.flagConfig = flagConfig;
    // Create LuceneUtils Class to filter terms.
    this.luceneUtils = new LuceneUtils(flagConfig);
  }

  /** Returns the object's elemental doc vectors. */
  public VectorStore getElementalDocVectors() { return this.elementalDocVectors; }

  /** Returns the object's luceneUtils. */
  public LuceneUtils getLuceneUtils() { return this.luceneUtils; }

  /** Returns the trained semantic vectors. */
  public VectorStore getSemanticTermVectors() { return termVectors; }

  /**
   * Creates term vectors from a Lucene index.
   * 
   * @param elementalDocVectors The store of elemental document vectors. Null
   * is an acceptable value, in which case the constructor will populate
   * this store. If non-null, the identifiers must correspond to the Lucene doc numbers.
   * @throws IOException if resources on disk cannot be opened.
   */
  public static TermVectorsFromLucene createTermVectorsFromLucene(
      FlagConfig flagConfig, VectorStore elementalDocVectors)
          throws IOException, RuntimeException {
    TermVectorsFromLucene vectorStore = new TermVectorsFromLucene(flagConfig);
    vectorStore.elementalDocVectors = elementalDocVectors;
    vectorStore.createTermVectorsFromLuceneImpl();
    return vectorStore;
  }

  private void createTermVectorsFromLuceneImpl() throws IOException {
    // Check that elemental doc vectors is the right size.
    if (elementalDocVectors != null) {
      logger.info("Reusing basic doc vectors; number of documents: "
          + elementalDocVectors.getNumVectors());
      if (elementalDocVectors.getNumVectors() != luceneUtils.getNumDocs()) {
        throw new RuntimeException("Wrong number of basicDocVectors " +
            "passed into constructor ...");
      }
    } else {
      this.elementalDocVectors = new ElementalVectorStore(flagConfig);
      /*
      // Create basic doc vectors in vector store.
      // Derived term vectors will be linear combinations of these.
      VerbatimLogger.info("Populating basic sparse doc vector store, number of vectors: "
          + luceneUtils.getNumDocs() + "\n");
      VectorStoreRAM randomBasicDocVectors = new VectorStoreRAM(flagConfig);
      randomBasicDocVectors.createNumberedRandomVectors(luceneUtils.getNumDocs(), flagConfig.seedlength(), null);
      this.elementalDocVectors = randomBasicDocVectors;
      */
    }

    trainTermVectors();
  }

  // Training method for term vectors.
  private void trainTermVectors() throws IOException {
    TermsEnum termsEnum = null; // Empty terms enum, encouraged for reuse in Lucene documentation.
    this.termVectors = new VectorStoreRAM(flagConfig);
    // Iterate through an enumeration of terms and create termVector table.
    VerbatimLogger.log(Level.INFO, "Creating semantic term vectors ...\n");

    for (String fieldName : flagConfig.contentsfields()) {
      Terms termsForField = this.luceneUtils.getTermsForField(fieldName);
      TermsEnum terms = termsForField.iterator(termsEnum);
      int tc = 0;
      while (terms.next() != null) {
        tc++;
      }
      VerbatimLogger.info("There are " + tc + " terms (and " + luceneUtils.getNumDocs() + " docs).\n");
    }

    for(String fieldName : flagConfig.contentsfields()) {
      VerbatimLogger.info("Training term vectors for field " + fieldName + "\n");
      int tc = 0;
      TermsEnum terms = this.luceneUtils.getTermsForField(fieldName).iterator(termsEnum);
      BytesRef bytes;
      while ((bytes = terms.next()) != null) {
        // Output progress counter.
        if (( tc % 10000 == 0 ) || ( tc < 10000 && tc % 1000 == 0 )) {
          VerbatimLogger.info("Processed " + tc + " terms ... ");
        }
        tc++;

        Term term = new Term(fieldName, bytes);
        // Skip terms that don't pass the filter.
        if (!luceneUtils.termFilter(term)) {
          continue;
        }

        // Initialize new termVector.
        Vector termVector = VectorFactory.createZeroVector(
            flagConfig.vectortype(), flagConfig.dimension());

        DocsEnum docsEnum = luceneUtils.getDocsForTerm(term);
        while (docsEnum.nextDoc() != DocsEnum.NO_MORE_DOCS) {
          String docID = Integer.toString(docsEnum.docID());
          int freq = docsEnum.freq();
          termVector.superpose(elementalDocVectors.getVector(docID), freq, null);
        }
        termVector.normalize();
        ((VectorStoreRAM) termVectors).putVector(term.text(), termVector);
      }
    }
    VerbatimLogger.info("\nCreated " + termVectors.getNumVectors() + " term vectors.\n");
  }

  /**
   * Generates an elemental vector for each
   * term. These elemental (random index) vectors will be used to
   * construct document vectors, a procedure we have called term-based
   * reflective random indexing.
   *
   * @throws IOException
   * @throws RuntimeException
   */
  public static TermVectorsFromLucene createTermBasedRRIVectors(FlagConfig flagConfig)
      throws IOException, RuntimeException {
    TermVectorsFromLucene termVectors = new TermVectorsFromLucene(flagConfig);
    termVectors.luceneUtils = new LuceneUtils(flagConfig);
    termVectors.createTermBasedRRIVectorsImpl();
    return termVectors;
  }

  private void createTermBasedRRIVectorsImpl() throws IOException, RuntimeException {
    this.termVectors = new ElementalVectorStore(flagConfig);
    TermsEnum termsEnum = null; // Empty terms enum, encouraged for reuse in Lucene documentation.

    if (!flagConfig.initialtermvectors().isEmpty() && !flagConfig.initialtermvectors().equals("random")) {
      VerbatimLogger.info("Using elemental term vectors from file " + flagConfig.initialtermvectors());
      CloseableVectorStore inputReader = VectorStoreReader.openVectorStore(flagConfig.initialtermvectors(), flagConfig);
      Enumeration<ObjectVector> termEnumeration = inputReader.getAllVectors();
      this.termVectors = new VectorStoreRAM(flagConfig);
      
      int count = 0;
      while (termEnumeration.hasMoreElements()) {
        ObjectVector next = termEnumeration.nextElement();
        ((VectorStoreRAM) this.termVectors).putVector(next.getObject(), next.getVector());
        count++;
      }
      inputReader.close();
      logger.info("Read in " + count + " vectors");
    } else {
      logger.info("Generating new elemental term vectors");
      this.termVectors = new ElementalVectorStore(flagConfig);
      for(String fieldName : flagConfig.contentsfields()) {
        TermsEnum terms = luceneUtils.getTermsForField(fieldName).iterator(termsEnum);
        BytesRef bytes;
        while ((bytes = terms.next()) != null) {
          Term term = new Term(fieldName, bytes);
          // Skip terms that don't pass the filter.
          if (!luceneUtils.termFilter(term))
            continue;
          // Automatically triggers elemental vector generation.
          this.termVectors.getVector(term.text());
        }
      }
    }
  }
}
