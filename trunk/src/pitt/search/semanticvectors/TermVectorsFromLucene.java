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
import java.util.Random;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.store.FSDirectory;

/**
 * Implementation of vector store that creates term vectors by
 * iterating through all the terms in a Lucene index.  Uses a sparse
 * representation for the basic document vectors, which saves
 * considerable space for collections with many individual documents.
 *
 * @author Dominic Widdows, Trevor Cohen.
 */
public class TermVectorsFromLucene implements VectorStore {

  private Hashtable<String, ObjectVector> termVectors;
  private IndexReader indexReader;
  private int seedLength;
  private String[] fieldsToIndex;
  private LuceneUtils lUtils;
  private int nonAlphabet;
  private int minFreq;
  private VectorStore basicDocVectors;
  private int dimension;
  private String initialtermvectors;

  // Basic accessor methods.
  /**
   * @return The object's basicDocVectors.
   */
  public VectorStore getBasicDocVectors(){ return this.basicDocVectors; }

  /**
   * @return The object's indexReader.
   */
  public IndexReader getIndexReader(){ return this.indexReader; }

  /**
   * @return The object's list of Lucene fields to index.
   */
  public String[] getFieldsToIndex(){ return this.fieldsToIndex; }


  // Implementation of basic VectorStore methods.
  public float[] getVector(Object term) {
    return termVectors.get(term).getVector();
  }

  public Enumeration<ObjectVector> getAllVectors() {
    return termVectors.elements();
  }

  public int getNumVectors() {
    return termVectors.size();
  }

  /**
   * @param indexDir Directory containing Lucene index.
   * @param seedLength Number of +1 or -1 entries in basic
   * vectors. Should be even to give same number of each.
   * @param minFreq The minimum term frequency for a term to be indexed.
   * @param nonAlphabet 
   * @param basicDocVectors The store of basic document vectors. Null
   * is an acceptable value, in which case the constructor will build
   * this table. If non-null, the identifiers must correspond to the Lucene doc numbers.
   * @param fieldsToIndex These fields will be indexed. If null, all fields will be indexed.
   * @throws IOException
   * @throws RuntimeException
   * @deprecated use the constructor that allows the specification of the
   * vectordimension instead.
   */
  @Deprecated
  public TermVectorsFromLucene(String indexDir,
                               int seedLength,
                               int minFreq,
                               int nonAlphabet,
                               VectorStore basicDocVectors,
                               String[] fieldsToIndex)
      throws IOException, RuntimeException {
    this.dimension = Flags.dimension;
    doConstruct1(indexDir,seedLength,minFreq,nonAlphabet,basicDocVectors,fieldsToIndex);
  }
  /**
   * @param indexDir Directory containing Lucene index.
   * @param dimension
   * @param seedLength Number of +1 or -1 entries in basic
   * vectors. Should be even to give same number of each.
   * @param minFreq The minimum term frequency for a term to be indexed.
   * @param nonAlphabet
   * @param basicDocVectors The store of basic document vectors. Null
   * is an acceptable value, in which case the constructor will build
   * this table. If non-null, the identifiers must correspond to the Lucene doc numbers.
   * @param fieldsToIndex These fields will be indexed. If null, all fields will be indexed.
   * @throws IOException
   * @throws RuntimeException
   */
  public TermVectorsFromLucene(String indexDir,
                               int dimension,
                               int seedLength,
                               int minFreq,
                               int nonAlphabet,
                               VectorStore basicDocVectors,
                               String[] fieldsToIndex)
      throws IOException, RuntimeException {
    this.dimension = dimension;
    doConstruct1(indexDir,seedLength,minFreq,nonAlphabet,basicDocVectors,fieldsToIndex);
  }
  private void doConstruct1(String indexDir,
                               int seedLength,
                               int minFreq,
                               int nonAlphabet,
                               VectorStore basicDocVectors,
                               String[] fieldsToIndex)
      throws IOException, RuntimeException {
    this.minFreq = minFreq;
    this.nonAlphabet = nonAlphabet;
    this.fieldsToIndex = fieldsToIndex;
    this.seedLength = seedLength;

    LuceneUtils.CompressIndex(indexDir);
    // Create LuceneUtils Class to filter terms.
    lUtils = new LuceneUtils(indexDir);
    indexReader = IndexReader.open(FSDirectory.open(new File(indexDir)));

    // Check that basicDocVectors is the right size.
    if (basicDocVectors != null) {
      this.basicDocVectors = basicDocVectors;
      System.out.println("Reusing basic doc vectors; number of documents: "
                         + basicDocVectors.getNumVectors());
      if (basicDocVectors.getNumVectors() != indexReader.numDocs()) {
        throw new RuntimeException("Wrong number of basicDocVectors " +
                                   "passed into constructor ...");
      }
    } else {
      // Create basic doc vectors in vector store.
      // Derived term vectors will be linear combinations of these.
      System.err.println("Populating basic sparse doc vector store, number of vectors: " +
                         indexReader.numDocs());
      VectorStoreSparseRAM randomBasicDocVectors = new VectorStoreSparseRAM();
      randomBasicDocVectors.CreateRandomVectors(indexReader.numDocs(), this.seedLength);
      this.basicDocVectors = randomBasicDocVectors;
    }

    createTermVectors();
  }

