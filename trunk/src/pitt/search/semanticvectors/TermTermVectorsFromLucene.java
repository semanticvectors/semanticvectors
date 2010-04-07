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
import java.io.File;
import java.io.IOException;
import java.lang.RuntimeException;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.index.TermPositionVector;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

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
  private boolean retraining = false;
  private VectorStoreRAM termVectors;
  private VectorStore indexVectors;
  private IndexReader indexReader;
  private int seedLength;
  private String[] fieldsToIndex;
  private int minFreq;
  private float[][] localindexvectors;
  private short[][] localsparseindexvectors;
  private LuceneUtils lUtils;
  private int nonAlphabet;

  /**
   * @return The object's indexReader.
   */
  public IndexReader getIndexReader(){ return this.indexReader; }

  /**
   * @return The object's basicTermVectors.
   */
  public VectorStore getBasicTermVectors(){ return this.termVectors; }

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
                                   int nonAlphabet,
                                   int windowSize,
                                   VectorStore basicTermVectors,
                                   String[] fieldsToIndex)
    throws IOException, RuntimeException {

    this.minFreq = minFreq;
    this.nonAlphabet = nonAlphabet;
    this.fieldsToIndex = fieldsToIndex;
    this.seedLength = seedLength;

    /* This small preprocessing step makes sure that the Lucene index
     * is optimized to use contiguous integers as identifiers.
     * Otherwise exceptions can occur if document id's are greater
     * than indexReader.numDocs().
     */
    IndexWriter compressor = new IndexWriter(
        FSDirectory.open(new File(indexDir)),
        new StandardAnalyzer(Version.LUCENE_30),
        false,
        MaxFieldLength.UNLIMITED);
    compressor.optimize();
    compressor.close();

    // Create an index vector for each term.
    this.indexReader = IndexReader.open(FSDirectory.open(new File(indexDir)));

    // Create LuceneUtils Class to filter terms
    lUtils = new LuceneUtils(indexDir);

    Random random = new Random();

    // Check that the Lucene index contains Term Positions.
    java.util.Collection fields_with_positions =
      indexReader.getFieldNames(IndexReader.FieldOption.TERMVECTOR_WITH_POSITION);
    if (fields_with_positions.isEmpty()) {
      System.err.println("Term-term indexing requires a Lucene index containing TermPositionVectors");
      System.err.println("Try rebuilding Lucene index using pitt.search.lucene.IndexFilePositions");
      throw new IOException("Lucene indexes not built correctly.");
    }

    this.indexVectors = new VectorStoreSparseRAM();
    this.termVectors = new VectorStoreRAM();

    // Iterate through an enumeration of terms and create basic termVector table.
    // Derived term vectors will be linear combinations of these.
    System.err.println("Creating basic term vectors ...");

    // Check that basicDocVectors is the right size.
    if (basicTermVectors != null)
      { retraining = true;
        this.indexVectors = basicTermVectors;
        System.out.println("Reusing basic term vectors; number of terms: "
                           + basicTermVectors.getNumVectors());
      }
    TermEnum terms = this.indexReader.terms();
    int tc = 0;

    while(terms.next()){
      Term term = terms.term();

      // Skip terms that don't pass the filter.
      if (!lUtils.termFilter(terms.term(), fieldsToIndex, nonAlphabet, minFreq))  {
        continue;
      }
      tc++;
      float[] termVector = new float[Flags.dimension];
      short[] indexVector =  VectorUtils.generateRandomVector(seedLength, random);

      // Place each term vector in the vector store.
      this.termVectors.putVector(term.text(), termVector);

      // Do the same for random index vectors unless retraining with trained term vectors
      if (!retraining)
        ((VectorStoreSparseRAM) this.indexVectors).putVector(term.text(), indexVector);
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
      if ((dc % 10000 == 0) || (dc < 10000 && dc % 1000 == 0)) {
        System.err.print(dc + " ... ");
      }

      /* TermPositionVectors contain arrays of (1) terms as text (2)
       * term frequencies and (3) term positions within a
       * document. The index of a particular term within this array
       * will be referred to as the 'local index' in comments.
       */
      TermPositionVector vex = 	(TermPositionVector) indexReader.getTermFreqVector(dc, "contents");
      if (vex != null) {
        int[] freqs = vex.getTermFrequencies();

        // Find number of positions in document (across all terms).
        int numwords = freqs.length;
        int numpositions = 0;
        for (int i = 0; i < numwords; ++i) {
          numpositions += freqs[i];
        }

        // Create local random index and term vectors for relevant terms.
        if (retraining)
          localindexvectors = new float[numwords][Flags.dimension];
        else
          localsparseindexvectors = new short[numwords][seedLength];

        float[][] localtermvectors = new float[numwords][Flags.dimension];

        // Create index with one space for each position.
        short[] positions = new short[numpositions];
        String[] docterms = vex.getTerms();

        for (short tcn = 0; tcn < numwords; ++tcn) {
          // Insert local term indices in position vector.
          int[] posns = vex.getTermPositions(tcn);  // Get all positions of term in document
          for (int pc = 0; pc < posns.length; ++pc) {
            // Set position of index vector to local
            // (document-specific) index of term in this position.

            // TODO(This contains printf debugging code - fix and get rid of this.
            System.err.print("Here with pc= " + pc + " out of " + posns.length);
            int position = posns[pc];
            System.err.print(" ... OK so far ...");
            positions[position] = tcn;
            System.err.println(" added.");
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

          for (int w = windowstart; w <= windowend; w++)	{
            if (w == focusposn) continue;
            int coterm = positions[w];
            /*
             * calculate permutation required for either Sahlgren (2008) implementation
             * encoding word order, or encoding direction as in Burgess and Lund's HAL
             */
            float[] localindex= new float[0];
            short[] localsparseindex = new short[0];
            if (retraining) localindex = localindexvectors[coterm].clone();
            else localsparseindex = localsparseindexvectors[coterm].clone();

            if (Flags.positionalmethod.equals("permutation")) {
              int permutation = w - focusposn;
              if (retraining)
                localindex = VectorUtils.permuteVector(localindex , permutation);
              else localsparseindex =  VectorUtils.permuteVector(localsparseindex, permutation);
            } else if (Flags.positionalmethod.equals("directional")) {
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
    if (Flags.positionalmethod.equals("permutation") && !retraining) {
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
}
