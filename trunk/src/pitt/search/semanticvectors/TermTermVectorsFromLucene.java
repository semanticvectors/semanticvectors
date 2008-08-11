/**
   Copyright (c) 2008, University of Pittsburgh

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
import org.apache.lucene.index.TermPositionVector;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

/**
 * Implementation of vector store that creates term by term
 * cooccurence vectors by iterating through all the documents in a
 * Lucene index.  This class implements a sliding context window
 * approach, as used by Burgess and Lund (HAL) and Schutze amongst
 * others Uses a sparse representation for the basic document vectors,
 * which saves considerable space for collections with many individual
 * documents.
 *
 * @author Trevor Cohen, Dominic Widdows. 
 */
public class TermTermVectorsFromLucene implements VectorStore {

  private VectorStoreRAM termVectors;
  private VectorStoreSparseRAM indexVectors;
  private IndexReader indexReader;
  private int seedLength;
  private String[] fieldsToIndex;
  private int minFreq;
	private BuildPositionalIndex.IndexType positionalIndexType;
  //private boolean permute = false;
  //private boolean directional = false;
  
  /**
   * @return The object's indexReader.
   */
  public IndexReader getIndexReader(){ return this.indexReader; }


  public String[] getFieldsToIndex(){ return this.fieldsToIndex; }

