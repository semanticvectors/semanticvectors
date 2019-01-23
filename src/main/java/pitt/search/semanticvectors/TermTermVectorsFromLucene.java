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

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.BytesRef;
import org.netlib.blas.BLAS;

import pitt.search.semanticvectors.DocVectors.DocIndexingStrategy;
import pitt.search.semanticvectors.orthography.NumberRepresentation;
import pitt.search.semanticvectors.utils.SigmoidTable;
import pitt.search.semanticvectors.utils.VerbatimLogger;
import pitt.search.semanticvectors.vectors.PermutationUtils;
import pitt.search.semanticvectors.vectors.PermutationVector;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorType;
import pitt.search.semanticvectors.vectors.VectorUtils;
import pitt.search.semanticvectors.vectors.ZeroVectorException;

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
    PROXIMITY
  }
  
  /** Different methods for creating positional indexes. */
  public enum EncodingMethod {
    /** Random indexing */
    RANDOM_INDEXING,
    /** Implementation of skipgram with negative sampling (Mikolov 2013) */
    EMBEDDINGS
  }

  private static final int MAX_EXP = 6;
  private FlagConfig flagConfig;
  private AtomicBoolean exhaustedQ = new AtomicBoolean();
  private int qsize = 100000;
  private boolean retraining = false;
  private volatile VectorStoreRAM semanticTermVectors;
  private volatile VectorStore elementalTermVectors;
  private volatile VectorStoreRAM embeddingDocVectors;
  private volatile CompressedVectorStoreRAM subwordEmbeddingVectors;
  
  private LuceneUtils luceneUtils;
  /** Used only with {@link PositionalMethod#PROXIMITY}. */
  private VectorStoreRAM positionalNumberVectors;
  private Random random;
  private ConcurrentSkipListMap<Double, String> termDic;
  private ConcurrentHashMap<String, Double> subsamplingProbabilities;
  private ConcurrentLinkedQueue<DocIdTerms> theQ;
  private double totalPool 	= 0; //total pool of terms probabilities for negative sampling corpus
  private long 	 totalCount = 0; //total count of terms in corpus
  private double initial_alpha = 0.05;
  private double alpha = initial_alpha;
  private double minimum_alpha = 0.0001*initial_alpha;
  private AtomicInteger totalDocCount = new AtomicInteger();
  private AtomicInteger totalQueueCount = new AtomicInteger();
  private SigmoidTable sigmoidTable		= new SigmoidTable(MAX_EXP,1000);
  private long tpd_average; //average number of terms per document

  /**
   * store Terms objects while retaining their docID
   * @author tcohen
   *
   */
  private class DocIdTerms
  {
	  int docID;
	  Terms terms;
	  
	  public DocIdTerms(int docID, Terms terms)
	  {
		  this.docID = docID;
		  this.terms = terms;
	  }
  }
  
  /**
   * Used to store permutations we'll use in training.  If positional method is one of the
   * permutations, this contains the shift for all the focus positions.
   */
 private VectorStoreRAM permutationCache;
 private ConcurrentLinkedQueue<Integer> randomStartpoints;

  /** Returns the semantic (learned) vectors. */
  public VectorStore getSemanticTermVectors() {
    return this.semanticTermVectors;
  }
  
  /** Points in total document collection to draw queue from (for randomization without excessive seek time) **/

  private void initializeRandomizationStartpoints(int incrementSize)
  {
  	this.randomStartpoints = new ConcurrentLinkedQueue<Integer>();
  	int increments 		   = luceneUtils.getNumDocs() / incrementSize;
  	boolean remainder 	   = luceneUtils.getNumDocs() % incrementSize > 0;
  	
  	if (remainder) increments++;
  	
  	ArrayList<Integer> toRandomize = new ArrayList<Integer>();
  	
  	for (int x = 0; x < increments; x++)
  		toRandomize.add(x * incrementSize);

  	Collections.shuffle(toRandomize);
  	
  	randomStartpoints.addAll(toRandomize);
  	
  }

  /**
   * Get component n-grams for a given term (for subword embeddings)
   */
  
  public ArrayList<String> getComponentNgrams(String incomingString)
  {
	  ArrayList<String> outgoingNgrams = new ArrayList<String>();
	  String toDecompose = "<"+incomingString+">";
	  
	  for (int ngram_length = flagConfig.minimum_ngram_length(); ngram_length <= flagConfig.maximum_ngram_length(); ngram_length++)
		  for (int j=0; j <= (toDecompose.length() - ngram_length); j++)
			  {
			  	String toAdd = toDecompose.substring(j,j+ngram_length);
			  	//don't include the term itself 
			  	if (!toAdd.equals(toDecompose))
			  		outgoingNgrams.add(toAdd);
			  }
	  
	  return outgoingNgrams;
  }
  
  
  
  /**
   * Constructs an instance using the given configs and elemental vectors.
   * @throws IOException
   */
  public TermTermVectorsFromLucene(
      FlagConfig flagConfig, VectorStore elementalTermVectors) throws IOException {
    this.flagConfig = flagConfig;

    this.random = new Random();
    this.initial_alpha = flagConfig.initial_alpha();
	this.alpha = initial_alpha;
    
    //store n-gram vectors (memory intensive)
    if (flagConfig.subword_embeddings())
    	{
    		VerbatimLogger.info("Using subword embeddings\n");
    		this.subwordEmbeddingVectors = new CompressedVectorStoreRAM(flagConfig);
    	}
    
    // Setup elemental vectors, depending on whether they were passed in or not.
    if (elementalTermVectors != null) {
      retraining = true;
      this.elementalTermVectors = elementalTermVectors;
      VerbatimLogger.info("Reusing basic term vectors; number of terms: "
          + elementalTermVectors.getNumVectors() + "\n");
      
      
      // TODO - arrange to pass the input and output weight vectors as parameters explicitly
      // currently the output weights are thought to exist in the "elementalvectors" file
      // from the command line "-initialtermvectors elementalvectors.bin", and the incoming weights
      // are assumed to be in the "embeddingvectors.bin" file in this location
      // (these are the default output files when if (flagConfig.encodingmethod().equals(EncodingMethod.EMBEDDINGS)) are used)
      if (flagConfig.encodingmethod().equals(EncodingMethod.EMBEDDINGS))
      {
    	  this.semanticTermVectors = new VectorStoreRAM(flagConfig);
    	  this.semanticTermVectors.initFromFile(flagConfig.initialtermvectors().replaceAll("elemental","embedding"));
      
    	  if (flagConfig.positionalmethod() != PositionalMethod.BASIC && flagConfig.vectortype().equals(VectorType.REAL))
    		  this.permutationCache.initFromFile(flagConfig.permutationcachefile());
    		  
      
      }
      
      
    } else {
      this.elementalTermVectors = new ElementalVectorStore(flagConfig);
    }

    if (flagConfig.encodingmethod().equals(EncodingMethod.EMBEDDINGS)) {
      //force dense vectors
      if (!flagConfig.vectortype().equals(VectorType.BINARY))
      { flagConfig.seedlength = flagConfig.dimension();
      	VerbatimLogger.info("Setting seedlength=dimensionality, to initialize embedding weights");}
      else {
        VerbatimLogger.info("Warning: binary vector embeddings are in the experimental phase");
      }
    }

    if (flagConfig.positionalmethod() == PositionalMethod.PERMUTATION
        || flagConfig.positionalmethod() == PositionalMethod.PERMUTATIONPLUSBASIC
        )
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
	  
	//ensure the vector store is configured for permutations
	VectorType typeA = flagConfig.vectortype();
	flagConfig.setVectortype(VectorType.PERMUTATION);
    permutationCache = new VectorStoreRAM(flagConfig);
    flagConfig.setVectortype(typeA);
    
    //for (int i = 0; i < 2 * flagConfig.windowradius() + 1; ++i) {
      //todo replace with PermutationFactory syntax
    for (int i = -1*flagConfig.windowradius(); i <= flagConfig.windowradius(); ++i) {
      {
    	  if (i==0) 
    	  {
    		  int[] noPerm = new int[flagConfig.dimension()];
    		  for (int q=0; q<flagConfig.dimension(); q++)
    			  noPerm[q] = q;
    		  permutationCache.putVector(0, new PermutationVector(noPerm));
    		  
    	  }
    	  else
    		  {
    		  	permutationCache.putVector(i, new PermutationVector(PermutationUtils.getRandomPermutation( flagConfig.vectortype(), flagConfig.dimension())));
    		    permutationCache.putVector("_"+i, new PermutationVector(PermutationUtils.getInversePermutation( ((PermutationVector) permutationCache.getVector(i)).getCoordinates())));
    		  }
      }
    		  
    		  //PermutationUtils.getShiftPermutation(
         // flagConfig.vectortype(), flagConfig.dimension(), i - flagConfig.windowradius());
    }
  }

  /**
   * Initialize queue of cached Terms objects
   */


  private synchronized void populateQueue() {
	  
	  
	  
	  if (this.totalQueueCount.get() >= luceneUtils.getNumDocs() || randomStartpoints.isEmpty())  
	  { if (theQ.size() == 0) exhaustedQ.set(true); return; }
	
	int added = 0;
    int startdoc = randomStartpoints.poll();
    int stopdoc  = Math.min(startdoc+ qsize, luceneUtils.getNumDocs());
    
    for (int a = startdoc; a < stopdoc; a++) {
      for (String field : flagConfig.contentsfields())
        try {
          int docID = a;
          Terms incomingTermVector = luceneUtils.getTermVector(a, field);
          totalQueueCount.incrementAndGet();
          if (incomingTermVector != null){ 
          theQ.add(new DocIdTerms(docID,incomingTermVector));
          added++;
          }
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
    }
    
    if (added > 0)
      System.err.println("Initialized TermVector Queue with " + added + " documents");
   
  }

  /**
   * Draws from term vector queue, with replacement
   */
  private synchronized DocIdTerms drawFromQueue() {
    if (theQ.isEmpty()) populateQueue();
    DocIdTerms toReturn = theQ.poll();
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
    if (flagConfig.vectortype().equals(VectorType.REAL))
    initializeProximityPermutations();
    else
    {
	NumberRepresentation numberRepresentation = new NumberRepresentation(flagConfig);
    positionalNumberVectors = numberRepresentation.getNumberVectors(1, 2 * flagConfig.windowradius() + 2);

    try {
      VectorStoreWriter.writeVectorsInLuceneFormat("numbervectors.bin", flagConfig, positionalNumberVectors);

    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    }
  }
  
  private void initializeProximityPermutations() {
  
		//ensure the vector store is configured for permutations
		VectorType typeA = flagConfig.vectortype();
		flagConfig.setVectortype(VectorType.PERMUTATION);
	    permutationCache = new VectorStoreRAM(flagConfig);
	    flagConfig.setVectortype(typeA);
	    
	    
	    int[] noPerm = new int[flagConfig.dimension()];
		  for (int q=0; q<flagConfig.dimension(); q++)
			  noPerm[q] = q;
		  permutationCache.putVector(0, new PermutationVector(noPerm));
	
	    
	    permutationCache.putVector(1, new PermutationVector(PermutationUtils.getRandomPermutation( flagConfig.vectortype(), flagConfig.dimension())));
	    permutationCache.putVector("_"+1, new PermutationVector(PermutationUtils.getInversePermutation( ((PermutationVector) permutationCache.getVector(1)).getCoordinates())));
		 
	    permutationCache.putVector(-1, new PermutationVector(PermutationUtils.getRandomPermutation( flagConfig.vectortype(), flagConfig.dimension())));
	    permutationCache.putVector("_"+-1, new PermutationVector(PermutationUtils.getInversePermutation( ((PermutationVector) permutationCache.getVector(-1)).getCoordinates())));
	    
	    //for (int i = 0; i < 2 * flagConfig.windowradius() + 1; ++i) {
	      //todo replace with PermutationFactory syntax
	    for (int i = -2; i >= -1*flagConfig.windowradius();  --i) {
	      
	    	
	    			int[] toAdd = PermutationUtils.getSwapPermutation(flagConfig.vectortype(), ((PermutationVector) permutationCache.getVector(i+1)).getCoordinates(), .25);
	    			
	    		  	permutationCache.putVector(i, new PermutationVector(toAdd));
	    		    permutationCache.putVector("_"+i, new PermutationVector(PermutationUtils.getInversePermutation(toAdd)));
	    		  
	      			}
		    for (int i = 2; i <= flagConfig.windowradius(); ++i) {
			      
			    	
		    	int[] toAdd = PermutationUtils.getSwapPermutation(flagConfig.vectortype(), ((PermutationVector) permutationCache.getVector(i-1)).getCoordinates(), .25);
    			
    		  	permutationCache.putVector(i, new PermutationVector(toAdd));
    		    permutationCache.putVector("_"+i, new PermutationVector(PermutationUtils.getInversePermutation(toAdd)));
    		  		  
			      	}
	    		  
	    		
	  
  }
  

  /**
   * Initialize all permutations that might be used (i.e +1 and -1).
   */
  private void initializeDirectionalPermutations() {
    
		//ensure the vector store is configured for permutations
		VectorType typeA = flagConfig.vectortype();
		flagConfig.setVectortype(VectorType.PERMUTATION);
	    permutationCache = new VectorStoreRAM(flagConfig);
	    flagConfig.setVectortype(typeA);
	  //  new int[2][PermutationUtils.getPermutationLength(flagConfig.vectortype(), flagConfig.dimension())];

    permutationCache.putVector(-1,  new PermutationVector(PermutationUtils.getRandomPermutation( flagConfig.vectortype(), flagConfig.dimension())));
    permutationCache.putVector("_"+-1, new PermutationVector(PermutationUtils.getInversePermutation( ((PermutationVector) permutationCache.getVector(-1)).getCoordinates())));
	
    //todo replace with PermutationFactory
    
    //temporary fix to deal with indexing of focus position when generating document embeddings
    int[] noPerm = new int[flagConfig.dimension()];
		  for (int q=0; q<flagConfig.dimension(); q++)
			  noPerm[q] = q;
		  permutationCache.putVector(0, new PermutationVector(noPerm));
		
    		//PermutationUtils.getShiftPermutation( flagConfig.vectortype(), flagConfig.dimension(), -1);

    permutationCache.putVector(1, new PermutationVector(PermutationUtils.getRandomPermutation( flagConfig.vectortype(), flagConfig.dimension())));
    permutationCache.putVector("_"+1, new PermutationVector(PermutationUtils.getInversePermutation( ((PermutationVector) permutationCache.getVector(1)).getCoordinates())));
	
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
            DocIdTerms terms = drawFromQueue();
            if (terms != null) {
              //VerbatimLogger.severe("No term vector for document "+dc);
            	  processTermPositionVector(terms, field, blas);
               }
             } catch (ArrayIndexOutOfBoundsException | IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
        }

  
        dcnt++;
        
        // Output progress counter.
        if ((dcnt % 10000 == 0) || (dcnt < 10000 && dcnt % 1000 == 0)) {
          VerbatimLogger.info("[T" + threadno + "]" + " processed " + dcnt + " documents in " + ("" + ((System.currentTimeMillis() - time) / (1000 * 60))).replaceAll("\\..*", "") + " min..");


        }
        
        if (threadno == 0 && dcnt % tpd_average == 0 && flagConfig.encodingmethod().equals(EncodingMethod.EMBEDDINGS)) {
            double proportionComplete = totalDocCount.get() / (double) ( (1+flagConfig.trainingcycles()) * (luceneUtils.getNumDocs()));
            //alpha = initial_alpha - (initial_alpha - minimum_alpha) * proportionComplete;
            alpha = initial_alpha * (1 - proportionComplete);
           if (alpha < minimum_alpha) alpha = minimum_alpha;
            if ((dcnt % 10000 == 0) || (dcnt < 10000 && dcnt % 1000 == 0)) VerbatimLogger.info("..Updated alpha to " + alpha + "..");
          }
        
      } //all documents processed
    }
  }

  private void trainTermTermVectors() throws IOException, RuntimeException {
    luceneUtils = new LuceneUtils(flagConfig);
    termDic = new ConcurrentSkipListMap<Double, String>();
    
    if (flagConfig.encodingmethod().equals(EncodingMethod.EMBEDDINGS) && flagConfig.docindexing().equals(DocIndexingStrategy.INMEMORY))
    	embeddingDocVectors = new VectorStoreRAM(flagConfig);
    	
    totalPool = 0;

    // Check that the Lucene index contains Term Positions.
    FieldInfos fieldsWithPositions = luceneUtils.getFieldInfos();
    if (!fieldsWithPositions.hasVectors()) {
      throw new IOException(
          "Term-term indexing requires a Lucene index containing TermPositionVectors."
              + "\nTry rebuilding Lucene index using pitt.search.lucene.IndexFilePositions");
    }

    if (this.semanticTermVectors == null) this.semanticTermVectors = new VectorStoreRAM(flagConfig);

    // Iterate through an enumeration of terms and allocate initial term vectors.
    // If not retraining, create random elemental vectors as well.
    // If retraining embeddings, create random vectors for terms that were not originally represented (to facilitate crossing corpora)
    int tc = 0;
    for (String fieldName : flagConfig.contentsfields()) {
      TermsEnum terms = this.luceneUtils.getTermsForField(fieldName).iterator();
      BytesRef bytes;
      while ((bytes = terms.next()) != null) {
        Term term = new Term(fieldName, bytes);
        
        // Skip terms that don't pass the filter.
        if (!luceneUtils.termFilter(term)) continue;
        tc++;
        totalCount += luceneUtils.getGlobalTermFreq(term);
        
        // construct negative sampling table without considering subsampling probabilities if no subsampling involved
        if (flagConfig.samplingthreshold() <= 0 || flagConfig.samplingthreshold() >= 1)
        {
        totalPool += Math.pow(luceneUtils.getGlobalTermFreq(term), .5); //originally .75, but FastText uses .5
        termDic.put(totalPool, term.text());  
        }
        
        Vector termVector = null;
        if (flagConfig.encodingmethod().equals(EncodingMethod.EMBEDDINGS)) {
            //force dense term vectors
          termVector = VectorFactory.generateRandomVector(flagConfig.vectortype(), flagConfig.dimension(), flagConfig.seedlength(), random);
        
        } else termVector = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());
        // Place each term vector in the vector store.
        if (!this.semanticTermVectors.containsVector(term.text())) 
        	this.semanticTermVectors.putVector(term.text(), termVector);
        // Do the same for random index vectors unless retraining with trained term vectors
        if (!retraining) {
          this.elementalTermVectors.getVector(term.text());
        
        } else if (retraining && flagConfig.encodingmethod().equals(EncodingMethod.EMBEDDINGS) && !elementalTermVectors.containsVector(term.text()))	{
        	//Retraining with embeddings - add random vectors for terms that meet inclusion criteria, but don't have output weights
        	//from previous corpus
        	((VectorStoreRAM) this.elementalTermVectors).putVector(term.text(),VectorFactory.generateRandomVector(flagConfig.vectortype(), flagConfig.dimension(), flagConfig.seedlength, random));
        }
      }
    }
    
    VerbatimLogger.info("\nNumber term vectors "+semanticTermVectors.getNumVectors()+"\t"+elementalTermVectors.getNumVectors());
    
    tpd_average =  totalCount / luceneUtils.getNumDocs();
    
    
    //precalculate probabilities for subsampling and negative sampling (need to iterate again once total term frequency known)
    if (flagConfig.samplingthreshold() > 0 && flagConfig.samplingthreshold() < 1) {
      subsamplingProbabilities = new ConcurrentHashMap<String, Double>();
      
      VerbatimLogger.info("Populating subsampling probabilities - total term count = " + totalCount + " which is " +tpd_average+ " per doc on average");
      int count = 0;
      for (String fieldName : flagConfig.contentsfields()) {
        TermsEnum terms = this.luceneUtils.getTermsForField(fieldName).iterator();
        BytesRef bytes;
        while ((bytes = terms.next()) != null) {
          Term term = new Term(fieldName, bytes);
          if (++count % 10000 == 0) VerbatimLogger.info(".");

          // Skip terms that don't pass the filter.
          if (!semanticTermVectors.containsVector(term.text())) continue;

          double globalFreq = (double) luceneUtils.getGlobalTermFreq(term) / (double) totalCount;
          double subdiscount = 1;
          
          if (globalFreq > flagConfig.samplingthreshold()) {
           
        	  double subsample_probability = 1 - (Math.sqrt(flagConfig.samplingthreshold() / globalFreq));
             
        	  if (flagConfig.aggressivesubsampling())
        	  //subsample_probability =1- (Math.sqrt(globalFreq / (double) (flagConfig.samplingthreshold() * totalCount)) + 1) 
            	//	* (flagConfig.samplingthreshold()  / globalFreq);
        		  subsample_probability = 1 - (Math.sqrt(flagConfig.samplingthreshold()/ globalFreq) + (flagConfig.samplingthreshold()/ globalFreq));
        		  
              subsamplingProbabilities.put(fieldName + ":" + bytes.utf8ToString(), subsample_probability);
             if (flagConfig.discountnegativesampling())
            	 subdiscount = 1-subsample_probability; //negative sample in accordance with subsampled frequency
            //VerbatimLogger.info("\n"+luceneUtils.getGlobalTermFreq(term) +" "+term.text()+" "+subsamplingProbabilities.get(fieldName+":"+bytes.utf8ToString()));
          }
          // construct negative sampling table, taking into account subsampling probabilities
          totalPool += Math.pow(subdiscount*luceneUtils.getGlobalTermFreq(term), .5);  //.75 changed to .5 as per fasttext code
          termDic.put(totalPool, term.text());
          }  //all terms for one field
      } // all fields
      VerbatimLogger.info("\n");
      if (subsamplingProbabilities !=null && subsamplingProbabilities.size() > 0)
        VerbatimLogger.info("Selected for subsampling: " + subsamplingProbabilities.size() + " terms.\n");
    } //end subsampling condition

    VerbatimLogger.info(
        "There are now elemental term vectors for " + tc + " terms (and " + luceneUtils.getNumDocs() + " docs).\n");


    totalDocCount.set(0);
   
    if (qsize > luceneUtils.getNumDocs()) //small document collection
    	qsize = luceneUtils.getNumDocs() / 10;
    
    for (int trainingcycle = 0; trainingcycle <= flagConfig.trainingcycles(); trainingcycle++) {
    
      initializeRandomizationStartpoints(qsize);
      exhaustedQ.set(false);
      theQ = new ConcurrentLinkedQueue<>();
      totalQueueCount.set(0);
      populateQueue();
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

    	  if (theQ.size() < qsize/2)
    	  { populateQueue(); }
      }

      VerbatimLogger.info("\nTime for training cycle " + (System.currentTimeMillis() - cycleStart) + "ms \n");
      VerbatimLogger.info("\nProcessed " +totalQueueCount.get() +" documents");
    } //end of training cycles
    
    VerbatimLogger.info("\nCreated " + semanticTermVectors.getNumVectors() + " term vectors ...\n");


    // If building a permutation index, these need to be written out to be reused.
    //
    // TODO(dwiddows): It is odd to do this here while not writing out the semantic
    // term vectors here.  We should redesign this.
    if (flagConfig.encodingmethod().equals(EncodingMethod.EMBEDDINGS) && (!flagConfig.notnormalized || flagConfig.subword_embeddings()))
    		{
    			Enumeration<ObjectVector> g = semanticTermVectors.getAllVectors();
    			 while (g.hasMoreElements()) {
    				
    				 ObjectVector nextObjectVector = g.nextElement();
    				 
    				 
    				 if (flagConfig.subword_embeddings())
    				 {
    					 ArrayList<String> subwordStrings = getComponentNgrams(nextObjectVector.getObject().toString());
    					 
    					 //used in the EARP paper: weight of each subword (ngram) == weight of original word
    					 float weightReduction = 1 / ((float) subwordStrings.size()+1);
    					 Vector wordVec = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());
        				 
    					 //if flagConfig.balanced_subwords(), combined weight of all subwords (ngrams) == weight of original word
    					 if (flagConfig.balanced_subwords()) weightReduction = 1; 
    					 
    					 wordVec.superpose(nextObjectVector.getVector(), weightReduction, null);
        					
    					 if (flagConfig.balanced_subwords()) weightReduction = 1 / ((float) subwordStrings.size()); 
    					 
    					 
    					  for (String subword: subwordStrings)
    					  { 
    						  Vector subwordVector = subwordEmbeddingVectors.getVector(subword, false);
    						 // nextObjectVector.getVector().superpose(subwordVector, weightReduction,null);
    						  wordVec.superpose(subwordVector, weightReduction,null);
    						  
    					 }
    					  
    					  nextObjectVector.setVector(wordVec);
    					
    				 }
    				 
    				 
    				 
    				 if (!flagConfig.notnormalized())
					   nextObjectVector.getVector().normalize();
    					 
    }
    		}
    
    if ((flagConfig.encodingmethod().equals(EncodingMethod.EMBEDDINGS)) || flagConfig.positionalmethod() == PositionalMethod.PERMUTATION
        || flagConfig.positionalmethod() == PositionalMethod.PERMUTATIONPLUSBASIC
        && (!retraining || flagConfig.encodingmethod().equals(EncodingMethod.EMBEDDINGS))) {
      VerbatimLogger.info("Normalizing and writing elemental vectors to " + flagConfig.elementalvectorfile() + "\n");
      Enumeration<ObjectVector> f = elementalTermVectors.getAllVectors();

      if (!flagConfig.notnormalized())
      while (f.hasMoreElements()) {
        f.nextElement().getVector().normalize();
      }
    }

    VectorStoreWriter.writeVectorsInLuceneFormat(flagConfig.elementalvectorfile()+".bin", flagConfig, this.elementalTermVectors);
    
    if (permutationCache != null)
    {
    VectorType typeA = flagConfig.vectortype();
    flagConfig.setVectortype(VectorType.PERMUTATION);
    VectorStoreWriter.writeVectorsInLuceneFormat(flagConfig.permutationcachefile()+".bin", flagConfig, this.permutationCache);
    flagConfig.setVectortype(typeA);
    }
    
    //write out document vectors
    if (flagConfig.encodingmethod().equals(EncodingMethod.EMBEDDINGS) && flagConfig.docindexing().equals(DocIndexingStrategy.INMEMORY))
    {
    	 Enumeration<ObjectVector> f = embeddingDocVectors.getAllVectors();

    	    // Open file and write headers.
    	    File vectorFile = new File(
    	        VectorStoreUtils.getStoreFileName(flagConfig.docvectorsfile(), flagConfig));
    	    String parentPath = vectorFile.getParent();
    	    if (parentPath == null) parentPath = "";
    	    FSDirectory fsDirectory = FSDirectory.open(FileSystems.getDefault().getPath(parentPath));

    	    IndexOutput outputStream = fsDirectory.createOutput(vectorFile.getName(), IOContext.DEFAULT);

    	    VerbatimLogger.info("Writing vectors incrementally to file " + vectorFile + " ... ");

    	    // Write header giving number of dimension for all vectors.
    	    outputStream.writeString(VectorStoreWriter.generateHeaderString(flagConfig));
    	 
         while (f.hasMoreElements()) {
           
        	 ObjectVector 	nextObjectVector = f.nextElement();
           	 Vector 		nextVector		 = nextObjectVector.getVector();
        	 
           	if (!flagConfig.notnormalized())
           	  nextVector.normalize();
        	 
        	 int 	  docID = (Integer) nextObjectVector.getObject();
             String docName = ""+docID; //luceneUtils.getExternalDocId(docID);
           
             // All fields in document have been processed. Write out documentID and normalized vector.
            outputStream.writeString(docName);
            nextVector.writeToLuceneStream(outputStream);
           } // Finish iterating through documents.

           VerbatimLogger.info("Finished writing vectors.\n");
           outputStream.close();
           fsDirectory.close();
            
         
    }
  }

  private void processEmbeddings(
      Vector embeddingVector, ArrayList<Vector> contextVectors,
      ArrayList<Integer> contextLabels, double learningRate, BLAS blas, int[] permutation, int[] inversePermutation) {
	  double scalarProduct = 0;
	  double error = 0;
	  int counter = 0;

    //for each contextVector   (there should be one "true" context vector, and a number of negative samples)
    for (Vector contextVec : contextVectors) {
    	
    	Vector duplicateContextVec 	 = contextVec.copy();
    	scalarProduct = VectorUtils.scalarProduct(embeddingVector, duplicateContextVec, flagConfig, blas, permutation);
       
      	
      if (!flagConfig.vectortype().equals(VectorType.BINARY)) //sigmoid function
  	  {	 //if label == 1, a context word - so the error is the (1-predicted probability of for this word) - ideally 0
          //if label == 0, a negative sample - so the error is the (predicted probability for this word) - ideally 0
    	  if (scalarProduct > MAX_EXP) error = contextLabels.get(counter++) - 1; 
    	  else if (scalarProduct < -MAX_EXP) error =  contextLabels.get(counter++);
    	  else error =  contextLabels.get(counter++) - sigmoidTable.sigmoid(scalarProduct);  
    } else //RELU-like function for binary vectors
      {
     	   scalarProduct = Math.max(scalarProduct, 0);
     	   error = contextLabels.get(counter++) - scalarProduct;
     	   //avoid passing floating points (the default behavior currently is to ignore these if the first superposition weight is an Integer)
      	  	error = Math.round(error*100);
     	  }
      
      //update the context vector and embedding vector, respectively
      if (error != 0)
      {
      VectorUtils.superposeInPlace(embeddingVector, contextVec, flagConfig, blas, learningRate * error, inversePermutation);
      VectorUtils.superposeInPlace(duplicateContextVec, embeddingVector, flagConfig, blas, learningRate * error, permutation);
      }
      }
    
  }
  
  /**
   * 
   * @param embeddingVectors
   * @param contextVectors
   * @param contextLabels
   * @param learningRate
   * @param blas
   * @param permutation 
   * @param inversePermutation
   */
  
  private void processEmbeddings(
	      ArrayList<Vector> embeddingVectors, ArrayList<Vector> contextVectors,
	      ArrayList<Integer> contextLabels, double learningRate, BLAS blas, int[] permutation, int[] inversePermutation) {
		  double scalarProduct = 0;
		  double error = 0;
		  int counter = 0;

		  Vector embeddingVector = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());
		  float weightReduction = 1; 
		  
		  //assume the first vector is the word vector - it is weighted as much as all the ngrams in concert if balanced_subwords is true
		  for (int v = 0; v < embeddingVectors.size(); v++)
		  {
			  if (flagConfig.balanced_subwords)
			  { if (v > 0) weightReduction = 1 / ((float) embeddingVectors.size()); } //technically should be embeddingVectors.size() -1
			  else weightReduction =  1 / ((float) embeddingVectors.size());
				
			  embeddingVector.superpose(embeddingVectors.get(v), weightReduction, null);
		  }
	    //for each contextVector   (there should be one "true" context vector, and a number of negative samples)
	    for (Vector contextVec : contextVectors) {
	    	
	    	Vector duplicateContextVec 	 = contextVec.copy();
	    	scalarProduct = VectorUtils.scalarProduct(embeddingVector, duplicateContextVec, flagConfig, blas, permutation);
	       
	      	
	      if (!flagConfig.vectortype().equals(VectorType.BINARY)) //sigmoid function
	  	  {	 //if label == 1, a context word - so the error is the (1-predicted probability of for this word) - ideally 0
	          //if label == 0, a negative sample - so the error is the (predicted probability for this word) - ideally 0
	    	  if (scalarProduct > MAX_EXP) error = contextLabels.get(counter++) - 1; 
	    	  else if (scalarProduct < -MAX_EXP) error =  contextLabels.get(counter++);
	    	  else error =  (float) (contextLabels.get(counter++) - sigmoidTable.sigmoid(scalarProduct));  
	    } else //RELU-like function for binary vectors
	      {
	     	   scalarProduct = Math.max(scalarProduct, 0);
	     	   error = contextLabels.get(counter++) - scalarProduct;
	     	   //avoid passing floating points (the default behavior currently is to ignore these if the first superposition weight is an Integer)
	      	  	error = Math.round(error*100);
	     	  }
	      
	      //update the context vector and embedding vector, respectively
	      if (error != 0)
	      {
	      VectorUtils.superposeInPlace(embeddingVector, contextVec, flagConfig, blas, learningRate * error, inversePermutation);
	      
	      weightReduction = 1; 
		  //assume the first vector is the word vector - it is weighted as much as all the ngrams in concert
	      for (int v = 0; v < embeddingVectors.size(); v++)
		  {
	    	//if (v > 0) weightReduction =  1 / (float) embeddingVectors.size();
	    	  if (flagConfig.balanced_subwords)
			  { if (v > 0) weightReduction = 1 / ((float) embeddingVectors.size()); } //technically should be embeddingVectors.size() -1 
			 
	    	VectorUtils.superposeInPlace(duplicateContextVec, embeddingVectors.get(v), flagConfig, blas, weightReduction * learningRate * error, permutation);    
		  }
	      }
	      
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
  private void processTermPositionVector(DocIdTerms terms, String field, BLAS blas)
      throws ArrayIndexOutOfBoundsException, IOException {
    if (terms == null) return;

    //Reconstruct document from term positions
    Hashtable<Integer, String> localTermPositions = new Hashtable<Integer, String>();

    //To accommodate "dynamic" sliding window that includes indexed/sampled terms only
    ArrayList<Integer> thePositions = new ArrayList<Integer>();

    TermsEnum termsEnum = terms.terms.iterator();
    BytesRef text;
   
    Integer docID = terms.docID; 
    
    //For each unique term in the document
    while ((text = termsEnum.next()) != null) {
      
      String theTerm = text.utf8ToString();
      if (!semanticTermVectors.containsVector(theTerm)) continue;

      PostingsEnum docsAndPositions = termsEnum.postings(null);
      if (docsAndPositions == null) continue;
      docsAndPositions.nextDoc();
      int freq = docsAndPositions.freq();
     
      
      //iterate through all positions of this term
      for (int x = 0; x < freq; x++) {
        int thePosition = docsAndPositions.nextPosition();
        
        //subsampling of frequent terms
        if (subsamplingProbabilities != null && subsamplingProbabilities.containsKey(field + ":" + theTerm) && random.nextDouble() <= subsamplingProbabilities.get(field + ":" + theTerm)) 
       	{
        	if (flagConfig.exactwindowpositions())	
        		{
        		localTermPositions.put(thePosition, "_BLANK_");
        		thePositions.add(thePosition);
        		}
        	continue;
        }
        //if we have survived the subsampling procedure, record this term position, and the terms it contains
        localTermPositions.put(thePosition, theTerm);
         thePositions.add(thePosition);    
    }
    }
    // Sort positions with indexed/sampled terms
    // Effectively this compresses the sequence of terms in this document, such that
    // terms that were excluded (stoplisted, or didn't meet frequency thresholds)
    // do not result in "blank" positions - rather, they are squeezed out of the sequence
    Collections.sort(thePositions);
 
    //vestigial code for the purpose of error checking for sequence reconstruction from Lucene
    //int cnt=0;
    //for (int index:thePositions)
    //System.out.println(++cnt+" "+index+" "+localTermPositions.get(index));

    //move the sliding window through the occupied positions (the focus position is the position of the "observed" term)
    for (int occupiedPositionNumber = 0; occupiedPositionNumber < thePositions.size(); occupiedPositionNumber++) {

      int focusposn = thePositions.get(occupiedPositionNumber); //returns the position in the sentence in the [positionNumber_th] position in the sequence
      String focusterm = localTermPositions.get(focusposn); //returns the term in this position
      
      //ignore this position if empty / subsampled 
      if (flagConfig.exactwindowpositions() && focusterm.equals("_BLANK_")) continue;
      
       //word2vec uniformly samples the window size - we will try this too
      int effectiveWindowRadius = flagConfig.windowradius();
      if (flagConfig.subsampleinwindow) effectiveWindowRadius = random.nextInt(flagConfig.windowradius()) + 1;

      int windowstart = Math.max(0, occupiedPositionNumber - effectiveWindowRadius);
      
      //permit assymetrical windows (shorter on the left - for evaluation against empirical data)
      if (flagConfig.truncatedleftradius() > 0)
      {
    	  	 int truncatedLeftRadius = Math.min(flagConfig.truncatedleftradius(), effectiveWindowRadius);
    	  	 windowstart = Math.max(0, occupiedPositionNumber - truncatedLeftRadius);
      }
      
      int windowend = Math.min(occupiedPositionNumber + effectiveWindowRadius, thePositions.size()-1);

      for (int cursorPositionNumber = windowstart; cursorPositionNumber <= windowend; cursorPositionNumber++) {

      //ignore the term in the center of the window (the focus term) unless generating document vectors simultaneously
	  if (cursorPositionNumber == occupiedPositionNumber && ! (flagConfig.encodingmethod().equals(EncodingMethod.EMBEDDINGS)  && flagConfig.docindexing().equals(DocIndexingStrategy.INMEMORY) ) )
			  continue;
 
	  //retrieve a term from within the sliding window
	  String coterm = localTermPositions.get(thePositions.get(cursorPositionNumber));
      
	//ignore this position if empty / subsampled 
      if (flagConfig.exactwindowpositions() && coterm.equals("_BLANK_")) continue;
      
    //if (coterm.equals(focusterm)) continue;
	  
	  //for permutation-based methods
	  int[] permutation =  null;
	  int[] inversePermutation = null;
	  
	  //"compressed" sliding windows, absolute position will not be preserved - we encode the adjacent term within the "compressed" sliding window
	  //ignoring subsampled terms and terms without vector representations
	  int desiredPermutation = cursorPositionNumber - occupiedPositionNumber;
	  
      if (flagConfig.positionalmethod().equals(PositionalMethod.PERMUTATION) || flagConfig.positionalmethod().equals(PositionalMethod.PROXIMITY)) {
    	    	permutation =  ((PermutationVector) permutationCache.getVector(desiredPermutation)).getCoordinates();
    	    	inversePermutation =  ((PermutationVector) permutationCache.getVector("_"+ desiredPermutation)).getCoordinates();
                
    	    	if (permutation == null) VerbatimLogger.info("null permutation");
                if (inversePermutation == null) VerbatimLogger.info("null inverse permutation");
      	      
    	          	} else if (flagConfig.positionalmethod().equals(PositionalMethod.DIRECTIONAL)) {
            	permutation =  ((PermutationVector) permutationCache.getVector((int) Math.signum(desiredPermutation))).getCoordinates();
            	inversePermutation =  ((PermutationVector) permutationCache.getVector("_"+ (int) Math.signum(desiredPermutation))).getCoordinates();
                
            	if (permutation == null) VerbatimLogger.info("null permutation");
                if (inversePermutation == null) VerbatimLogger.info("null inverse permutation");
    	          	}
      
      
	 // if (permutation != null)
		//  	inversePermutation = PermutationUtils.getInversePermutation(permutation);
      
	 //get context vector for co-occurring term
	  Vector toSuperpose = elementalTermVectors.getVector(coterm);

        /**
         * Implementation of skipgram with negative sampling (Mikolov 2013)
         */

        if (flagConfig.encodingmethod().equals(EncodingMethod.EMBEDDINGS)) {
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
	  
	  if (cursorPositionNumber != occupiedPositionNumber) //skip the focus term when training term vectors
	  {
		  
		// else  //process word - exclude in faster edition
		  
	  
	  //process word + ngrams
	  if (flagConfig.subword_embeddings())
	  {
		  ArrayList<String> subWords = getComponentNgrams(focusterm);
		  
		  
		  //add word first - for faster edition
		  ArrayList<Vector> subWordVectors = new ArrayList<Vector>();
		  
		  subWordVectors.add(semanticTermVectors.getVector(focusterm));
		  
		  for (String subword:subWords)
			  subWordVectors.add(subwordEmbeddingVectors.getVector(subword,false));  //if set to true, will subsample subwords
		
		  this.processEmbeddings(subWordVectors, contextVectors, contextLabels, alpha, blas, permutation, inversePermutation);
	  }
	  else processEmbeddings(semanticTermVectors.getVector(focusterm), contextVectors, contextLabels, alpha, blas, permutation, inversePermutation);
	  
	  	  
	  }
	
       
	     //include the focus term when training document vectors - currently this requires 
	      //storing all document vectors in memory, so isn't suitable for large corpora
	  	  //doc vectors are generated by treating each document as though it were
	      //the focus term in every sliding window, seems to produce good empirical results in practice - with multiple training cycles -
	      //despite being a very rough "one-line" approximation of previously document approaches (PV-DBOW)
	  	  //an open question is whether this has a beneficial effect on the resulting word vectors or not
          if (flagConfig.docindexing().equals(DocIndexingStrategy.INMEMORY))
          {
        	 if (!embeddingDocVectors.containsVector(docID))
        		   embeddingDocVectors.putVector(docID, VectorFactory.generateRandomVector(flagConfig.vectortype(), flagConfig.dimension(), flagConfig.seedlength, random));
        	  
        	  this.processEmbeddings(embeddingDocVectors.getVector(docID), contextVectors, contextLabels, alpha, blas , permutation, inversePermutation);
          } 
          
        } else {
          //random indexing variants
          float globalweight = luceneUtils.getGlobalTermWeight(new Term(field, coterm));

          // bind to appropriate position vector
          if (flagConfig.positionalmethod() == PositionalMethod.PROXIMITY) {
            toSuperpose = elementalTermVectors.getVector(coterm).copy();
            toSuperpose.bind(positionalNumberVectors.getVector(cursorPositionNumber - focusposn));
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
           permutation =  ((PermutationVector) permutationCache.getVector(cursorPositionNumber - focusposn)).getCoordinates();
            
            semanticTermVectors.getVector(focusterm).superpose(toSuperpose, globalweight, permutation);
          } else if (flagConfig.positionalmethod() == PositionalMethod.DIRECTIONAL) {
              permutation =  ((PermutationVector) permutationCache.getVector(Math.signum(cursorPositionNumber - focusposn))).getCoordinates();
              
            semanticTermVectors.getVector(focusterm).superpose(toSuperpose, globalweight, permutation);
          }
        }
      } 
      //end of current sliding window
    } //end of all sliding windows

    totalDocCount.incrementAndGet();
  }
}

