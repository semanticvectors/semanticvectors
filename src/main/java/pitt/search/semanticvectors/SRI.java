package pitt.search.semanticvectors;

/**
 * Copyright (c) 2008, University of Pittsburgh
 * <p/>
 * All rights reserved.
 * <p/>
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * <p/>
 * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * <p/>
 * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided
 * with the distribution.
 * <p/>
 * Neither the name of the University of Pittsburgh nor the names
 * of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written
 * permission.
 * <p/>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 **/


import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Enumeration;
import java.util.Random;
import java.util.logging.Logger;
import java.io.IOException;
import java.lang.RuntimeException;

import org.apache.lucene.index.DocsAndPositionsEnum;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

import pitt.search.semanticvectors.DocVectors.DocIndexingStrategy;
import pitt.search.semanticvectors.LuceneUtils.TermWeight;
import pitt.search.semanticvectors.utils.VerbatimLogger;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;

/**
 * Symmetric Random Indexing (SRI), an alternative to RI that (like RRI)
 * addresses the issue of indirect inference.
 *
 * First described in: 
 * Cohen, T., & Schvaneveldt, R. W. (2010). The trajectory of scientific discovery: 
 * concept co-occurrence and converging semantic distance. Stud Health Technol Inform, 160, 661-665.
 *
 * SRI may offers some advantages over RRI, in that it provides a direct route to associations between terms 
 * that don’t co-occur directly, and doesn’t encode a term’s elemental vector into its semantic vector, 
 * which is what happens with RRI and must contribute toward the rapid degradation in performance over multiple iterations. 
 *
 * These advantages are offset to some degree by the fact that SRI requires many more superposition operations - rather than 
 * adding a pre-generated document vector to the semantic vector of each term that occurs in it, 
 * the elemental vector for every other term in the document is added to the semantic vector for each term. 
 *
 * On the other hand, as these are sparse vectors, superposition involves processing the non-zero elements 
 * of a random (elemental) vector only.  So with 2,000 dimensional vectors we would break even on 100-word abstracts 
 * with a seed length of 10*2. 
 *
 * Implementation of vector store that creates term by term
 * cooccurence vectors by iterating through all the documents in a
 * Lucene index.  This class differs from random indexing as originally
 * implemented by reducing the dimensions of the term-document matrix
 * multiplied by its transpose, rather than those of the original
 * term-document matrix.
 *
 * @author Trevor Cohen, Dominic Widdows. 
 */
public class SRI implements VectorStore {

  private VectorStoreRAM termVectors;
  private VectorStore indexVectors;
  private FlagConfig flagConfig;
  private LuceneUtils lUtils;
  private static final Logger logger = Logger.getLogger(
      SRI.class.getCanonicalName());


  /**
   * Creates SRI instance, and trains term vectors as well as 
   * document vectors if indicated.
   */
  public SRI(FlagConfig flagConfig)
      throws IOException, RuntimeException {


    this.flagConfig = flagConfig;
    termVectors = new VectorStoreRAM(flagConfig);
    this.lUtils = new LuceneUtils(flagConfig);

    //initialize zero vectors and index vectors
    initializeVectorStores();

    int numdocs = lUtils.getNumDocs();

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
      for (String field : flagConfig.contentsfields()) {
        Terms terms = lUtils.getTermVector(dc, field);
        if (terms == null) {
          VerbatimLogger.severe("No term vector for document " + dc);
          continue;
        }

        ArrayList<String> localTerms = new ArrayList<String>();
        ArrayList<Integer> freqs = new ArrayList<Integer>();
        Hashtable<Integer, Integer> localTermPositions = new Hashtable<Integer, Integer>();

        TermsEnum termsEnum = terms.iterator(null);
        BytesRef text;
        int termcount = 0;

        //get all the terms and frequencies required for processing
        while ((text = termsEnum.next()) != null) {
          String theTerm = text.utf8ToString();
          if (!termVectors.containsVector(theTerm)) continue;
          DocsAndPositionsEnum docsAndPositions = termsEnum.docsAndPositions(null, null);
          if (docsAndPositions == null) return;
          docsAndPositions.nextDoc();
          freqs.add(docsAndPositions.freq());
          localTerms.add(theTerm);

          for (int x = 0; x < docsAndPositions.freq(); x++) {
            localTermPositions.put(new Integer(docsAndPositions.nextPosition()), termcount);
          }

          termcount++;
        }


        int numwords = freqs.size();
        float norm = 0;

        /** transform the frequencies into weighted frequencies (if required) **/
        float[] freaks = new float[freqs.size()];
        for (int x = 0; x < freaks.length; x++) {
          int freq = freqs.get(x);
          String aTerm = localTerms.get(x);
          float globalweight = lUtils.getGlobalTermWeight(new Term(field, aTerm));
          float localweight = lUtils.getLocalTermWeight(freq);
          freaks[x] = localweight * globalweight;
          norm += Math.pow(freaks[x], 2);
        }

        /** normalize the transient document vector (it contains all non-zero values) **/
        norm = (float) Math.sqrt(norm);
        for (int x = 0; x < freaks.length; x++)
          freaks[x] = freaks[x] / norm;

        /** create local random index and term vectors for relevant terms**/
        Vector[] localindexvectors = new Vector[numwords];
        Vector[] localtermvectors = new Vector[numwords];


        for (short tcn = 0; tcn < numwords; ++tcn) {
          // Only terms that have passed the term filter are included in the VectorStores.
          if (this.termVectors.containsVector(localTerms.get(tcn))) {
            /** retrieve relevant random index vectors**/
            localindexvectors[tcn] = indexVectors.getVector(localTerms.get(tcn));

            /** retrieve the float[] arrays of relevant term vectors **/
            localtermvectors[tcn] = termVectors.getVector(localTerms.get(tcn));
          }
        }

        for (int x = 0; x < localTerms.size() - 1; x++)
          for (int y = x + 1; y < localTerms.size(); y++) {
            if ((localtermvectors[x] != null) && (localtermvectors[y] != null)) {
              float freq = freaks[x];
              float freq2 = freaks[y];
              float mult = freq * freq2; //calculate this component of the scalar product between term-by-doc vectors


              localtermvectors[x].superpose(localindexvectors[y], mult, null);
              localtermvectors[y].superpose(localindexvectors[x], mult, null);
            }
          }
      }
    }