  /**
   * @param indexDir Directory containing Lucene index.
   * @param seedLength Number of +1 or -1 entries in basic
   * vectors. Should be even to give same number of each.
   * @param minFreq The minimum term frequency for a term to be indexed.
   * @param windowSize The size of the sliding context window.
   * @param fieldsToIndex These fields will be indexed. If null, all fields will be indexed.
   */
  public TermTermVectorsFromLucene(String indexDir,
																	 int seedLength,
																	 int minFreq,
																	 int windowSize,
																	 String[] fieldsToIndex,
																	 BuildPositionalIndex.IndexType positionalIndexType)
		throws IOException, RuntimeException {
    this.minFreq = minFreq;
    this.fieldsToIndex = fieldsToIndex;
    this.seedLength = seedLength;
		this.positionalIndexType = positionalIndexType;

    /* This small preprocessing step uses an IndexModifier to make
     * sure that the Lucene index is optimized to use contiguous
     * integers as identifiers, otherwise exceptions can occur if
     * document id's are greater than indexReader.numDocs().
     */
    IndexModifier modifier = new IndexModifier(indexDir, new StandardAnalyzer(), false);
    modifier.optimize();
    modifier.close();

    /* Create an index vector for each term. */
    this.indexReader = IndexReader.open(indexDir);
    Random random = new Random();

    /* Set parameters for type of index */
		/* TODO(dwiddows): Check if we need any extra internal state for
		 * this or if it's OK to take this out.

    if (BuildPositionalIndex.indexType == equals("permutation"))
    	this.permute = true;
    else if (BuildPositionalIndex.indexType.equals("directional"))
    	this.directional = true;
		*/
    
    // Check that the Lucene index contains Term Positions.
    java.util.Collection fields_with_positions =
			indexReader.getFieldNames(IndexReader.FieldOption.TERMVECTOR_WITH_POSITION);
    if (fields_with_positions.isEmpty()) {
			System.err.println("Term-term indexing requires a Lucene index containing TermPositionVectors");
			System.err.println("Try rebuilding Lucene index using pitt.search.lucene.IndexFilePositions");
			System.exit(0);
		}
    
    this.indexVectors = new VectorStoreSparseRAM();
    this.termVectors = new VectorStoreRAM();

    // Iterate through an enumeration of terms and create termVector table.
    System.err.println("Creating term vectors ...");
    TermEnum terms = this.indexReader.terms();
    int tc = 0;

    while(terms.next()){
			Term term = terms.term();

			// Skip terms that don't pass the filter.
			if (!termFilter(terms.term())) {
				continue;
			}
			tc++;
			float[] termVector = new float[ObjectVector.vecLength];
			short[] indexVector =  VectorUtils.generateRandomVector(seedLength, random);

			// Place each term vector in the vector store.
			this.termVectors.putVector(term.text(), termVector);

			// Do the same for random index vectors.
			this.indexVectors.putVector(term.text(), indexVector);
    }
    System.err.println("There are " + tc + " terms (and " + indexReader.numDocs() + " docs)");


		/* Iterate through documents. For each term, add term index vector
     * for any term occurring within a window of size windowSize such
     * that for example if windowSize = 5 with the window over the
     * phrase "your life is your life" the index vectors for terms
     * "your" and "life" would each be added to the term vector for
     * "is" twice.
     */
		int numdocs = this.indexReader.numDocs();

		for (int dc = 0; dc < numdocs; ++dc) {
			/* output progress counter */
			if (( dc % 10000 == 0 ) || ( dc < 10000 && dc % 1000 == 0 )) {
				System.err.print(dc + " ... ");
			}

			/* TermPositionVectors contain arrays of (1) terms as text (2)
			 * term frequencies and (3) term positions within a
			 * document. The index of a particular term within this array
			 * will be referred to as the 'local index' in comments.
			 */
			TermPositionVector vex = 	(TermPositionVector) indexReader.getTermFreqVector(dc, "contents");
			if (vex !=null) {
				int[] freqs = vex.getTermFrequencies();
					
				/** find number of positions in document (across all terms)**/
				int numwords = freqs.length;
				int numpositions = 0;
				for (int i = 0; i < numwords; ++i) {
					numpositions += freqs[i];
				}

				/** create index with one space for each position **/
				short[] positions = new short[numpositions];
				String[] docterms = vex.getTerms();

				/** create local random index and term vectors for relevant terms**/
				short[][] localindexvectors = new short[numwords][seedLength];
				float[][] localtermvectors = new float[numwords][ObjectVector.vecLength];

				for (short tcn = 0; tcn < numwords; ++tcn)	{ 
					/** insert local term indices in position vector   **/
					int[] posns = vex.getTermPositions(tcn); //  get all positions of term in document
					for (int pc = 0; pc < posns.length; ++pc)	{
						//  set position of index vector to local
						//  (document-specific) index of term in this position
						positions[posns[pc]] = tcn;
					}

					// Only terms that have passed the term filter are included in the VectorStores.
					if (this.indexVectors.getVector(docterms[tcn]) != null) {
						/** retrieve relevant random index vectors**/
						localindexvectors[tcn] = indexVectors.getSparseVector(docterms[tcn]);
						/** retrieve the float[] arrays of relevant term vectors **/
						localtermvectors[tcn] = termVectors.getVector(docterms[tcn]);
					}
				}

				/** Iterate through positions adding index vectors of terms
				 *  occurring within window to term vector for focus term
				 **/
				int w2 = windowSize / 2;
				for (int p = 0; p < positions.length; ++p) {
					int focusposn = p;
					int focusterm = positions[focusposn];
					int windowstart = Math.max(0, p - w2);
					int windowend = Math.min(focusposn + w2, positions.length - 1);

					/* add random vector (in condensed (signed index + 1)
					 * representation) to term vector by adding -1 or +1 to the
					 * location (index - 1) according to the sign of the index.
					 * (The -1 and +1 are necessary because there is no signed
					 * version of 0, so we'd have no way of telling that the
					 * zeroth position in the array should be plus or minus 1.)
					 * See also generateRandomVector method below.
					 */

					for (int w = windowstart; w < focusposn; w++)	{
						int coterm = positions[w];
						/*
						 * calculate permutation required for either Sahlgren (2008) implementation
						 * encoding word order, or encoding direction as in Burgess and Lund's HAL
						 */
						short[] localindex = localindexvectors[coterm].clone(); 
						if (this.positionalIndexType == BuildPositionalIndex.IndexType.PERMUTATION) {
							int permutation = w - focusposn;
							VectorUtils.permuteSparseVector(localindex, permutation);
						} else if (this.positionalIndexType == BuildPositionalIndex.IndexType.DIRECTIONAL) {
							VectorUtils.permuteSparseVector(localindex, -1);
						}

						/* docterms[coterm] contains the term in position[w] in this document */
						if (this.indexVectors.getVector(docterms[coterm]) != null) {
							for (int i = 0; i < seedLength; ++i) {
								short index = localindex[i];
								localtermvectors[focusterm][Math.abs(index) - 1] +=  Math.signum(index);
							}
						}
					}

					for (int w = focusposn + 1; w <= windowend; w++) {
						int coterm = positions[w];
						/*
						 * calculate permutation required for either Sahlgren (2008) implementation
						 * encoding word order, or encoding direction as in Burgess and Lund's HAL
						 */
						short[] localindex = localindexvectors[coterm].clone(); 
						if (this.positionalIndexType == BuildPositionalIndex.IndexType.PERMUTATION) {
							int permutation = w - focusposn;
							VectorUtils.permuteSparseVector(localindex, permutation);
						} else if (this.positionalIndexType == BuildPositionalIndex.IndexType.DIRECTIONAL) {
							VectorUtils.permuteSparseVector(localindex, -1);
						}
						
						if (this.indexVectors.getVector(docterms[coterm]) != null) { 
							for  (int i = 0; i < seedLength; ++i) {
								short index = localindex[i];
								localtermvectors[focusterm][Math.abs(index) - 1] +=  Math.signum(index);
							}
						}
					}
				}
			}
		}

		System.err.println("\nCreated " + termVectors.getNumVectors() + " term vectors ...");
		System.err.println("\nNormalizing term vectors");
		Enumeration e = termVectors.getAllVectors();
		while (e.hasMoreElements())	{	
			ObjectVector temp = (ObjectVector) e.nextElement();
			float[] next = temp.getVector();
			next = VectorUtils.getNormalizedVector(next);
			temp.setVector(next);
		}

		// If building a permutation index, these need to be written out to be reused.
		if (positionalIndexType == BuildPositionalIndex.IndexType.PERMUTATION) {
			String randFile = "randomvectors.bin";
			System.err.println("\nWriting random vectors to "+randFile);
			new VectorStoreWriter().WriteVectors(randFile, this.indexVectors);
		}
	}

	// Basic VectorStore interface stuff now implemented through termVectors.
	public float[] getVector(Object term) {
		return termVectors.getVector(term);
	}
	
  public Enumeration getAllVectors() {
    return termVectors.getAllVectors();
  }
	
	public int getNumVectors() {
		return termVectors.getNumVectors();
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
