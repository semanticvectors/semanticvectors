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

import java.util.Arrays;
import java.util.Enumeration;
import java.util.Random;
import java.io.File;
import java.io.IOException;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.index.TermPositionVector;
import org.apache.lucene.store.FSDirectory;

/**
 * Implementation of vector store that creates term by term
 * co-occurrence vectors by iterating through all the documents in a
 * Lucene index.  This class implements a sliding context window
 * approach, as used by Burgess and Lund (HAL) and Schutze amongst
 * others Uses a sparse representation for the basic document vectors,
 * which saves considerable space for collections with many individual
 * documents.
 *
 * @author Trevor Cohen, Dominic Widdows.
 */
public class TermTermVectorsFromLucene implements VectorStore {
  private boolean retraining = false;
  private VectorStoreRAM termVectors;
  private VectorStore indexVectors;
  private IndexReader indexReader;
  private int seedLength;
  private String[] fieldsToIndex;
  private int minFreq;
  private int windowSize;
  private float[][] localindexvectors;
  private short[][] localsparseindexvectors;
  private LuceneUtils lUtils;
  private int nonAlphabet;
  
  private int dimension;
  private String positionalmethod;

  static final short NONEXISTENT = -1;

  /**
   * @return The object's indexReader.
   */
  public IndexReader getIndexReader(){ return this.indexReader; }

  /**
   * @return The object's basicTermVectors.
   */
  public VectorStore getBasicTermVectors(){ return this.termVectors; }

  public String[] getFieldsToIndex(){ return this.fieldsToIndex; }

  // Basic VectorStore interface methods implemented through termVectors.
  public float[] getVector(Object term) {
    return termVectors.getVector(term);
  }

  public Enumeration<ObjectVector> getAllVectors() {
    return termVectors.getAllVectors();
  }

  public int getNumVectors() {
    return termVectors.getNumVectors();
  }