    logger.info("\nCreated " + termVectors.getNumVectors() + " term vectors ...");
    logger.info("\nNormalizing term vectors");
    Enumeration<ObjectVector> e = termVectors.getAllVectors();

    while (e.hasMoreElements()) {
      ObjectVector temp = (ObjectVector) e.nextElement();
      temp.getVector().normalize();
    }

    VectorStoreWriter.writeVectorsInLuceneFormat("sritermvectors.bin", flagConfig, termVectors);

    //Generate document vectors using newly created termvectors
    // Incremental indexing is hardcoded into SRI
    // TODO: Understand if this is an appropriate requirement, and whether
    //       the user should be alerted of any potential consequences.
    if (flagConfig.docindexing() != DocIndexingStrategy.NONE) {
      IncrementalDocVectors.createIncrementalDocVectors(
          termVectors, flagConfig, new LuceneUtils(flagConfig));
    }
  }

  public Vector getVector(Object term) {
    return termVectors.getVector(term);
  }

  public Enumeration<ObjectVector> getAllVectors() {
    return termVectors.getAllVectors();
  }

  //creates zero vectors for terms to be indexed

  private void initializeVectorStores() throws IOException {
    termVectors = new VectorStoreRAM(flagConfig);

    if (flagConfig.initialtermvectors().isEmpty()) {
      indexVectors = new ElementalVectorStore(flagConfig);
    } else {
      indexVectors = new VectorStoreRAM(flagConfig);
      ((VectorStoreRAM) indexVectors).initFromFile(flagConfig.initialtermvectors());

    }
    for (String fieldName : this.flagConfig.contentsfields()) {
      Terms terms = this.lUtils.getTermsForField(fieldName);
      TermsEnum termEnum = terms.iterator(null);
      int tc = 0;

      BytesRef bytes;
      while ((bytes = termEnum.next()) != null) {
        Term term = new Term(fieldName, bytes);

        if (termVectors.getVector(term.text()) != null) continue;
        if (!lUtils.termFilter(term)) continue;
        tc++;
        Vector termVector = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());

        // Place each term vector in the vector store.
        termVectors.putVector(term.text(), termVector);

      }
      VerbatimLogger.info(String.format(
          "There are %d terms (and %d docs)", tc, this.lUtils.getNumDocs()));
    }
  }


  @Override
  public boolean containsVector(Object object) {
    // TODO Auto-generated method stub
    return termVectors.containsVector(object);
  }

  /**
   * @return a count of the number of vectors in the store.
   */
  public int getNumVectors() {
    return termVectors.getNumVectors();
  }

  public static void main(String[] args) throws IllegalArgumentException, IOException {
    FlagConfig flagConfig = FlagConfig.getFlagConfig(args);
    args = flagConfig.remainingArgs;

    if (flagConfig.luceneindexpath().isEmpty()) {
      throw (new IllegalArgumentException("-luceneindexpath argument must be provided."));
    }

    VerbatimLogger.info("Building SRI model from index in: " + flagConfig.luceneindexpath() + "\n");
    VerbatimLogger.info("Minimum frequency = " + flagConfig.minfrequency() + "\n");
    VerbatimLogger.info("Maximum frequency = " + flagConfig.maxfrequency() + "\n");
    VerbatimLogger.info("Number non-alphabet characters = " + flagConfig.maxnonalphabetchars() + "\n");

    new SRI(flagConfig);
  }
}

  


