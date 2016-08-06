/**
 * Copyright (c) 2008, University of Pittsburgh
 * <p>
 * All rights reserved.
 * <p>
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * <p>
 * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * <p>
 * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided
 * with the distribution.
 * <p>
 * Neither the name of the University of Pittsburgh nor the names
 * of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written
 * permission.
 * <p>
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

package pitt.search.semanticvectors;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.lucene.index.DocsAndPositionsEnum;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;
import org.netlib.blas.BLAS;

import pitt.search.semanticvectors.orthography.NumberRepresentation;
import pitt.search.semanticvectors.utils.VerbatimLogger;
import pitt.search.semanticvectors.vectors.PermutationUtils;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorType;
import pitt.search.semanticvectors.vectors.VectorUtils;

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
public class TermTermVectorsFromLucene { //implements VectorStore {

  /** Different methods for creating positional indexes. */
  public enum PositionalMethod {
    /** Basic "bag-of-words" method using context windows. */
    BASIC,
    /** Binds vectors on left or right based on position. */
    DIRECTIONAL,
    /** Permutes vectors according to how many places they are from focus term. */
    PERMUTATION,
    /** Superposition of basic and permuted vectors. */
    PERMUTATIONPLUSBASIC,
    /** Encodes position within sliding window using NumberRepresentation */
    PROXIMITY,
    /** Implementation of skipgram with negative sampling (Mikolov 2013) */
    EMBEDDINGS
  }

  private FlagConfig flagConfig;
  private boolean retraining = false;
  private volatile VectorStoreRAM semanticTermVectors;
  private volatile VectorStore elementalTermVectors;
  private LuceneUtils luceneUtils;
  /** Used only with {@link PositionalMethod#PROXIMITY}. */
  private VectorStoreRAM positionalNumberVectors;
  private Random random;
  private ConcurrentSkipListMap<Double, String> termDic;
  private ConcurrentHashMap<String, Double> subsamplingProbabilities;
  private ConcurrentLinkedQueue<Terms> theQ;
  private double totalPool 	= 0; //total pool of terms probabilities for negative sampling corpus
  private long 	 totalCount = 0; //total count of terms in corpus
  private double alpha = 0.025;
  private double minimum_alpha = 0.0001;
  private AtomicInteger totalDocCount = new AtomicInteger();

  /**
   * Used to store permutations we'll use in training.  If positional method is one of the
   * permutations, this contains the shift for all the focus positions.
   */
  private int[][] permutationCache;

  /** Returns the semantic (learned) vectors. */
  public VectorStore getSemanticTermVectors() {
    return this.semanticTermVectors;
  }

  /**
   * Constructs an instance using the given configs and elemental vectors.
   * @throws IOException
   */
  public TermTermVectorsFromLucene(
      FlagConfig flagConfig, VectorStore elementalTermVectors) throws IOException {
    this.flagConfig = flagConfig;

    this.random = new Random();

    // Setup elemental vectors, depending on whether they were passed in or not.
    if (elementalTermVectors != null) {
      retraining = true;
      this.elementalTermVectors = elementalTermVectors;
      VerbatimLogger.info("Reusing basic term vectors; number of terms: "
          + elementalTermVectors.getNumVectors() + "\n");
    } else {
      this.elementalTermVectors = new ElementalVectorStore(flagConfig);
    }

    if (flagConfig.positionalmethod().equals(PositionalMethod.EMBEDDINGS)) {
      //force dense vectors
      if (!flagConfig.vectortype().equals(VectorType.BINARY))
      { flagConfig.seedlength = flagConfig.dimension();
      	VerbatimLogger.info("Setting seedlength=dimensionsionality, to initialize embedding weights");}
      else {
        VerbatimLogger.info("Warning: binary vector embeddings are in the experimental phase");
      }
    }

    if (flagConfig.positionalmethod() == PositionalMethod.PERMUTATION
        || flagConfig.positionalmethod() == PositionalMethod.PERMUTATIONPLUSBASIC)
      initializePermutations();
    else if (flagConfig.positionalmethod() == PositionalMethod.DIRECTIONAL)
      initializeDirectionalPermutations();
    else if (flagConfig.positionalmethod() == PositionalMethod.PROXIMITY)
      initializeNumberRepresentations();
    trainTermTermVectors();
  }

  /**
   * Initialize all permutations that might be used.
   */
  private void initializePermutations() {
    permutationCache =
        new int[2 * flagConfig.windowradius() + 1][PermutationUtils.getPermutationLength(flagConfig.vectortype(), flagConfig.dimension())];
    for (int i = 0; i < 2 * flagConfig.windowradius() + 1; ++i) {
      permutationCache[i] = PermutationUtils.getShiftPermutation(
          flagConfig.vectortype(), flagConfig.dimension(), i - flagConfig.windowradius());
    }
  }

  /**
   * Initialize queue of cached Terms objects
   */
  private int startdoc = 0;
  private int qsize = 100000;
  private AtomicBoolean exhaustedQ = new AtomicBoolean();

  private synchronized void initializeQueue() {
	LinkedList<Terms> tempQ = new LinkedList<Terms>();
    int added = 0;
    int stopdoc = Math.min(startdoc + qsize, luceneUtils.getNumDocs());
    for (int a = startdoc; a < stopdoc; a++) {
      for (String field : flagConfig.contentsfields())
        try {
          tempQ.add(luceneUtils.getTermVector(a, field));
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }

      startdoc++;
      added++;
    }

    //randomize
    Collections.shuffle(tempQ);
    theQ.addAll(tempQ);

    if (added > 0)
      System.err.println("Initialized TermVector Queue with " + added + " documents");
    else exhaustedQ.set(true);
  }

  /**
   * Draws from term vector queue, with replacement
   */
  private synchronized Terms drawFromQueue() {
    if (theQ.isEmpty()) initializeQueue();
    Terms toReturn = theQ.poll();
    return toReturn;
  }

  private boolean queueExhausted() {
    return exhaustedQ.get();
  }

  /**
   * Initialize all number vectors that might be used (i.e. one for each position in the sliding window)
   * Used only with {@link PositionalMethod#PROXIMITY}.
   */
  private void initializeNumberRepresentations() {
    NumberRepresentation numberRepresentation = new NumberRepresentation(flagConfig);
    positionalNumberVectors = numberRepresentation.getNumberVectors(1, 2 * flagConfig.windowradius() + 2);

    try {
      VectorStoreWriter.writeVectorsInLuceneFormat("numbervectors.bin", flagConfig, positionalNumberVectors);

    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  /**
   * Initialize all permutations that might be used (i.e +1 and -1).
   */
  private void initializeDirectionalPermutations() {
    permutationCache =
        new int[2][PermutationUtils.getPermutationLength(flagConfig.vectortype(), flagConfig.dimension())];

    permutationCache[0] = PermutationUtils.getShiftPermutation(
        flagConfig.vectortype(), flagConfig.dimension(), -1);

    permutationCache[1] = PermutationUtils.getShiftPermutation(
        flagConfig.vectortype(), flagConfig.dimension(), 1);
  }


  private class TrainTermVectorThread implements Runnable {
    int dcnt = 0;
    int threadno = 0;
    double time = 0;
    BLAS blas = null;

    public TrainTermVectorThread(int threadno) {
      this.threadno = threadno;
      this.blas = BLAS.getInstance();
      this.time = System.currentTimeMillis();
    }

    @Override
    public void run() {

      while (!queueExhausted()) {
        for (String field : flagConfig.contentsfields()) {
          try {
            Terms terms = drawFromQueue();
            if (terms == null) {
              //VerbatimLogger.severe("No term vector for document "+dc);
              continue;
            }
            processTermPositionVector(terms, field, blas);
          } catch (ArrayIndexOutOfBoundsException | IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
        }

        // Output progress counter.
        if ((dcnt % 10000 == 0) || (dcnt < 10000 && dcnt % 1000 == 0)) {
          VerbatimLogger.info("[T" + threadno + "]" + " processed " + dcnt + " documents in " + ("" + ((System.currentTimeMillis() - time) / (1000 * 60))).replaceAll("\\..*", "") + " min..");

          if (threadno == 0 && dcnt % 10000 == 0) {
            double proportionComplete = totalDocCount.get() / (double) ( (1+flagConfig.trainingcycles()) * (luceneUtils.getNumDocs()));
            alpha -= (alpha - minimum_alpha) * proportionComplete;
            if (alpha < minimum_alpha) alpha = minimum_alpha;
            VerbatimLogger.info("..Updated alpha to " + alpha + "..");
          }
        }
        dcnt++;
      } //all documents processed
    }
  }

  private void trainTermTermVectors() throws IOException, RuntimeException {
    luceneUtils = new LuceneUtils(flagConfig);
    termDic = new ConcurrentSkipListMap<Double, String>();
    totalPool = 0;

    // Check that the Lucene index contains Term Positions.
    FieldInfos fieldsWithPositions = luceneUtils.getFieldInfos();
    if (!fieldsWithPositions.hasVectors()) {
      throw new IOException(
          "Term-term indexing requires a Lucene index containing TermPositionVectors."
              + "\nTry rebuilding Lucene index using pitt.search.lucene.IndexFilePositions");
    }

    this.semanticTermVectors = new VectorStoreRAM(flagConfig);

    // Iterate through an enumeration of terms and allocate initial term vectors.
    // If not retraining, create random elemental vectors as well.
    int tc = 0;
    for (String fieldName : flagConfig.contentsfields()) {
      TermsEnum terms = this.luceneUtils.getTermsForField(fieldName).iterator(null);
      BytesRef bytes;
      while ((bytes = terms.next()) != null) {
        Term term = new Term(fieldName, bytes);
        // Skip terms that don't pass the filter.
        if (!luceneUtils.termFilter(term)) continue;
        tc++;

        Vector termVector = null;
        // construct negative sampling table
        if (flagConfig.positionalmethod().equals(PositionalMethod.EMBEDDINGS)) {
          totalPool += Math.pow(luceneUtils.getGlobalTermFreq(term), .75);
          termDic.put(totalPool, term.text());
          //force dense term vectors
          termVector = VectorFactory.generateRandomVector(flagConfig.vectortype(), flagConfig.dimension(), flagConfig.seedlength(), random);
        
        } else termVector = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());
        // Place each term vector in the vector store.
        this.semanticTermVectors.putVector(term.text(), termVector);
        totalCount += luceneUtils.getGlobalTermFreq(term);
        // Do the same for random index vectors unless retraining with trained term vectors
        if (!retraining) {
          this.elementalTermVectors.getVector(term.text());
        
        }
      }
    }

    //precalculate probabilities for subsampling (need to iterate again once total term frequency known)
    if (flagConfig.samplingthreshold() > -1 && flagConfig.samplingthreshold() < 1) {
      subsamplingProbabilities = new ConcurrentHashMap<String, Double>();

      VerbatimLogger.info("Populating subsampling probabilities - total term count = " + totalCount + " which is " + (totalCount / luceneUtils.getNumDocs()) + " per doc on average");
      int count = 0;
      for (String fieldName : flagConfig.contentsfields()) {
        TermsEnum terms = this.luceneUtils.getTermsForField(fieldName).iterator(null);
        BytesRef bytes;
        while ((bytes = terms.next()) != null) {
          Term term = new Term(fieldName, bytes);
          if (++count % 10000 == 0) VerbatimLogger.info(".");

          // Skip terms that don't pass the filter.
          if (!semanticTermVectors.containsVector(term.text())) continue;

          double globalFreq = (double) luceneUtils.getGlobalTermFreq(term) / (double) totalCount;

          if (globalFreq > flagConfig.samplingthreshold()) {
            double discount = 1; //(globalFreq - flagConfig.samplingthreshold()) / globalFreq;
            subsamplingProbabilities.put(fieldName + ":" + bytes.utf8ToString(), (discount - Math.sqrt(flagConfig.samplingthreshold() / globalFreq)));
            //VerbatimLogger.info(globalFreq+" "+term.text()+" "+subsamplingProbabilities.get(fieldName+":"+bytes.utf8ToString()));
          }
        }  //all terms for one field
      } // all fields
      VerbatimLogger.info("\n");
      if (subsamplingProbabilities !=null && subsamplingProbabilities.size() > 0)
        VerbatimLogger.info("Selected for subsampling: " + subsamplingProbabilities.size() + " terms.\n");
    } //end subsampling condition

    VerbatimLogger.info(
        "There are now elemental term vectors for " + tc + " terms (and " + luceneUtils.getNumDocs() + " docs).\n");

    for (int trainingcycle = 0; trainingcycle <= flagConfig.trainingcycles(); trainingcycle++) {
      startdoc = 0;
      exhaustedQ.set(false);
      theQ = new ConcurrentLinkedQueue<>();

      initializeQueue();
      double cycleStart = System.currentTimeMillis();

      int numthreads = flagConfig.numthreads();
      ExecutorService executor = Executors.newFixedThreadPool(numthreads);

      for (int q = 0; q < numthreads; q++) {
        executor.execute(new TrainTermVectorThread(q));
        VerbatimLogger.info("Started thread " + q + "\n");
      }

      executor.shutdown();
      // Wait until all threads are finish
      while (!executor.isTerminated()) {
      }

      VerbatimLogger.info("\nTime for training cycle " + (System.currentTimeMillis() - cycleStart) + "ms \n");
      VerbatimLogger.info("\nCreated " + semanticTermVectors.getNumVectors() + " term vectors ...\n");

    } //end of training cycles
    
    Enumeration<ObjectVector> e = semanticTermVectors.getAllVectors();

    while (e.hasMoreElements()) {
      e.nextElement().getVector().normalize();
    }

    // If building a permutation index, these need to be written out to be reused.
    //
    // TODO(dwiddows): It is odd to do this here while not writing out the semantic
    // term vectors here.  We should redesign this.
    if ((flagConfig.positionalmethod().equals(PositionalMethod.EMBEDDINGS)) || flagConfig.positionalmethod() == PositionalMethod.PERMUTATION
        || flagConfig.positionalmethod() == PositionalMethod.PERMUTATIONPLUSBASIC
        && !retraining) {
      VerbatimLogger.info("Normalizing and writing elemental vectors to " + flagConfig.elementalvectorfile() + "\n");
      Enumeration<ObjectVector> f = elementalTermVectors.getAllVectors();

      while (f.hasMoreElements()) {
        f.nextElement().getVector().normalize();
      }
    }

    VectorStoreWriter.writeVectors(flagConfig.elementalvectorfile(), flagConfig, this.elementalTermVectors);
  }

  private void processEmbeddings(
      Vector embeddingVector, ArrayList<Vector> contextVectors,
      ArrayList<Integer> contextLabels, double learningRate, BLAS blas) {
	  double feedForwardOutput = 0;
	  double error = 0;
	  int counter = 0;

    //for each contextVector   (there should be one "true" context vector, and a number of negative samples)
    for (Vector contextVec : contextVectors) {
    	
    	Vector duplicateContextVec 	 = contextVec.copy();
    	feedForwardOutput = VectorUtils.scalarProduct(embeddingVector, duplicateContextVec, flagConfig, blas);
       
      if (!flagConfig.vectortype().equals(VectorType.BINARY)) //sigmoid function
  	  {
        feedForwardOutput = Math.pow(Math.E, -1 * feedForwardOutput);
      	feedForwardOutput = 1 / (1 + feedForwardOutput);
        //if label == 1, a context word - so the error is the (1-predicted probability of for this word) - ideally 0
        //if label == 0, a negative sample - so the error is the (predicted probability for this word) - ideally 0
      	error = feedForwardOutput - contextLabels.get(counter++);  
  	  } else //RELU-like function for binary vectors
      {
     	   feedForwardOutput = Math.max(feedForwardOutput, 0);
     	   error = feedForwardOutput - contextLabels.get(counter++);
     	   //avoid passing floating points (the default behavior currently is to ignore these if the first superposition weight is an Integer)
      	  	error = Math.round(error*100);
     	  }
      
      //update the context vector and embedding vector, respectively
      VectorUtils.superposeInPlace(embeddingVector, contextVec, flagConfig, blas, -learningRate * error);
      VectorUtils.superposeInPlace(duplicateContextVec, embeddingVector, flagConfig, blas, -learningRate * error);
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
   * @throws IOException
   */
  private void processTermPositionVector(Terms terms, String field, BLAS blas)
      throws ArrayIndexOutOfBoundsException, IOException {
    if (terms == null) return;

    //Reconstruct document from term positions
    Hashtable<Integer, String> localTermPositions = new Hashtable<Integer, String>();

    //To accommodate "dynamic" sliding window that includes indexed/sampled terms only
    ArrayList<Integer> thePositions = new ArrayList<Integer>();

    TermsEnum termsEnum = terms.iterator(null);
    BytesRef text;
   
    while ((text = termsEnum.next()) != null) {
      String theTerm = text.utf8ToString();
      if (!semanticTermVectors.containsVector(theTerm)) continue;

      DocsAndPositionsEnum docsAndPositions = termsEnum.docsAndPositions(null, null);
      if (docsAndPositions == null) return;
      docsAndPositions.nextDoc();

      int freq = docsAndPositions.freq();
     
      //iterate through all positions of this term
      for (int x = 0; x < freq; x++) {

        int thePosition = docsAndPositions.nextPosition();

        //subsampling of frequent terms
        if (subsamplingProbabilities == null || (!subsamplingProbabilities.containsKey(field + ":" + theTerm) || random.nextDouble() > subsamplingProbabilities.get(field + ":" + theTerm))) {
          localTermPositions.put(thePosition, theTerm);
          thePositions.add(thePosition);
            }
      }
    }

    // Sort positions with indexed/sampled terms
    // Effectively this compresses the sequence of terms in this document, such that
    // terms that were subsampled, stoplisted, or didn't meet frequencey thresholds
    // do not result in "blank" positions - rather, they are squeezed out of the sequence
    Collections.sort(thePositions);

    //vestigial code for the purpose of error checking for sequence reconstruction from Lucene
    //int cnt=0;
    //for (int index:thePositions)
    //System.out.println(++cnt+" "+index+" "+localTermPositions.get(index));

    //move the sliding window through the sequence (the focus position is the position of the "observed" term)
    for (int focusposn : thePositions) {

      String focusterm = localTermPositions.get(focusposn);

      //word2vec uniformly samples the window size - we will try this too
      int effectiveWindowRadius = flagConfig.windowradius();
      if (flagConfig.subsampleinwindow) effectiveWindowRadius = random.nextInt(flagConfig.windowradius()) + 1;

      int windowstart = Math.max(0, focusposn - effectiveWindowRadius);
      int windowend = Math.min(focusposn + effectiveWindowRadius, localTermPositions.size() - 1);

      for (int cursor = windowstart; cursor <= windowend; cursor++) {

        if (cursor == focusposn) continue;

        String coterm = localTermPositions.get(thePositions.get(cursor));
        
        Vector toSuperpose = elementalTermVectors.getVector(coterm);

        /**
         * Implementation of skipgram with negative sampling (Mikolov 2013)
         */

        if ((flagConfig.positionalmethod().equals(PositionalMethod.EMBEDDINGS))) {
          ArrayList<Vector> contextVectors = new ArrayList<Vector>();
          ArrayList<Integer> contextLabels = new ArrayList<Integer>();

          //add the context term, with label '1'
          contextVectors.add(toSuperpose);
          contextLabels.add(1);

          //add flagConfig.negsamples() randomly drawn terms with label '0'
          //these terms (and hence their context vectors)
          //are drawn with a probability of (global occurrence)^0.75, as recommended
          //by Mikolov and other authors
          while (contextVectors.size() <= flagConfig.negsamples) {
            Vector randomTerm = null;
            double max = totalPool; //total (non unique) term count

            while (randomTerm == null) {
              double test = random.nextDouble()*max;
              if (termDic.ceilingEntry(test) != null) {
            	  String testTerm = termDic.ceilingEntry(test).getValue();
              		if (! testTerm.equals(coterm))
              		randomTerm = elementalTermVectors.getVector(testTerm);
              }
              	}

            contextVectors.add(randomTerm);
            contextLabels.add(0);

          }

          this.processEmbeddings(semanticTermVectors.getVector(focusterm), contextVectors, contextLabels, alpha, blas);
        } else {
          //random indexing variants
          float globalweight = luceneUtils.getGlobalTermWeight(new Term(field, coterm));

          // bind to appropriate position vector
          if (flagConfig.positionalmethod() == PositionalMethod.PROXIMITY) {
            toSuperpose = elementalTermVectors.getVector(coterm).copy();
            toSuperpose.bind(positionalNumberVectors.getVector(cursor - focusposn));
          }

          // calculate permutation required for either Sahlgren (2008) implementation
          // encoding word order, or encoding direction as in Burgess and Lund's HAL
          if (flagConfig.positionalmethod() == PositionalMethod.BASIC
              || flagConfig.positionalmethod() == PositionalMethod.PERMUTATIONPLUSBASIC
              || flagConfig.positionalmethod() == PositionalMethod.PROXIMITY) {
            semanticTermVectors.getVector(focusterm).superpose(toSuperpose, globalweight, null);
          }
          if (flagConfig.positionalmethod() == PositionalMethod.PERMUTATION
              || flagConfig.positionalmethod() == PositionalMethod.PERMUTATIONPLUSBASIC) {
            int[] permutation = permutationCache[cursor - focusposn + flagConfig.windowradius()];
            semanticTermVectors.getVector(focusterm).superpose(toSuperpose, globalweight, permutation);
          } else if (flagConfig.positionalmethod() == PositionalMethod.DIRECTIONAL) {
            int[] permutation = permutationCache[(int) Math.max(0, Math.signum(cursor - focusposn))];
            semanticTermVectors.getVector(focusterm).superpose(toSuperpose, globalweight, permutation);
          }
        }
      } //end of current sliding window
    } //end of all sliding windows

    totalDocCount.incrementAndGet();
  }
}
