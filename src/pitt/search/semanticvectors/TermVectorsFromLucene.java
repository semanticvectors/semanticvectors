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

import java.io.File;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Random;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.store.FSDirectory;

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

  private Hashtable<String, ObjectVector> termVectors;
  private int dimension;
  private VectorType vectorType;
  private IndexReader indexReader;
  private int seedLength;
  private String[] fieldsToIndex;
  private LuceneUtils lUtils;
  private int maxNonAlphabet;
  private int minFreq;
  private int maxFreq;
  private VectorStore basicDocVectors;
  private String initialTermVectorsFile;
  private String indexDir;

  private TermVectorsFromLucene() {}
  
  // Basic accessor methods.
  @Override
  public VectorType getVectorType() { return vectorType; }
  
  @Override
  public int getDimension() { return dimension; }
  
  /**
   * @return The object's basicDocVectors.
   */
  public VectorStore getBasicDocVectors() { return this.basicDocVectors; }

  /**
   * @return The object's indexReader.
   */
  public IndexReader getIndexReader() { return this.indexReader; }

  /**
   * @return The object's list of Lucene fields to index.
   */
  public String[] getFieldsToIndex() { return this.fieldsToIndex; }
  
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
   * @param indexDir directory containing Lucene index.
   * @param vectorType type of vector
   * @param dimension number of dimensions
   * @param seedLength number of +1 or -1 entries in basic
   * vectors. Should be even to give same number of each.
   * @param minFreq The minimum term frequency for a term to be indexed.
   * @param nonAlphabet
   * @param basicDocVectors The store of basic document vectors. Null
   * is an acceptable value, in which case the constructor will build
   * this table. If non-null, the identifiers must correspond to the Lucene doc numbers.
   * @param fieldsToIndex These fields will be indexed. If null, all fields will be indexed.
   * @throws IOException if resources on disk cannot be opened.
   */
  public static TermVectorsFromLucene createTermVectorsFromLucene(
      String indexDir, VectorType vectorType, int dimension, int seedLength, int minFreq,
      int maxFreq, int nonAlphabet, VectorStore basicDocVectors, String[] fieldsToIndex)
  throws IOException, RuntimeException {
    TermVectorsFromLucene vectorStore = new TermVectorsFromLucene() {};
    vectorStore.dimension = dimension;
    vectorStore.vectorType = vectorType;
    vectorStore.indexDir = indexDir;
    vectorStore.minFreq = minFreq;
    vectorStore.maxFreq = maxFreq;
    vectorStore.maxNonAlphabet = nonAlphabet;
    vectorStore.fieldsToIndex = fieldsToIndex;
    vectorStore.seedLength = seedLength;
    vectorStore.basicDocVectors = basicDocVectors;
    vectorStore.createTemVectorsFromLuceneImpl();
    return vectorStore;
  }
  
  private void createTemVectorsFromLuceneImpl() throws IOException {
    LuceneUtils.compressIndex(indexDir);
    // Create LuceneUtils Class to filter terms.
    lUtils = new LuceneUtils(indexDir);
    indexReader = IndexReader.open(FSDirectory.open(new File(indexDir)));

    // Check that basicDocVectors is the right size.
    if (basicDocVectors != null) {
      logger.info("Reusing basic doc vectors; number of documents: "
          + basicDocVectors.getNumVectors());
      if (basicDocVectors.getNumVectors() != indexReader.numDocs()) {
        throw new RuntimeException("Wrong number of basicDocVectors " +
        "passed into constructor ...");
      }
    } else {
      // Create basic doc vectors in vector store.
      // Derived term vectors will be linear combinations of these.
      logger.info("Populating basic sparse doc vector store, number of vectors: "
          + indexReader.numDocs());
      VectorStoreRAM randomBasicDocVectors = new VectorStoreRAM(vectorType, dimension);
      randomBasicDocVectors.createRandomVectors(indexReader.numDocs(), this.seedLength, null);
      this.basicDocVectors = randomBasicDocVectors;
    }

    trainTermVectors();
  }

  // Training method for term vectors.
  private void trainTermVectors() throws IOException {
    termVectors = new Hashtable<String, ObjectVector>();
    // Iterate through an enumeration of terms and create termVector table.
    VerbatimLogger.log(Level.INFO, "Creating term vectors ...");
    TermEnum terms = this.indexReader.terms();
    int tc = 0;
    while(terms.next()){
      tc++;
    }
    VerbatimLogger.info("There are " + tc + " terms (and " + indexReader.numDocs() + " docs).\n");

    tc = 1;
    terms = indexReader.terms();
    while (terms.next()) {
      // Output progress counter.
      if (( tc % 10000 == 0 ) || ( tc < 10000 && tc % 1000 == 0 )) {
        VerbatimLogger.info("Processed " + tc + " terms ... ");
      }
      tc++;

      Term term = terms.term();
      // Skip terms that don't pass the filter.
      if (!lUtils.termFilter(terms.term(), fieldsToIndex, minFreq, maxFreq, maxNonAlphabet)) {
        continue;
      }

      // Initialize new termVector.
      Vector termVector = VectorFactory.createZeroVector(Flags.vectortype, Flags.dimension);

      TermDocs tDocs = indexReader.termDocs(term);
      while (tDocs.next()) {
        String docID = Integer.toString(tDocs.doc());
        int freq = tDocs.freq();

        termVector.superpose(basicDocVectors.getVector(docID), freq, null);
      }
      termVector.normalize();
      termVectors.put(term.text(), new ObjectVector(term.text(), termVector));
    }
    VerbatimLogger.info("\nCreated " + termVectors.size() + " term vectors.\n");
  }

  /**
   * Generates an elemental vector for each
   * term. These elemental (random index) vectors will be used to
   * construct document vectors, a procedure we have called term-based
   * reflective random indexing.
   *
   * @param indexDir the directory of the Lucene Index
   * @param seedLength number of +1 or -1 entries in basic vectors.
   *        Should be even to give same number of each.
   * @param nonAlphabet the number of nonalphabet characters permitted
   * @param minFreq The minimum term frequency for a term to be indexed
   * @param initialTermVectorsFile name of file containing initial term vectors. If "random",
   *        initialize random vectors instead.
   * @param fieldsToIndex the fields to be indexed (most commonly "contents")
   * @throws IOException
   * @throws RuntimeException
   */
  public static TermVectorsFromLucene createTermBasedRRIVectors(
      String indexDir, VectorType vectorType, int dimension, int seedLength, int minFreq, int maxFreq,
      int nonAlphabet, String initialTermVectorsFile, String[] fieldsToIndex)
  throws IOException, RuntimeException {
    TermVectorsFromLucene trainedTermvectors = new TermVectorsFromLucene() {};
    trainedTermvectors.indexDir = indexDir;
    trainedTermvectors.initialTermVectorsFile = initialTermVectorsFile;
    trainedTermvectors.minFreq = minFreq;
    trainedTermvectors.maxFreq = maxFreq;
    trainedTermvectors.maxNonAlphabet = nonAlphabet;
    trainedTermvectors.fieldsToIndex = fieldsToIndex;
    trainedTermvectors.seedLength = seedLength;
    trainedTermvectors.dimension = dimension;
    trainedTermvectors.vectorType = vectorType;
    trainedTermvectors.createTermBasedRRIVectorsImpl();
    return trainedTermvectors;
  }

  private void createTermBasedRRIVectorsImpl() throws IOException, RuntimeException {
    LuceneUtils.compressIndex(indexDir);

    // Create LuceneUtils Class to filter terms.
    lUtils = new LuceneUtils(indexDir);

    indexReader = IndexReader.open(FSDirectory.open(new File(indexDir)));
    Random random = new Random();
    this.termVectors = new Hashtable<String,ObjectVector>();

    // For each term in the index
    if (initialTermVectorsFile.equals("random")) {
      logger.info("Creating random term vectors");
      TermEnum terms = indexReader.terms();
      int tc = 0;
      while(terms.next()){
        Term term = terms.term();
        // Skip terms that don't pass the filter.
        if (!lUtils.termFilter(terms.term(), fieldsToIndex, minFreq, maxFreq, maxNonAlphabet))  {
          continue;
        }
        tc++;
        Vector indexVector = VectorFactory.generateRandomVector(
            vectorType, dimension, seedLength, random);
        // Place each term vector in the vector store.
        this.termVectors.put(term.text(), new ObjectVector(term.text(), indexVector));
      }
    } else {
      VerbatimLogger.info("Using semantic term vectors from file " + initialTermVectorsFile);
      VectorStoreReaderLucene inputReader = new VectorStoreReaderLucene(initialTermVectorsFile);
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
