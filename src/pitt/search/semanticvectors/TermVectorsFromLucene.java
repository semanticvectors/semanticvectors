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
import java.util.Hashtable;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;

import pitt.search.semanticvectors.hashing.Bobcat;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorType;

/**
 * Implementation of vector store that creates term vectors by
 * iterating through all the terms in a Lucene index.  Uses a sparse
 * representation for the basic document vectors, which saves
 * considerable space for collections with many individual documents.
 *
 * @author Dominic Widdows, Trevor Cohen.
 */
public class TermVectorsFromLucene implements VectorStore {
  private static final Logger logger = Logger.getLogger(
      TermVectorsFromLucene.class.getCanonicalName());

  private FlagConfig flagConfig;
  private Hashtable<String, ObjectVector> termVectors;
  //private IndexReader indexReader;
  private LuceneUtils luceneUtils;
  private VectorStore basicDocVectors;

  private TermVectorsFromLucene(FlagConfig flagConfig) {
    this.flagConfig = flagConfig;
  }

  // Basic accessor methods.
  @Override
  public VectorType getVectorType() {
    return flagConfig.vectortype();
  }

  @Override
  public int getDimension() { return flagConfig.dimension(); }

  /** Returns the object's basicDocVectors. */
  public VectorStore getBasicDocVectors() { return this.basicDocVectors; }

  /**
   * @return The object's indexReader.
   */
  //public IndexReader getIndexReader() { return this.indexReader; }

  /** Returns the object's luceneUtils. */
  public LuceneUtils getLuceneUtils() { return this.luceneUtils; }

  /**
   * @return The object's list of Lucene fields to index.
   * 
   * TODO: Deprecate.
   */
  public String[] getFieldsToIndex() { return flagConfig.contentsfields(); }

  // Implementation of basic VectorStore methods.
  public Vector getVector(Object term) {
    return termVectors.get(term).getVector();
  }

  public Enumeration<ObjectVector> getAllVectors() {
    return termVectors.elements();
  }

  public int getNumVectors() {
    return termVectors.size();
  }

  /**
   * Creates term vectors from a Lucene index.
   * 
   * @param basicDocVectors The store of basic document vectors. Null
   * is an acceptable value, in which case the constructor will build
   * this table. If non-null, the identifiers must correspond to the Lucene doc numbers.
   * @throws IOException if resources on disk cannot be opened.
   */
  public static TermVectorsFromLucene createTermVectorsFromLucene(
      FlagConfig flagConfig, VectorStore basicDocVectors)
          throws IOException, RuntimeException {
    TermVectorsFromLucene vectorStore = new TermVectorsFromLucene(flagConfig);
    vectorStore.basicDocVectors = basicDocVectors;
    vectorStore.createTemVectorsFromLuceneImpl();
    return vectorStore;
  }

  private void createTemVectorsFromLuceneImpl() throws IOException {
    LuceneUtils.compressIndex(flagConfig.luceneindexpath());
    // Create LuceneUtils Class to filter terms.
    luceneUtils = new LuceneUtils(flagConfig);
    // indexReader = IndexReader.open(FSDirectory.open(new File(flagConfig.luceneindexpath())));

    // Check that basicDocVectors is the right size.
    if (basicDocVectors != null) {
      logger.info("Reusing basic doc vectors; number of documents: "
          + basicDocVectors.getNumVectors());
      if (basicDocVectors.getNumVectors() != luceneUtils.getNumDocs()) {
        throw new RuntimeException("Wrong number of basicDocVectors " +
            "passed into constructor ...");
      }
    } else {
      // Create basic doc vectors in vector store.
      // Derived term vectors will be linear combinations of these.
      VerbatimLogger.info("Populating basic sparse doc vector store, number of vectors: "
          + luceneUtils.getNumDocs() + "\n");
      VectorStoreRAM randomBasicDocVectors = new VectorStoreRAM(flagConfig);
      randomBasicDocVectors.createRandomVectors(luceneUtils.getNumDocs(), flagConfig.seedlength(), null);
      this.basicDocVectors = randomBasicDocVectors;
    }

    trainTermVectors();
  }

  // Training method for term vectors.
  private void trainTermVectors() throws IOException {
    TermsEnum termsEnum = null; // Empty terms enum, encouraged for reuse in Lucene documentation.
    this.termVectors = new Hashtable<String, ObjectVector>();
    // Iterate through an enumeration of terms and create termVector table.
    VerbatimLogger.log(Level.INFO, "Creating term vectors ...");

    for (String fieldName : flagConfig.contentsfields()) {
      TermsEnum terms = this.luceneUtils.getTermsForField(fieldName).iterator(termsEnum);
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
            getVectorType(), flagConfig.dimension());

        DocsEnum docsEnum = luceneUtils.getDocsForTerm(term);
        while (docsEnum.nextDoc() != DocsEnum.NO_MORE_DOCS) {
          String docID = Integer.toString(docsEnum.docID());
          int freq = docsEnum.freq();
          termVector.superpose(basicDocVectors.getVector(docID), freq, null);
        }
        termVector.normalize();
        termVectors.put(term.text(), new ObjectVector(term.text(), termVector));
      }
    }
    VerbatimLogger.info("\nCreated " + termVectors.size() + " term vectors.\n");
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
    TermVectorsFromLucene trainedTermvectors = new TermVectorsFromLucene(flagConfig);
    trainedTermvectors.createTermBasedRRIVectorsImpl();
    return trainedTermvectors;
  }

  private void createTermBasedRRIVectorsImpl() throws IOException, RuntimeException {
    LuceneUtils.compressIndex(flagConfig.luceneindexpath());

    // Create LuceneUtils Class to filter terms.
    luceneUtils = new LuceneUtils(flagConfig);

    Random random = new Random();
    this.termVectors = new Hashtable<String,ObjectVector>();
    TermsEnum termsEnum = null; // Empty terms enum, encouraged for reuse in Lucene documentation.

    // For each contents field and each term in the index
    if (flagConfig.initialtermvectors().equals("random")) {
      logger.info("Creating random term vectors");

      for(String fieldName : flagConfig.contentsfields()) {
        VerbatimLogger.info("Training RRI term vectors for field " + fieldName + "\n");
        TermsEnum terms = luceneUtils.getTermsForField(fieldName).iterator(termsEnum);
        BytesRef bytes;
        while ((bytes = terms.next()) != null) {
          Term term = new Term(fieldName, bytes);
          // Skip terms that don't pass the filter.
          if (!luceneUtils.termFilter(term))  {
            continue;
          }

          if (flagConfig.deterministicvectors())
            random.setSeed(Bobcat.asLong(term.text()));

          Vector indexVector = VectorFactory.generateRandomVector(
              flagConfig.vectortype(), flagConfig.dimension(),
              flagConfig.seedlength(), random);
          // Place each term vector in the vector store.
          this.termVectors.put(term.text(), new ObjectVector(term.text(), indexVector));
        }
      }
    } else {
      VerbatimLogger.info("Using semantic term vectors from file " + flagConfig.initialtermvectors());
      VectorStoreReaderLucene inputReader = new VectorStoreReaderLucene(flagConfig.initialtermvectors(), flagConfig);
      Enumeration<ObjectVector> termEnumeration = inputReader.getAllVectors();
      int count = 0;

      while (termEnumeration.hasMoreElements()) {
        ObjectVector next = termEnumeration.nextElement();
        String term = next.getObject().toString();
        this.termVectors.put(term, next);
        count++;
      }
      inputReader.close();
      logger.info("Read in " + count + " vectors");
    }
  }
}