  /**
   * This constructor uses all the values passed as arguments and in addition
   * the parameters dimension and positionalmethod from Flags.
   * @param indexDir Directory containing Lucene index.
   * @param seedLength Number of +1 or -1 entries in basic
   * vectors. Should be even to give same number of each.
   * @param minFreq The minimum term frequency for a term to be indexed.
   * @param nonAlphabet
   * @param windowSize The size of the sliding context window.
   * @param basicTermVectors
   * @param fieldsToIndex These fields will be indexed.
   * @throws IOException
   * @throws RuntimeException 
   * @deprecated use the constructor that explicitly specifies all needed
   * values from Flags.
   */
  public TermTermVectorsFromLucene(String indexDir,
                                   int seedLength,
                                   int minFreq,
                                   int nonAlphabet,
                                   int windowSize,
                                   VectorStore basicTermVectors,
                                   String[] fieldsToIndex)
    throws IOException, RuntimeException {
    this.dimension = Flags.dimension;
    this.positionalmethod = Flags.positionalmethod;
    doConstructor(indexDir,seedLength,minFreq,nonAlphabet,
        windowSize,basicTermVectors,fieldsToIndex);
  }
  /**
   * This constructor uses only the values passed, no parameters from Flag.
   * @param indexDir Directory containing Lucene index.
   * @dimension number of dimensions to use for the vectors
   * @param dimension
   * @param seedLength Number of +1 or -1 entries in basic
   * vectors. Should be even to give same number of each.
   * @param minFreq The minimum term frequency for a term to be indexed.
   * @param nonAlphabet 
   * @param windowSize The size of the sliding context window.
   * @param positionalmethod 
   * @param basicTermVectors 
   * @param fieldsToIndex These fields will be indexed.
   * @throws IOException
   * @throws RuntimeException 
   */
  public TermTermVectorsFromLucene(String indexDir,
                                   int dimension,
                                   int seedLength,
                                   int minFreq,
                                   int nonAlphabet,
                                   int windowSize,
                                   String positionalmethod,
                                   VectorStore basicTermVectors,
                                   String[] fieldsToIndex)
    throws IOException, RuntimeException {
    this.dimension = dimension;
    this.positionalmethod = positionalmethod;
    doConstructor(indexDir,seedLength,minFreq,nonAlphabet,
        windowSize,basicTermVectors,fieldsToIndex);
  }
  private void doConstructor(String indexDir,
                                   int seedLength,
                                   int minFreq,
                                   int nonAlphabet,
                                   int windowSize,
                                   VectorStore basicTermVectors,
                                   String[] fieldsToIndex)
    throws IOException, RuntimeException {

    this.minFreq = minFreq;
    this.nonAlphabet = nonAlphabet;
    this.fieldsToIndex = fieldsToIndex;
    this.seedLength = seedLength;
    this.windowSize = windowSize;

    // Check that the Lucene index contains Term Positions.
    LuceneUtils.CompressIndex(indexDir);
    this.indexReader = IndexReader.open(FSDirectory.open(new File(indexDir)));
    java.util.Collection fields_with_positions =
      indexReader.getFieldNames(IndexReader.FieldOption.TERMVECTOR_WITH_POSITION);
    if (fields_with_positions.isEmpty()) {
      System.err.println("Term-term indexing requires a Lucene index containing TermPositionVectors");
      System.err.println("Try rebuilding Lucene index using pitt.search.lucene.IndexFilePositions");
      throw new IOException("Lucene indexes not built correctly.");
    }
    lUtils = new LuceneUtils(indexDir);

    // If basicTermVectors was passed in, set state accordingly.
    if (basicTermVectors != null) { 
      retraining = true;
      this.indexVectors = basicTermVectors;
      System.out.println("Reusing basic term vectors; number of terms: "
			 + basicTermVectors.getNumVectors());
    } else {
      this.indexVectors = new VectorStoreSparseRAM();
    }
    Random random = new Random();
    this.termVectors = new VectorStoreRAM();

    // Iterate through an enumeration of terms and allocate termVector memory. 
    // If not retraining, create random elemental vectors as well.
    System.err.println("Creating basic term vectors ...");
    TermEnum terms = this.indexReader.terms();
    int tc = 0;
    while(terms.next()){
      Term term = terms.term();
      // Skip terms that don't pass the filter.
      if (!lUtils.termFilter(terms.term(), fieldsToIndex))  {
        continue;
      }
      tc++;
      float[] termVector = new float[dimension];
      // Place each term vector in the vector store.
      this.termVectors.putVector(term.text(), termVector);
      // Do the same for random index vectors unless retraining with trained term vectors
      if (!retraining) {
	short[] indexVector =  VectorUtils.generateRandomVector(seedLength, random);
	((VectorStoreSparseRAM) this.indexVectors).putVector(term.text(), indexVector);
      }
    }
    System.err.println("There are " + tc + " terms (and " + indexReader.numDocs() + " docs)");

    // Iterate through documents.
    int numdocs = this.indexReader.numDocs();
    for (int dc = 0; dc < numdocs; ++dc) {
      /* output progress counter */
    	 if ((dc % 10000 == 0) || (dc < 10000 && dc % 1000 == 0)) {
        System.err.print(dc + " ... ");
      }
    
    	 try {
    	 
      for (String field: fieldsToIndex) {
	TermPositionVector vex = (TermPositionVector) indexReader.getTermFreqVector(dc, field);
	if (vex != null) processTermPositionVector(vex);
      }
    	 } 		
    	catch (Exception e)
    	{
    		System.err.println("\nFailed to process document "+indexReader.document(dc).get("path")+"\n");
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
    if ((positionalmethod.equals("permutation") || (positionalmethod.equals("permutation_plus_basic") ))&& !retraining) {
      String randFile = "randomvectors.bin";
      System.err.println("\nWriting random vectors to "+randFile);
      System.err.println("\nNormalizing random vectors");
      Enumeration f = indexVectors.getAllVectors();
      while (f.hasMoreElements())	{
        ObjectVector temp = (ObjectVector) f.nextElement();
        float[] next = temp.getVector();
        next = VectorUtils.getNormalizedVector(next);
        temp.setVector(next);
      }
      new VectorStoreWriter().WriteVectors(randFile, this.indexVectors);
    }
  }

  /**
     For each term, add term index vector
     * for any term occurring within a window of size windowSize such
     * that for example if windowSize = 5 with the window over the
     * phrase "your life is your life" the index vectors for terms
     * "your" and "life" would each be added to the term vector for
     * "is" twice.
     *
     * TermPositionVectors contain arrays of (1) terms as text (2)
     * term frequencies and (3) term positions within a
     * document. The index of a particular term within this array
     * will be referred to as the 'local index' in comments.
     */
  private void processTermPositionVector(TermPositionVector vex) throws java.lang.ArrayIndexOutOfBoundsException {
    int[] freqs = vex.getTermFrequencies();

    // Find number of positions in document (across all terms).
    int numwords = freqs.length;
    int numpositions = 0;
    for (short tcn = 0; tcn < numwords; ++tcn) {
      int[] posns = vex.getTermPositions(tcn);
      for (int pc = 0; pc < posns.length; ++pc) {
        numpositions = Math.max(numpositions, posns[pc]);
      }
    }
		numpositions += 1; //convert from zero-based index to count

    // Create local random index and term vectors for relevant terms.
    if (retraining)
      localindexvectors = new float[numwords][dimension];
    else
      localsparseindexvectors = new short[numwords][seedLength];

    float[][] localtermvectors = new float[numwords][dimension];

    // Create index with one space for each position.
    short[] positions = new short[numpositions];
    Arrays.fill(positions, NONEXISTENT);
    String[] docterms = vex.getTerms();

    for (short tcn = 0; tcn < numwords; ++tcn) {
      // Insert local term indices in position vector.
      int[] posns = vex.getTermPositions(tcn);  // Get all positions of term in document
      for (int pc = 0; pc < posns.length; ++pc) {
	// Set position of index vector to local
	// (document-specific) index of term in this position.
	int position = posns[pc];
	positions[position] = tcn;
      }

      // Only terms that have passed the term filter are included in the VectorStores.
      if (this.indexVectors.getVector(docterms[tcn]) != null) {
	// Retrieve relevant random index vectors.
	if (retraining)
	  localindexvectors[tcn] = indexVectors.getVector(docterms[tcn]);
	else
	  localsparseindexvectors[tcn] =
	    ((VectorStoreSparseRAM) indexVectors).getSparseVector(docterms[tcn]);

	// Retrieve the float[] arrays of relevant term vectors.
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
      if (focusterm == NONEXISTENT) continue;
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
      for (int w = windowstart; w <= windowend; w++) {
	if (w == focusposn) continue;
	int coterm = positions[w];
        if (coterm == NONEXISTENT) continue;
	// calculate permutation required for either Sahlgren (2008) implementation
	// encoding word order, or encoding direction as in Burgess and Lund's HAL
	float[] localindex= new float[0];
	short[] localsparseindex = new short[0];
	if (retraining) localindex = localindexvectors[coterm].clone();
	else localsparseindex = localsparseindexvectors[coterm].clone();
	
	//combine 'content' and 'order' information - first add the unpermuted vector
	if (positionalmethod.equals("permutation_plus_basic"))
	{
		// docterms[coterm] contains the term in position[w] in this document.
		if (this.indexVectors.getVector(docterms[coterm]) != null && localtermvectors[focusterm] != null) {
		  if (retraining)
		    VectorUtils.addVectors(localtermvectors[focusterm],localindex,1);
		  else
		    VectorUtils.addVectors(localtermvectors[focusterm],localsparseindex,1);
		}
	}

	if (positionalmethod.equals("permutation") || positionalmethod.equals("permutation_plus_basic")) {
	  int permutation = w - focusposn;
	  if (retraining)
	    localindex = VectorUtils.permuteVector(localindex , permutation);
	  else localsparseindex =  VectorUtils.permuteVector(localsparseindex, permutation);
	} else if (positionalmethod.equals("directional")) {
	  if (retraining)
	    localindex = VectorUtils.permuteVector(localindex, new Float(Math.signum(w-focusposn)).intValue());
	  else localsparseindex = VectorUtils.permuteVector(localsparseindex, new Float(Math.signum(w-focusposn)).intValue());
	}

	// docterms[coterm] contains the term in position[w] in this document.
	if (this.indexVectors.getVector(docterms[coterm]) != null && localtermvectors[focusterm] != null) {
	  if (retraining)
	    VectorUtils.addVectors(localtermvectors[focusterm],localindex,1);
	  else
	    VectorUtils.addVectors(localtermvectors[focusterm],localsparseindex,1);
	}
      }
    }
  }

}
