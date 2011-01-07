/**
   Copyright (c) 2009, Queensland University of Technology

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

package qut.beagle;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Random;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.index.TermPositionVector;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import pitt.search.semanticvectors.Flags;
import pitt.search.semanticvectors.ObjectVector;
import pitt.search.semanticvectors.VectorStore;
import pitt.search.semanticvectors.VectorStoreRAM;
import pitt.search.semanticvectors.VectorStoreWriter;
import cern.colt.matrix.tfcomplex.impl.DenseFComplexMatrix1D;
import cern.colt.matrix.tfloat.impl.DenseFloatMatrix1D;

/**
 * Implementation of vector store that creates term vectors by superposing ngram vectors
 * according to a hybrid-BEAGLE model. The BEAGLE method by Jones and Mewhort captures syntactic
 * structure within text. The method implemented here is a hybrid in that several performance
 * optimizations have been implemented, including a slightly different method for encoding order
 * within ngrams, which, non-the-less retain the essential BEAGLE idea of convolving term vectors
 * for constructing ngrams.
 *
 * This current implementation is dependent on the Parallel Colt version 0.7 Java numerical library for
 * computing fourier transforms for accelerating the calculation of circular convolutions.
 *
 * It is based very loosely on TermTermVectorsFromLucene
 *
 * @author Lance De Vine
 */
public class BeagleNGramVectors implements VectorStore {
  private VectorStoreRAM termVectors;
  private VectorStoreRAM indexVectors;
  private IndexReader indexReader;
  private String[] fieldsToIndex;
  
  // The minimum required frequency of a term for it to be considered as a focus
  // term when constructing term vectors.
  private int minFreqTerm;

  // The minimum required frequency of a term for it to be included as one of
  // the terms within an ngram. This is useful if you want to exclude frequently
  // occuring terms from defining the syntactic structure for a focus term.
  private int minFreqIndex;

  // The maximum number of grams used for constructing ngrams.
  private int numGrams = 3;

  // Size of cache used to store computed FFTs
  private int FFTCacheSize = 5000;

  // The stopwords are used to exclude frequently occuring terms from having
  // their term vectors computed as this will add a lot to computation time.
  // The stop words may have already been removed when the documents were
  // indexed by Lucene, but they may have also been kept if they are needed
  // for creating index vectors.
  private String stopwords = null;

  DenseFloatMatrix1D phi;
  int[] Permute1;
  int[] Permute2;

  BeagleUtils utils;
  BeagleNGramBuilder ngBuilder;
  TermFilter tFilter = null;
  TermFilter iFilter = null;

  /**
   * @return The object's indexReader.
   */
  public IndexReader getIndexReader(){ return this.indexReader; }

  public String[] getFieldsToIndex(){ return this.fieldsToIndex; }

