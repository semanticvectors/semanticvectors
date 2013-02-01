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
import java.util.logging.Logger;
import java.io.File;
import java.io.IOException;

import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.index.TermPositionVector;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.ReaderUtil;

import pitt.search.semanticvectors.hashing.Bobcat;
import pitt.search.semanticvectors.vectors.PermutationUtils;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorType;

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
  private static final Logger logger = Logger.getLogger(
      TermTermVectorsFromLucene.class.getCanonicalName());

  // TODO: Refactor to get other fields from FlagConfig as appropriate.
  private FlagConfig flagConfig;
  
  private int dimension;
  private VectorType vectorType;
  private boolean retraining = false;
  private VectorStoreRAM termVectors;
  private VectorStore indexVectors;
  private String luceneIndexDir;
  private IndexReader luceneIndexReader;
  private int seedLength;
  private String[] fieldsToIndex;
  private int minFreq;
  private int maxFreq;
  private int maxNonAlphabet;
  private boolean filterNumbers;
  private int windowSize;
  private Vector[] localindexvectors;
  private LuceneUtils lUtils;

  private String positionalmethod;
  
  /**
   * Used to store permutations we'll use in training.  If positional method is one of the
   * permutations, this contains the shift for all the focus positions.
   */
  private int[][] permutationCache;

  static final short NONEXISTENT = -1;


  @Override
  public VectorType getVectorType() { return vectorType; }
  
  @Override
  public int getDimension() { return dimension; }
  
  /**
   * @return The object's indexReader.
   */
  public IndexReader getIndexReader(){ return this.luceneIndexReader; }

  /**
   * @return The object's basicTermVectors.
   */
  public VectorStore getBasicTermVectors(){ return this.termVectors; }

  public String[] getFieldsToIndex(){ return this.fieldsToIndex; }

  // Basic VectorStore interface methods implemented through termVectors.
  public Vector getVector(Object term) {
    return termVectors.getVector(term);
  }

  public Enumeration<ObjectVector> getAllVectors() {
    return termVectors.getAllVectors();
  }

  public int getNumVectors() {
    return termVectors.getNumVectors();
  }

  /**
   * This constructor uses only the values passed, no parameters from Flag.
   * @param luceneIndexDir Directory containing Lucene index.
   * @param vectorType type of vector
   * @param dimension number of dimension to use for the vectors
   * @param seedLength Number of +1 or -1 entries in basic
   * vectors. Should be even to give same number of each.
   * @param minFreq The minimum term frequency for a term to be indexed.
   * @param maxFreq The minimum term frequency for a term to be indexed.
   * @param maxNonAlphabet
   * @param filterNumbers
   * @param windowSize The size of the sliding context window.
   * @param positionalmethod
   * @param indexVectors
   * @param fieldsToIndex These fields will be indexed.
   * @throws IOException
   */
  public TermTermVectorsFromLucene(
      FlagConfig flagConfig,
      String luceneIndexDir, VectorType vectorType, int dimension, int seedLength,
      int minFreq, int maxFreq, int maxNonAlphabet, boolean filterNumbers, int windowSize, String positionalmethod,
      VectorStore indexVectors, String[] fieldsToIndex) throws IOException {
    this.flagConfig = flagConfig;
    this.luceneIndexDir = luceneIndexDir;
    this.vectorType = vectorType;
    this.dimension = dimension;
    this.positionalmethod = positionalmethod;
    this.minFreq = minFreq;
    this.maxFreq = maxFreq;
    this.maxNonAlphabet = maxNonAlphabet;
    this.filterNumbers = filterNumbers;
    this.fieldsToIndex = fieldsToIndex;
    this.seedLength = seedLength;
    this.windowSize = windowSize;
    this.indexVectors = indexVectors;

    // TODO(widdows): This clearly demonstrates the need for catching flag values and
    // turning them into enums earlier in the pipeline. This would be a very silly place to
    // have a programming typo cause an error!
    if (positionalmethod.equals("permutation")
        || positionalmethod.equals("permutation_plus_basic")) {
      initializePermutations();}
      else if (positionalmethod.equals("directional")) {
      initializeDirectionalPermutations();	  
    }
    trainTermTermVectors();
  }

  /**
   * Initialize all permutations that might be used.
   */
  private void initializePermutations() {    
    permutationCache =
      new int[windowSize][PermutationUtils.getPermutationLength(vectorType, dimension)];
    for (int i = 0; i < windowSize; ++i) {
      permutationCache[i] = PermutationUtils.getShiftPermutation(
          vectorType, dimension, i - windowSize/2);
    }
  }
  
  /**
   * Initialize all permutations that might be used (i.e +1 and -1).
   */
  private void initializeDirectionalPermutations() {    
    permutationCache =
      new int[2][PermutationUtils.getPermutationLength(vectorType, dimension)];
      
    permutationCache[0] = PermutationUtils.getShiftPermutation(
          vectorType, dimension, -1);
    
    permutationCache[1] = PermutationUtils.getShiftPermutation(
            vectorType, dimension, 1);
    
  }
  
  private void trainTermTermVectors() throws IOException, RuntimeException {
    // Check that the Lucene index contains Term Positions.
    LuceneUtils.compressIndex(luceneIndexDir);
    this.luceneIndexReader = IndexReader.open(FSDirectory.open(new File(luceneIndexDir)));
    FieldInfos fieldsWithPositions = ReaderUtil.getMergedFieldInfos(luceneIndexReader);
    if (!fieldsWithPositions.hasVectors()) {
      throw new IOException(
          "Term-term indexing requires a Lucene index containing TermPositionVectors."
          + "\nTry rebuilding Lucene index using pitt.search.lucene.IndexFilePositions");
    }
    lUtils = new LuceneUtils(flagConfig);

    // If basicTermVectors was passed in, set state accordingly.
    if (indexVectors != null) {
      retraining = true;
      VerbatimLogger.info("Reusing basic term vectors; number of terms: "
          + indexVectors.getNumVectors() + "\n");
    } else {
      this.indexVectors = new VectorStoreRAM(flagConfig);
    }
    Random random = new Random();
    this.termVectors = new VectorStoreRAM(flagConfig);

    // Iterate through an enumeration of terms and allocate initial term vectors.
    // If not retraining, create random elemental vectors as well.
    TermEnum terms = this.luceneIndexReader.terms();
    int tc = 0;
    while(terms.next()) {
      Term term = terms.term();
      // Skip terms that don't pass the filter.
      if (!lUtils.termFilter(terms.term())) {
        continue;
      }
      tc++;
      Vector termVector = VectorFactory.createZeroVector(vectorType, dimension);
      // Place each term vector in the vector store.
      this.termVectors.putVector(term.text(), termVector);
      // Do the same for random index vectors unless retraining with trained term vectors
      if (!retraining) {
    	  
    	if (flagConfig.deterministicvectors())
    	  random.setSeed(Bobcat.asLong(term.text()));
    		
        Vector indexVector =  VectorFactory.generateRandomVector(
            vectorType, dimension, seedLength, random);
        ((VectorStoreRAM) this.indexVectors).putVector(term.text(), indexVector);
      }
    }
    VerbatimLogger.info("Created basic term vectors for " + tc + " terms (and "
        + luceneIndexReader.numDocs() + " docs).\n");

    // Iterate through documents.
    int numdocs = this.luceneIndexReader.numDocs();
    for (int dc = 0; dc < numdocs; ++dc) {
      // Output progress counter.
      if ((dc % 10000 == 0) || (dc < 10000 && dc % 1000 == 0)) {
        VerbatimLogger.info("Processed " + dc + " documents ... ");
      }

      for (String field: fieldsToIndex) {
        TermPositionVector vex = (TermPositionVector) luceneIndexReader.getTermFreqVector(dc, field);
        if (vex != null) processTermPositionVector(vex);
      }
    }

    VerbatimLogger.info("Created " + termVectors.getNumVectors() + " term vectors ...\n");
    VerbatimLogger.info("Normalizing term vectors.\n");
    Enumeration<ObjectVector> e = termVectors.getAllVectors();
    while (e.hasMoreElements())	{
      e.nextElement().getVector().normalize();
    }

    // If building a permutation index, these need to be written out to be reused.
    //
    // TODO(widdows): It is odd to do this here while not writing out the semantic
    // term vectors here.  We should redesign this.
    if ((positionalmethod.equals("permutation") || (positionalmethod.equals("permutation_plus_basic"))) 
        && !retraining) {
      VerbatimLogger.info("Normalizing and writing random vectors to " + flagConfig.elementalvectorfile() + "\n");
      Enumeration<ObjectVector> f = indexVectors.getAllVectors();
      while (f.hasMoreElements())	{
        f.nextElement().getVector().normalize();
      }
      VectorStoreWriter.writeVectors(flagConfig.elementalvectorfile(), flagConfig, this.indexVectors);
    }
  }

  /**
   * For each term, add term index vector
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
  private void processTermPositionVector(TermPositionVector vex)
      throws ArrayIndexOutOfBoundsException {
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
    localindexvectors = new Vector[numwords];
    Vector[] localtermvectors = new Vector[numwords];

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
        localindexvectors[tcn] = indexVectors.getVector(docterms[tcn]);

        // Retrieve relevant term vectors.
        localtermvectors[tcn] = termVectors.getVector(docterms[tcn]);
      }
    }

    /** Iterate through positions adding index vectors of terms
     *  occurring within window to term vector for focus term
     **/
    int windowRadius = windowSize / 2;
    for (int focusposn = 0; focusposn < positions.length; ++focusposn) {
      int focusterm = positions[focusposn];
      if (focusterm == NONEXISTENT) continue;
      int windowstart = Math.max(0, focusposn - windowRadius);
      int windowend = Math.min(focusposn + windowRadius, positions.length - 1);

      for (int cursor = windowstart; cursor <= windowend; cursor++) {
        if (cursor == focusposn) continue;
        int coterm = positions[cursor];
        if (coterm == NONEXISTENT) continue;
        
        if (this.indexVectors.getVector(docterms[coterm]) == null
            || localtermvectors[focusterm] == null) {
          continue;
        }
        
        float globalweight = 1;
        if (flagConfig.termweight().equals("logentropy")) {
            //local weighting: 1+ log (local frequency)
            Term term = new Term(vex.getField(), docterms[coterm]);
            globalweight = globalweight * lUtils.getEntropy(term);
          }
          else 
          if (flagConfig.termweight().equals("idf")) {
        	  
        	  	Term term = new Term(vex.getField(), docterms[coterm]);
                globalweight =  globalweight * lUtils.getIDF(term);
        	  	
        	  	}	
        
        // calculate permutation required for either Sahlgren (2008) implementation
        // encoding word order, or encoding direction as in Burgess and Lund's HAL
        if (positionalmethod.equals("permutation_plus_basic")
            || positionalmethod.equals("basic")) {
          // docterms[coterm] contains the term in position[w] in this document.
          localtermvectors[focusterm].superpose(localindexvectors[coterm], globalweight, null);
        }
        if ((positionalmethod.equals("permutation"))
            || (positionalmethod.equals("permutation_plus_basic"))) {
          int[] permutation = permutationCache[cursor - focusposn + windowRadius];
          localtermvectors[focusterm].superpose(localindexvectors[coterm], globalweight, permutation);
        } else if (positionalmethod.equals("directional")) {
        	 int[] permutation = permutationCache[(int) Math.max(0,Math.signum(cursor - focusposn))];
             localtermvectors[focusterm].superpose(localindexvectors[coterm], globalweight, permutation);
        		
        }
      }
    }
  }
}
