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

import java.util.Hashtable;
import java.util.Enumeration;
import java.util.Random;
import java.io.IOException;
import java.lang.RuntimeException;
import org.apache.lucene.index.IndexModifier;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

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

  /**
   * @param indexDir Directory containing Lucene index.
   * @param seedLength Number of +1 or -1 entries in basic
   * vectors. Should be even to give same number of each.
   * @param minFreq The minimum term frequency for a term to be indexed.
   * @param basicDocVectors The store of basic document vectors. Null
   * is an acceptable value, in which case the constructor will build
   * this table. If non-null, the identifiers must correspond to the Lucene doc numbers.
   * @param fieldsToIndex These fields will be indexed. If null, all fields will be indexed.
   */
  public TermVectorsFromLucene(String indexDir,
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

    /* This small preprocessing step uses an IndexModifier to make
     * sure that the Lucene index is optimized to use contiguous
     * integers as identifiers, otherwise exceptions can occur if
     * document id's are greater than indexReader.numDocs().
     */
    IndexModifier modifier = new IndexModifier(indexDir, new StandardAnalyzer(), false);
    modifier.optimize();
    modifier.close();

    // Create LuceneUtils Class to filter terms.
    lUtils = new LuceneUtils(indexDir);

    indexReader = IndexReader.open(indexDir);

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
      if (!lUtils.termFilter(terms.term(), fieldsToIndex, nonAlphabet, minFreq)) {
        continue;
      }

      // Initialize new termVector.
      float[] termVector = new float[Flags.dimension];
      for (int i = 0; i < Flags.dimension; ++i) {
        termVector[i] = 0;
      }

      TermDocs tDocs = indexReader.termDocs(term);
      while (tDocs.next()) {
        String docID = Integer.toString(tDocs.doc());
        float[] docVector = this.basicDocVectors.getVector(docID);
        int freq = tDocs.freq();
				
        for (int i = 0; i < Flags.dimension; ++i) {
          termVector[i] += freq * docVector[i];
        }
      }
      termVector = VectorUtils.getNormalizedVector(termVector);
      termVectors.put(term.text(), new ObjectVector(term.text(), termVector));
    }
    System.err.println("\nCreated " + termVectors.size() + " term vectors ...");
  }

  /**
	 * This constructor generates an elemental vector for each term. These elemental (random index) vectors will
	 * be used to construct document vectors, a procedure we have called term-based reflective random indexing 
	 * @param indexDir			the directory of the Lucene Index
	 * @param seedLength Number of +1 or -1 entries in basic
	 * vectors. Should be even to give same number of each.
	 * @param nonAlphabet 		the number of nonalphabet characters permitted
	 * @param minFreq The minimum term frequency for a term to be indexed.
	 * @param fieldsToIndex		the fields to be indexed (most commonly "contents")
	 */
	
  public TermVectorsFromLucene(String indexDir,  
		  						int seedLength,
		  						int minFreq,
		  						int nonAlphabet,
		  						String[] fieldsToIndex) throws IOException, RuntimeException 
  { 
  	
  
	  this.minFreq = minFreq;
	    this.nonAlphabet = nonAlphabet;
	    this.fieldsToIndex = fieldsToIndex;
	    this.seedLength = seedLength;

	    /* This small preprocessing step uses an IndexModifier to make
	     * sure that the Lucene index is optimized to use contiguous
	     * integers as identifiers, otherwise exceptions can occur if
	     * document id's are greater than indexReader.numDocs().
	     */
	    IndexModifier modifier = new IndexModifier(indexDir, new StandardAnalyzer(), false);
	    modifier.optimize();
	    modifier.close();

	    // Create LuceneUtils Class to filter terms.
	    lUtils = new LuceneUtils(indexDir);

	    indexReader = IndexReader.open(indexDir);
	    Random random = new Random();
     	this.termVectors = new Hashtable<String,ObjectVector>();
      
    // For each term in the index
    
    TermEnum terms = indexReader.terms();
    int tc = 0;
    while(terms.next()){
      Term term = terms.term();
     
      // Skip terms that don't pass the filter.

      if (!lUtils.termFilter(terms.term(), fieldsToIndex, nonAlphabet, minFreq))  {
        continue;
      }
      tc++;
      
      short[] indexVector =  VectorUtils.generateRandomVector(seedLength, random);
      float[] indexVector2 = VectorUtils.getNormalizedVector(VectorUtils.sparseVectorToFloatVector(indexVector, Flags.dimension));
      // Place each term vector in the vector store.
   
      this.termVectors.put(term.text(), new ObjectVector(term.text(),VectorUtils.sparseVectorToFloatVector(indexVector, Flags.dimension)));
    }

  
   
  }
	
  
  public float[] getVector(Object term) {
    return termVectors.get(term).getVector();
  }

  public Enumeration getAllVectors() {
    return termVectors.elements();
  }

  public int getNumVectors() {
    return termVectors.size();
  }

}
