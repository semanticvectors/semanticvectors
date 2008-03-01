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
import org.apache.lucene.index.*;
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
		private int minFreq;
		private short[][] basicDocVectors;

		/**
		 * @return The object's indexReader.
		 */
		public IndexReader getIndexReader(){ return this.indexReader; }

		/**
		 * @return The object's basicDocVectors.
		 */
		public short[][] getBasicDocVectors(){ return this.basicDocVectors; }

		/**
		 * @return The object's basicDocVectors.
		 */
		public String[] getFieldsToIndex(){ return this.fieldsToIndex; }

		/**
		 * @param indexDir Directory containing Lucene index.
		 * @param seedLength Number of +1 or -1 entries in basic
		 * vectors. Should be even to give same number of each.
		 * @param minFreq The minimum term frequency for a term to be indexed.
		 * @param basicDocVectors The table of basic document
		 * vectors. Null is an acceptable value, in which case the
		 * constructor will build this table.
		 * @param fieldsToIndex These fields will be indexed. If null, all fields will be indexed.
		 */
		public TermVectorsFromLucene(String indexDir,
																 int seedLength,
																 int minFreq, 
																 short[][] basicDocVectors,
																 String[] fieldsToIndex) 
				throws IOException, RuntimeException {
				this.minFreq = minFreq;
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

				/* Create the basic document vectors. */
				indexReader = IndexReader.open(indexDir);
				Random random = new Random();

				/* Check that basicDocVectors is the right size */
				if (basicDocVectors != null) {
						this.basicDocVectors = basicDocVectors;
						if (basicDocVectors.length != indexReader.numDocs()) {
								throw new RuntimeException("Wrong number of basicDocVectors " +
																					 "passed into constructor ...");
						}
				} else {
						/* Create basic doc vector table */
						System.err.println("Populating basic doc vector table, number of vectors: " +
															 indexReader.numDocs());
						this.basicDocVectors = new short[indexReader.numDocs()][seedLength];
						for (int i = 0; i < indexReader.numDocs(); i++) {
								this.basicDocVectors[i] =
										VectorUtils.generateRandomVector(seedLength, random);
						}
				}

				termVectors = new Hashtable();

				/* iterate through an enumeration of terms and create termVector table*/
				System.err.println("Creating term vectors ...");
				TermEnum terms = indexReader.terms();
				int tc = 0;
				while(terms.next()){
						tc++;
				}
				System.err.println("There are " + tc + " terms (and " + indexReader.numDocs() + " docs)");

				tc = 0;
				terms = indexReader.terms();
				while (terms.next()) {
						/* output progress counter */
						if (( tc % 10000 == 0 ) || ( tc < 10000 && tc % 1000 == 0 )) {
								System.err.print(tc + " ... ");
						}
						tc++;

						Term term = terms.term();

						/* skip terms that don't pass the filter */
						if (!termFilter(terms.term())) {
								continue;
						}

						/* initialize new termVector */
						float[] termVector = new float[ObjectVector.vecLength];
						for (int i = 0; i < ObjectVector.vecLength; i++) {
								termVector[i]=0;
						}

						TermDocs tDocs = indexReader.termDocs(term);
						while( tDocs.next() ){
								int doc = tDocs.doc();
								int freq = tDocs.freq();

								/* add random vector (in condensed (signed index + 1)
								 * representation) to term vector by adding -1 or +1 to the
								 * location (index - 1) according to the sign of the index.
								 * (The -1 and +1 are necessary because there is no signed
								 * version of 0, so we'd have no way of telling that the
								 * zeroth position in the array should be plus or minus 1.)
								 * See also generateRandomVector method below.
								 */
								for ( int i = 0; i < seedLength; i++ ){
										short index = this.basicDocVectors[doc][i];
										termVector[Math.abs(index) - 1] += freq * Math.signum(index);
								}
						}
						termVector = VectorUtils.getNormalizedVector(termVector);
						termVectors.put(term.text(), new ObjectVector(term.text(), termVector));
				}
				System.err.println("\nCreated " + termVectors.size() + " term vectors ...");
		}

		public float[] getVector(Object term){
				return termVectors.get(term).getVector();
		}

		public Enumeration getAllVectors(){
				return termVectors.elements();
		}

		/**
		 * Filters out non-alphabetic terms and those of low frequency
		 * it might be a good idea to factor this out as a separate component.
		 * @param term Term to be filtered.
		 */
		private boolean termFilter (Term term) throws IOException {
				/* Field filter. */
				if (this.fieldsToIndex != null) {
						boolean desiredField = false;
						for (int i = 0; i < fieldsToIndex.length; ++i) {
								if (term.field().equals(fieldsToIndex[i])) {
										desiredField = true;
								}
						}
						if (desiredField == false) {
								return false;
						}
				}

				/* character filter */
				String termText = term.text();
				for( int i=0; i<termText.length(); i++ ){
						if( !Character.isLetter(termText.charAt(i)) ){
								return false;
						}
				}

				/* freqency filter */
				int freq = 0;
				TermDocs tDocs = indexReader.termDocs(term);
				while( tDocs.next() ){
						freq += tDocs.freq();
				}
				if( freq < minFreq ){
						return false;
				}

				return true;
		}
}