  // Training method for term vectors.
  private void createTermVectors() throws IOException {
    termVectors = new Hashtable<String, ObjectVector>();
    // Iterate through an enumeration of terms and create termVector table.
    System.err.println("Creating term vectors ...");
    TermEnum terms = this.indexReader.terms();
    int tc = 0;
    while(terms.next()){
      tc++;
    }
    System.err.println("There are " + tc + " terms (and " + indexReader.numDocs() + " docs)");

    tc = 0;
    terms = indexReader.terms();
    while (terms.next()) {
      // Output progress counter.
      if (( tc % 10000 == 0 ) || ( tc < 10000 && tc % 1000 == 0 )) {
        System.err.print(tc + " ... ");
      }
      tc++;

      Term term = terms.term();
      // Skip terms that don't pass the filter.
      if (!lUtils.termFilter(terms.term(), fieldsToIndex)) {
        continue;
      }

      // Initialize new termVector.
      float[] termVector = new float[dimension];
      for (int i = 0; i < dimension; ++i) {
        termVector[i] = 0;
      }

      TermDocs tDocs = indexReader.termDocs(term);
      while (tDocs.next()) {
        String docID = Integer.toString(tDocs.doc());
        int freq = tDocs.freq();
        
        if (this.basicDocVectors.getClass().equals(VectorStoreSparseRAM.class)) //random docvectors
        {
        	termVector = VectorUtils.addVectors(termVector, ((VectorStoreSparseRAM) this.basicDocVectors).getSparseVector(docID), freq); 
        }
        else  //pretrained docvectors
        {
        termVector = VectorUtils.addVectors(termVector, this.basicDocVectors.getVector(docID),freq);
        }
        

      }
      termVector = VectorUtils.getNormalizedVector(termVector);
      termVectors.put(term.text(), new ObjectVector(term.text(), termVector));
    }
    System.err.println("\nCreated " + termVectors.size() + " term vectors ...");
  }

  /**
   * This constructor generates an elemental vector for each
   * term. These elemental (random index) vectors will be used to
   * construct document vectors, a procedure we have called term-based
   * reflective random indexing.
   *
   * @param indexDir			the directory of the Lucene Index
   * @param seedLength Number of +1 or -1 entries in basic
   * vectors. Should be even to give same number of each.
   * @param nonAlphabet 		the number of nonalphabet characters permitted
   * @param minFreq The minimum term frequency for a term to be indexed.
   * @param fieldsToIndex		the fields to be indexed (most commonly "contents")
   * @throws IOException 
   * @throws RuntimeException 
   * @deprecated use the constructor that allows the speficification of the
   * initialtermvectors parameter
   */
  public TermVectorsFromLucene(String indexDir,
			       int seedLength,
			       int minFreq,
			       int nonAlphabet,
			       String[] fieldsToIndex)
      throws IOException, RuntimeException {
    this.initialtermvectors = Flags.initialtermvectors;
    doConstruct2(indexDir,seedLength,minFreq,nonAlphabet,fieldsToIndex);
  }
  /**
   * This constructor generates an elemental vector for each
   * term. These elemental (random index) vectors will be used to
   * construct document vectors, a procedure we have called term-based
   * reflective random indexing.
   *
   * @param indexDir			the directory of the Lucene Index
   * @param seedLength Number of +1 or -1 entries in basic
   * vectors. Should be even to give same number of each.
   * @param nonAlphabet 		the number of nonalphabet characters permitted
   * @param minFreq The minimum term frequency for a term to be indexed.
   * @param initialtermvectors 
   * @param fieldsToIndex		the fields to be indexed (most commonly "contents")
   * @throws IOException 
   * @throws RuntimeException 
   */
  public TermVectorsFromLucene(String indexDir,
			       int seedLength,
			       int minFreq,
			       int nonAlphabet,
                               String initialtermvectors,
			       String[] fieldsToIndex)
      throws IOException, RuntimeException {
    this.initialtermvectors = initialtermvectors;
    doConstruct2(indexDir,seedLength,minFreq,nonAlphabet,fieldsToIndex);
  }

  private void doConstruct2(String indexDir,
			       int seedLength,
			       int minFreq,
			       int nonAlphabet,
			       String[] fieldsToIndex)
      throws IOException, RuntimeException {
    this.minFreq = minFreq;
    this.nonAlphabet = nonAlphabet;
    this.fieldsToIndex = fieldsToIndex;
    this.seedLength = seedLength;

    LuceneUtils.CompressIndex(indexDir);

    // Create LuceneUtils Class to filter terms.
    lUtils = new LuceneUtils(indexDir);

    indexReader = IndexReader.open(FSDirectory.open(new File(indexDir)));
    Random random = new Random();
    this.termVectors = new Hashtable<String,ObjectVector>();

    // For each term in the index
    if (initialtermvectors.equals("random")) {
      System.err.println("Creating random term vectors");
      TermEnum terms = indexReader.terms();
      int tc = 0;
      while(terms.next()){
        Term term = terms.term();
        // Skip terms that don't pass the filter.
        if (!lUtils.termFilter(terms.term(), fieldsToIndex))  {
          continue;
        }
        tc++;

        short[] indexVector =  VectorUtils.generateRandomVector(seedLength, random);
        // Place each term vector in the vector store.
        this.termVectors.put(term.text(),
                             new ObjectVector(term.text(),
                                              VectorUtils.sparseVectorToFloatVector(
                                                  indexVector, Flags.dimension)));
      }
    } else {
      System.err.println("Using semantic term vectors from file " + initialtermvectors);
      VectorStoreReaderLucene inputReader = new VectorStoreReaderLucene(initialtermvectors);
      Enumeration<ObjectVector> termEnumeration = inputReader.getAllVectors();
      inputReader.close();
      int count = 0;

      while (termEnumeration.hasMoreElements()) {
        ObjectVector next = termEnumeration.nextElement();
        String term = next.getObject().toString();
        this.termVectors.put(term, next);
        count++;
      }
      System.err.println("Read in "+count+" vectors");
    }
  }

}