  /**
   * @param indexDir Directory containing Lucene index.
   * @param minFreq The minimum term frequency for a term to be indexed.
   * @param fieldsToIndex These fields will be indexed. If null, all fields will be indexed.
   * @param numGrams The max number of terms in generated ngrams.
   */
  public BeagleNGramVectors(String indexDir, int minFreqTerm, int minFreqIndex,
                            String[] fieldsToIndex, int numGrams, String stopwords )
      throws IOException, RuntimeException
  {

    this.minFreqTerm = minFreqTerm;
    this.minFreqIndex = minFreqIndex;
    this.fieldsToIndex = fieldsToIndex;
    this.numGrams = numGrams;
    this.stopwords = stopwords;

    // Get Beagle Utils and set fft cache size
    utils = BeagleUtils.getInstance();

    ngBuilder = BeagleNGramBuilder.getInstance();
    ngBuilder.initialise();
    ngBuilder.setFFTCacheSize(FFTCacheSize);

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

    /* Create an index vector for each term. */
    this.indexReader = IndexReader.open(FSDirectory.open(new File(indexDir)));
    Random random = new Random();

    this.tFilter = new CustomTermFilter( indexReader, minFreqTerm, stopwords );
    this.iFilter = new TermFreqFilter( indexReader, minFreqIndex );

    // Check that the Lucene index contains Term Positions.
    java.util.Collection fields_with_positions =
        indexReader.getFieldNames(IndexReader.FieldOption.TERMVECTOR_WITH_POSITION);
    if (fields_with_positions.isEmpty()) {
      System.out.println("BEAGLE term indexing requires a Lucene index containing TermPositionVectors");
      System.out.println("Try rebuilding Lucene index using pitt.search.lucene.IndexFilePositions");
      throw new IOException("Lucene indexes not built correctly.");
    }

    this.indexVectors = new VectorStoreRAM(Flags.dimension);
    this.termVectors = new VectorStoreRAM(Flags.dimension);

    // Iterate through an enumeration of terms and create term vectors.
    System.out.println("Creating term vectors ...");
    TermEnum terms = this.indexReader.terms();
    int tc = 0;

    while(terms.next())
    {
      Term term = terms.term();
      tc++;

      // Create random index vectors for terms that pass filter
      if (iFilter.filter( term ))
      {
        float[] indexVector =  utils.generateNormalizedRandomVector();
        this.indexVectors.putVector(term.text(), indexVector);
      }

      // Create zero term vectors for terms that pass filter
      if (tFilter.filter( term ))
      {
        float[] termVector = new float[Flags.dimension];
        this.termVectors.putVector(term.text(), termVector);
      }
    }

    System.out.println("There are " + indexVectors.getNumVectors() + " index vectors and " + termVectors.getNumVectors() + " term vectors.");

    System.out.println("There are " + tc + " terms (and " + indexReader.numDocs() + " docs)");

    /* Iterate through documents. Construct ngrams via convolution
     * and add representations for ngrams to term vector.
     */
    int numdocs = this.indexReader.numDocs();

    //for (int dc = 0; dc < 1000; dc++)
    for (int dc = 0; dc < numdocs; ++dc)
    {
      /* output progress counter */
      if (( dc % 1000 == 0 ) || ( dc < 1000 && dc % 100 == 0 )) {
        System.out.print(dc + "... ");
      }

      /* TermPositionVectors contain arrays of (1) terms as text (2)
       * term frequencies and (3) term positions within a
       * document. The index of a particular term within this array
       * will be referred to as the 'local index' in comments.
       */
      TermPositionVector vex = 	(TermPositionVector) indexReader.getTermFreqVector(dc, "contents");
      if (vex !=null)
      {
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
        DenseFloatMatrix1D[] localindexvectors = new DenseFloatMatrix1D[numwords];
        DenseFloatMatrix1D[] localtermvectors = new DenseFloatMatrix1D[numwords];

        // Initialise the ngram builder
        ngBuilder.initialiseNGrams(numpositions);

        // Go through words in this document
        for (short tcn = 0; tcn < numwords; ++tcn)	{
          /** insert local term indices in position vector   **/
          int[] posns = vex.getTermPositions(tcn); //  get all positions of term in document
          for (int pc = 0; pc < posns.length; ++pc)	{
            //  set position of index vector to local
            //  (document-specific) index of term in this position
            positions[posns[pc]] = tcn;
          }

          // Create local index vectors
          if (this.indexVectors.getVector(docterms[tcn]) != null) {
            /** retrieve relevant random index vectors**/
            localindexvectors[tcn] = new DenseFloatMatrix1D( indexVectors.getVector(docterms[tcn]) );
          }

          // Create local term vectors
          if (this.termVectors.getVector(docterms[tcn]) != null) {
            /** retrieve the relevant term vectors**/
            localtermvectors[tcn] = utils.createZeroVector( Flags.dimension );
          }
        }

        DenseFloatMatrix1D vec1;

        /** Go through positions adding ngram vectors involving the focus term
         *  to the term vector for the focus term
         **/
        for (int p = 0; p < positions.length; ++p)
        {
          int focusposn = p;
          int focusterm = positions[focusposn];
          int start, end;
          int termId;
          boolean canProcess = true;

          // Check that we have a term vector for this term
          if (termVectors.getVector((Object)(docterms[focusterm]))==null)	continue;

          // Construct ngrams of increasing size
          for (int ngSize=2; ngSize<=numGrams; ngSize++ )
          {

            //construct all ngrams of size ngSize
            for (int ng = 1; ng<=ngSize; ng++)
            {
              // Construct ngram
              start = (p-ngSize+ng);
              end = (p+ng);

              if (start<0 || end >=positions.length) continue;

              canProcess = true;

              //Check that all required index vectors exist
              for (int iv = start; iv<=end; iv++ )
              {
                if ((iv!=p) && (localindexvectors[positions[iv]]==null))
                {
                  canProcess = false;
                  break;
                }
              }

              if (!canProcess) continue;

              vec1 = ngBuilder.generateNGramVector( docterms, positions, localindexvectors, start, end, p );

              termId = positions[p];

              utils.addAssignVectors( localtermvectors[termId], vec1, 1.0f );
            }
          }

        } // positions

        // Need to add localtermvectors to termvectors

        // Go through distinct words in this document and add their vectors
        // to termVectors.
        float[] v;
        for (short tcn = 0; tcn < numwords; tcn++)
        {
          if ((v = termVectors.getVector(docterms[tcn])) != null)
          {
            for (int i=0; i<v.length; i++)
            {
              v[i] += localtermvectors[tcn].getQuick(i);
            }
          }
        }
      }
    }

    System.out.println("\nCreated " + termVectors.getNumVectors() + " term vectors ...");
    System.out.println("\nNormalizing term vectors");

    Enumeration e = termVectors.getAllVectors();
    normalise( e );

    System.out.println("\nCreated " + indexVectors.getNumVectors() + " index vectors ...");
    System.out.println("\nNormalizing index vectors");

    e = indexVectors.getAllVectors();
    normalise( e );

  }

  public void normalise( Enumeration e )
  {
    int zeroVecs = 0, totVecs = 0;
    while (e.hasMoreElements())
    {
      ObjectVector temp = (ObjectVector) e.nextElement();

      if (temp.getVector() == null) continue;

      float[] next = temp.getVector();

      // Normalise and count any zero vectors
      // (There shouldn't be any zero vectors)
      if (!utils.normalize(next)) zeroVecs++;
      totVecs++;
    }

    System.out.println("Total number of vectors: " + totVecs);
    System.out.println("Number of zero vectors: " + zeroVecs);
  }

  public VectorStore getIndexVectors()
  {
    return indexVectors;
  }

  // Basic VectorStore interface stuff now implemented through termVectors.
  public float[] getVector(Object term) {
    return termVectors.getVector(term);
  }

  public Enumeration<ObjectVector> getAllVectors() {
    return termVectors.getAllVectors();
  }

  public int getNumVectors() {
    return termVectors.getNumVectors();
  }

  public int getDimension() {
    return Flags.dimension;
  }
}
