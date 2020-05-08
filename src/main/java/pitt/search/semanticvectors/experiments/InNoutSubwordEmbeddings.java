package pitt.search.semanticvectors.experiments;

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
import java.util.Collections;
import java.util.Hashtable;
import java.util.Enumeration;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.io.File;
import java.io.IOException;
import java.lang.RuntimeException;
import java.nio.file.FileSystems;

import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;
import org.netlib.blas.BLAS;

import pitt.search.semanticvectors.CompressedVectorStoreRAM;
import pitt.search.semanticvectors.ElementalVectorStore;
import pitt.search.semanticvectors.ElementalVectorStore.ElementalGenerationMethod;
import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.LuceneUtils;
import pitt.search.semanticvectors.ObjectVector;
import pitt.search.semanticvectors.VectorStore;
import pitt.search.semanticvectors.VectorStoreRAM;
import pitt.search.semanticvectors.VectorStoreWriter;
import pitt.search.semanticvectors.utils.Bobcat;
import pitt.search.semanticvectors.utils.SigmoidTable;
import pitt.search.semanticvectors.utils.VerbatimLogger;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorType;
import pitt.search.semanticvectors.vectors.VectorUtils;

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
public class InNoutSubwordEmbeddings implements VectorStore {

  private volatile VectorStoreRAM semanticTermVectors;
  private volatile VectorStore indexVectors;
  private FlagConfig flagConfig;
  private volatile CompressedVectorStoreRAM subwordEmbeddingVectors;
  private LuceneUtils luceneUtils;
  private static final Logger logger = Logger.getLogger(
      InNoutSubwordEmbeddings.class.getCanonicalName());
  
  private AtomicBoolean exhaustedQ = new AtomicBoolean();
  private int qsize = 50000;
  private ConcurrentSkipListMap<Double, String> termDic;
  private ConcurrentHashMap<String, Double> subsamplingProbabilities;
  private double totalPool 	= 0; //total pool of terms probabilities for negative sampling corpus
  private double initial_alpha = 0.025;
  private double alpha 		   = 0.025;
  private double minimum_alpha = 0.0001;
  private int tc = 0;
  private static int instabilitycount = 0;
  private AtomicInteger totalDocCount = new AtomicInteger();
  private AtomicInteger totalQueueCount = new AtomicInteger();
  private SigmoidTable sigmoidTable = new SigmoidTable(6,1000);
  private ConcurrentLinkedQueue<Integer> randomStartpoints;
  private ConcurrentLinkedQueue<DocIdTerms> theQ;
  private static final int MAX_EXP = 6;
  
  /**
   * Creates SRI instance, and trains term vectors as well as 
   * document vectors if indicated.
   */
  public InNoutSubwordEmbeddings(FlagConfig flagConfig)
      throws IOException, RuntimeException {

			
    this.flagConfig = flagConfig;
    semanticTermVectors = new VectorStoreRAM(flagConfig);
    
    
     this.luceneUtils = new LuceneUtils(flagConfig);

    //initialize zero vectors and index vectors
    initializeVectorStores();

    
    theQ = new ConcurrentLinkedQueue<DocIdTerms>();
   
    if (qsize > luceneUtils.getNumDocs()) //small document collection
    	qsize = luceneUtils.getNumDocs() / 10;
    
    for (tc = 0; tc <= flagConfig.trainingcycles(); tc++)
    {   
    	
    	VerbatimLogger.info("Cycle "+tc);
        initializeRandomizationStartpoints(qsize);
        exhaustedQ.set(false);
        theQ = new ConcurrentLinkedQueue<>();
        totalQueueCount.set(0);
        populateQueue();
     	
        int numthreads = flagConfig.numthreads();
        ExecutorService executor = Executors.newFixedThreadPool(numthreads);

        for (int q = 0; q < numthreads; q++) {
          executor.execute(new TrainDocThread(q));
        }

        executor.shutdown();
        // Wait until all threads are finish
        while (!executor.isTerminated()) {
      } // Finish iterating through predications.


  }
    
    logger.info("\nCreated " + semanticTermVectors.getNumVectors() + " term vectors ...");
    
   if (flagConfig.subword_embeddings())
		 {
	   		Enumeration<ObjectVector> d = indexVectors.getAllVectors();

	   		while (d.hasMoreElements())
	   		{
	   		  ObjectVector nextObjectVector = d.nextElement();
			  ArrayList<String> subwordStrings = this.subwordEmbeddingVectors.getComponentNgrams(nextObjectVector.getObject().toString());
			 
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
		 }
    
    
    
    if (!flagConfig.notnormalized)
    {
    logger.info("\nNormalizing term vectors");
    Enumeration<ObjectVector> e = semanticTermVectors.getAllVectors();

    
    while (e.hasMoreElements()) {
      ObjectVector temp = (ObjectVector) e.nextElement();
      temp.getVector().normalize();
    }
    
    logger.info("\nCreated " + indexVectors.getNumVectors() + " context vectors ...");
    logger.info("\nNormalizing term vectors");
    Enumeration<ObjectVector> f = indexVectors.getAllVectors();

    while (f.hasMoreElements()) {
      ObjectVector temp = (ObjectVector) f.nextElement();
      temp.getVector().normalize();
    }
    }

    VectorStoreWriter.writeVectorsInLuceneFormat("inputweightvectors.bin", flagConfig, semanticTermVectors);
    VectorStoreWriter.writeVectorsInLuceneFormat("outputweightvectors.bin", flagConfig, indexVectors);
    if (flagConfig.write_subwordembeddings())
    		VectorStoreWriter.writeVectorsInLuceneFormat("subwordembeddings.bin", flagConfig, subwordEmbeddingVectors.exportVectorStoreRAM());

  }
  
 
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
   * 
   * @author tcohen
   *
   */
  private class TrainDocThread implements Runnable {
	    
	  	int dcnt = 0;
	    int threadno = 0;
	    BLAS blas = null;
	    double time = 0;
	    Random random = null;

	    public TrainDocThread(int threadno) {
	      this.threadno = threadno;
	      this.blas = BLAS.getInstance();
	      this.random = new Random();
	      this.time = System.currentTimeMillis();
	      VerbatimLogger.info("Starting thread "+threadno+"...");
	    }

	    
	    @Override
	    public void run() {

	      while (!queueExhausted()) {
	        for (String field : flagConfig.contentsfields()) {
	          try {
	            DocIdTerms terms = drawFromQueue();
	            if (terms != null) {
	              //VerbatimLogger.severe("No term vector for document "+dc);
	            	  processTermVector(terms, field, random, blas);
	               }
	             } catch (ArrayIndexOutOfBoundsException e) {
	            // TODO Auto-generated catch block
	            e.printStackTrace();
	          }
	        }

	        // Output progress counter.
	        if ((dcnt % 10000 == 0) || (dcnt < 10000 && dcnt % 1000 == 0)) {
	          VerbatimLogger.info("[T" + threadno + "]" + " processed " + dcnt + " documents in " + ("" + ((System.currentTimeMillis() - time) / (1000 * 60))).replaceAll("\\..*", "") + " min..");


	        }
	        dcnt++;
	      } //all documents processed
	    }
	  }
	    

  
  /**
   * 
   * @param embeddingVector
   * @param contextVectors
   * @param contextLabels
   * @param learningRate
   * @param blas
   */
  
  
  
  
  
  
  private ArrayList<Double> processEmbeddings(
      Vector embeddingVector, ArrayList<Vector> contextVectors,
      ArrayList<Integer> contextLabels, double learningRate, BLAS blas) {
	  int counter = 0;

	  ArrayList<Double> errors = new ArrayList<Double>();
	  
    //for each contextVector   (there should be one "true" context vector, and a number of negative samples)
    for (Vector contextVec : contextVectors) {
    	
    	  double scalarProduct = 0;
    	  double error = 0;
    	
    	
    	Vector duplicateContextVec 	 = contextVec.copy();
    	scalarProduct = VectorUtils.scalarProduct(embeddingVector, duplicateContextVec, flagConfig, blas);
    //if (Math.abs(scalarProduct) > MAX_EXP) 
    	 //{counter++; continue;} //skipping cases with outsize scalar products, as per prior implementations - may avoid numerically unstable term vectors down the line
    	
    //	if (scalarProduct > 1000)
    	//	System.out.println(scalarProduct);
    	
      if (!flagConfig.vectortype().equals(VectorType.BINARY)) //sigmoid function
  	  {
    		scalarProduct = sigmoidTable.sigmoid(scalarProduct); 
        //if label == 1, a context word - so the error is the (1-predicted probability of for this word) - ideally 0
        //if label == 0, a negative sample - so the error is the (predicted probability for this word) - ideally 0
    		 error = contextLabels.get(counter++) - scalarProduct;  
    		// if (contextLabels.get(counter-1) == 1) System.out.print(" ---> "+error*learningRate+" <--- : ");
  	  } else //RELU-like function for binary vectors
      {
     	   scalarProduct = Math.max(scalarProduct, 0);
     	   error = contextLabels.get(counter++) - scalarProduct;
     	   //avoid passing floating points (the default behavior currently is to ignore these if the first superposition weight is an Integer)
      	  	error = Math.round(error*100);
     	  }
      
      errors.add(error);
      
      //update the context vector and embedding vector, respectively
      //VectorUtils.superposeInPlace(embeddingVector, contextVec, flagConfig, blas, learningRate * error);
      VectorUtils.superposeInPlace(duplicateContextVec, embeddingVector, flagConfig, blas, learningRate * error);
      
    }
    return errors;
  }
  
  
/**
 * 
 * @param terms
 * @param field
 * @param random
 * @param blas
 */

  

  public void processTermVector(DocIdTerms terms, String field, Random random, BLAS blas)
  {
	  	/* TermPositionVectors contain arrays of (1) terms as text (2)
			 * term frequencies and (3) term positions within a
			 * document. The index of a particular term within this array
			 * will be referred to as the 'local index' in comments.
			 */
	   
	        ArrayList<String> localTerms = new ArrayList<String>();
	        ArrayList<Integer> freqs = new ArrayList<Integer>();
	        Hashtable<Integer, Integer> localTermPositions = new Hashtable<Integer, Integer>();

	        TermsEnum termsEnum;
			try {
				
			termsEnum = terms.terms.iterator();
			int docID = terms.docID;
	        BytesRef text;
	        int termcount = 0;

	        //get all the terms and frequencies required for processing
	        while ((text = termsEnum.next()) != null) {
	          
	        String theTerm = text.utf8ToString();
	
	          if (!semanticTermVectors.containsVector(theTerm) && !indexVectors.containsVector(theTerm)) continue;
	          if (subsamplingProbabilities != null && subsamplingProbabilities.containsKey(field + ":" + theTerm) && random.nextDouble() <= subsamplingProbabilities.get(field + ":" + theTerm)) 
	          continue;
	              
	          PostingsEnum docsAndPositions = termsEnum.postings(null);
	           if (docsAndPositions == null) continue;
	           
	          docsAndPositions.nextDoc();
	          int freq = docsAndPositions.freq();
	          freqs.add(freq);
	          localTerms.add(theTerm);

	          for (int x = 0; x < freq; x++) {
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
	          float globalweight = luceneUtils.getGlobalTermWeight(new Term(field, aTerm));
	          float localweight = luceneUtils.getLocalTermWeight(freq);
	          freaks[x] = localweight * globalweight;
	          norm += Math.pow(freaks[x], 2);
	        }

	        /** normalize the transient document vector (it contains all non-zero values) **/
	        norm = (float) Math.sqrt(norm);
	        for (int x = 0; x < freaks.length; x++)
	          freaks[x] = freaks[x] / norm;

	        /** create local random index and term vectors for relevant terms**/
	        Vector[] localoutputvectors = new Vector[numwords];
	        Vector[] localinputvectors = new Vector[numwords];

	        for (short tcn = 0; tcn < numwords; tcn++) {
	          // Only terms that have passed the term filter are included in the VectorStores.
	           /** retrieve relevant random index vectors**/
	           if (localTerms.get(tcn).startsWith(">")) localoutputvectors[tcn] = indexVectors.getVector(localTerms.get(tcn));

	            /** retrieve the float[] arrays of relevant term vectors **/
	            if (!localTerms.get(tcn).startsWith(">")) localinputvectors[tcn] = semanticTermVectors.getVector(localTerms.get(tcn));
	            

	        }
	        
	              if (flagConfig.encodingmethod().equals(pitt.search.semanticvectors.TermTermVectorsFromLucene.EncodingMethod.EMBEDDINGS))
	              {
	            	 
	            	   for (int x = 0; x < localTerms.size(); x++)
	            	  for (int y = 0; y < localTerms.size(); y++)
	            	  {
	            		  if (x == y) continue;
	            		  
	            	  	  ArrayList<Vector> contextVectors = new ArrayList<Vector>();
	        	            ArrayList<Integer> contextLabels = new ArrayList<Integer>();
	        	            ArrayList<String> contextTerms = new ArrayList<String>();
	        	            
	        	                
	            	if (localinputvectors[x] != null && localoutputvectors[y] != null) //predictors start with underscore, outcomes do not
	            	{
	            	              
	            	               int subwordCount = 0;
	            	               
	            	               if (flagConfig.subword_embeddings())
	            	         	  {
	            	            	   	   
	            	                   String outputTerm = localTerms.get(y);
		            	               contextTerms.add(outputTerm);
	            	            	   	   ArrayList<String> subWords = 
	            	            	   			subwordEmbeddingVectors.getComponentNgrams(outputTerm);
	            	         		  
	            	            	   	   Vector toAdd = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());  
       	            	   			   toAdd.superpose(localoutputvectors[y],1 / (double) (subWords.size()+1),null);
	            	             
       	            	   			   for (String subword:subWords)
	            	         			  toAdd.superpose(subwordEmbeddingVectors.getVector(subword,false), 1 / (double) (subWords.size()+1), null);  //if set to true, will subsample subwords
	            	         		
	            	         		 //add the evolving document vector for the context
		            	               contextVectors.add(toAdd);
		            	               contextLabels.add(1);
		            	               
	            	            	  }
	            	               else
	            	               {
	            	            	   //add the evolving document vector for the context
		            	               contextVectors.add(localoutputvectors[y]);
		            	               contextLabels.add(1);
		            	               
	            	               }
	            	               
	            	               //add flagConfig.negsamples() randomly drawn terms with label '0'
	              	               //these terms (and hence their context vectors)
	              	               //are drawn with a probability of (global occurrence)^0.75, as recommended
	              	               //by Mikolov and other authors
	              	           
	            	               
	            	               while (contextVectors.size() <= flagConfig.negsamples) {
	              	                 Vector negsampleVector = null;
	              	                 double max = totalPool; //total (non unique) term count

	              	                 while (negsampleVector == null) {
	              	                   double test = random.nextDouble()*max;
	              	                   if (termDic.ceilingEntry(test) != null) {
	              	                 	  String testTerm = termDic.ceilingEntry(test).getValue();
	              	                   		if (!localTerms.contains(testTerm) && testTerm.startsWith(">")) //if the term is not in the document
	              	                   		{ negsampleVector = indexVectors.getVector(testTerm);
	              	                   		
	              	                   	 if (flagConfig.subword_embeddings())
	   	            	         	  {
	   	            	            	   	   
	   	            	                   String negSampleTerm = testTerm;
	   		            	               contextTerms.add(negSampleTerm);
	   	            	            	   	   ArrayList<String> subWords = 
	   	            	            	   			subwordEmbeddingVectors.getComponentNgrams(negSampleTerm);
	   	            	         		  
	   	            	            	   	   Vector toAdd = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());  
	          	            	   		   toAdd.superpose(negsampleVector,1 / (double) (subWords.size()+1),null);
	          	            	   		
	          	            	   			for (String subword:subWords)
	   	            	         			toAdd.superpose(subwordEmbeddingVectors.getVector(subword,false), 1 / (double) (subWords.size()+1), null);  //if set to true, will subsample subwords
	   	            	         		
	   	            	         		 //add the evolving document vector for the context
	   		            	               contextVectors.add(toAdd);
	   		            	               contextLabels.add(0);
	   		            	               
	   	            	            	  }
	              	                   	 else
	              	                   	 {
	              	                   		contextVectors.add(negsampleVector);
	              	                			contextLabels.add(0);
	              	                   	 }
	              	                   		
	              	                   		
	              	                   		}
	              	                   }
	              	                   	}
	              	             }
	        	            	  
	            	              
	            	             
	            	               //for each contextVector   (there should be one "true" context vector, and a number of negative samples)
	            	               
	            	              // System.out.print(sigmoidTable.sigmoid(VectorUtils.scalarProduct(contextVectors.get(0),embeddingDocVector,flagConfig, blas))+": ");
	            	               if (flagConfig.subword_embeddings())
	            	               {
	            	            	     ArrayList<Double> errors = processEmbeddings(localinputvectors[x], contextVectors, contextLabels, alpha,blas);
	            	            	     int ind = 0;
	            	            	     
	            	            	     for (String outputTerm:contextTerms)
	            	            	     {
	            	            	    	   try {
	            	            	    	 	double error = errors.get(ind);
	            	            	    	 	ind++;
		            	            	    	   ArrayList<String> subWords = 
		   	            	            	   			subwordEmbeddingVectors.getComponentNgrams(outputTerm);
		   	            	         	localoutputvectors[y].superpose(localinputvectors[x],(alpha*error)  / ((double) (subWords.size()+1)),null);
		   	            	             
		   	            	            	  for (String subword:subWords)
		   	            	         			  subwordEmbeddingVectors.getVector(subword,false).superpose(localinputvectors[x], (alpha*error) / ((double) (subWords.size()+1)), null);  //if set to true, will subsample subwords
		   	            	         	
	            	            	    	   }
	            	            	    	   catch (Exception e)
	            	            	    	   {
	            	            	    		   System.out.println(errors.size()+"\t"+outputTerm);
	            	            	    	   }
	            	            	    	 	
	            	            	    	 
	            	            	     }
	            	               
	            	               }
	            	               else    
	            	               processEmbeddings(localinputvectors[x],contextVectors,contextLabels, alpha, blas);
	            	             //  System.out.print(sigmoidTable.sigmoid(VectorUtils.scalarProduct(contextVectors.get(0),embeddingDocVector,flagConfig, blas))+"\n");
	   	              	        

	             

		            	          }
	            		

	              
	            	  }
	            	  
	            	 
	              }
	              else
	              {	  
	            	   for (int x = 0; x < localTerms.size() - 1; x++)
	            	          for (int y = x + 1; y < localTerms.size(); y++) {
	            	            if ((localinputvectors[x] != null) && (localinputvectors[y] != null)) {
	            	              float freq = freaks[x];
	            	              float freq2 = freaks[y];
	            	              float mult = freq * freq2; //calculate this component of the scalar product between term-by-doc vectors
	            	   
	            	  
	            	  localinputvectors[x].superpose(localoutputvectors[y], mult, null);
	            	  localinputvectors[y].superpose(localoutputvectors[x], mult, null);
	              }
	            }
	          }} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			
			 int dc = totalDocCount.incrementAndGet();
		       /* output progress counter */
		      if ((dc % 10000 == 0) || (dc < 10000 && dc % 1000 == 0)) {
		        System.err.print(dc + " ... ");
		        double proportionComplete = dc / (double) ( (1+flagConfig.trainingcycles()) * (luceneUtils.getNumDocs()));
	            alpha = initial_alpha - (initial_alpha - minimum_alpha) * proportionComplete;
	        
		      }
	  
  }
  
  public Vector getVector(Object term) {
    return semanticTermVectors.getVector(term);
  }

  public Enumeration<ObjectVector> getAllVectors() {
    return semanticTermVectors.getAllVectors();
  }

  

  
  //creates zero vectors for terms to be indexed

  private void initializeVectorStores() throws IOException {
    semanticTermVectors = new VectorStoreRAM(flagConfig);
    
    Random random = new Random();
    termDic = new ConcurrentSkipListMap<Double, String>();
    
    if (flagConfig.initialtermvectors().isEmpty()) {
      indexVectors = new ElementalVectorStore(flagConfig);
    } else {
      indexVectors = new VectorStoreRAM(flagConfig);
      ((VectorStoreRAM) indexVectors).initFromFile(flagConfig.initialtermvectors());

    }
    
    //store n-gram vectors (memory intensive)
    if (flagConfig.subword_embeddings())
    	{
    		VerbatimLogger.info("Using subword embeddings\n");
    		this.subwordEmbeddingVectors = new CompressedVectorStoreRAM(flagConfig);
    	}
    
    long totalTermCount = 0;
    
    for (String fieldName : this.flagConfig.contentsfields()) {
      Terms terms = this.luceneUtils.getTermsForField(fieldName);
      TermsEnum termEnum = terms.iterator();


      BytesRef bytes;
      while ((bytes = termEnum.next()) != null) {
        Term term = new Term(fieldName, bytes);
        totalTermCount+=luceneUtils.getGlobalTermFreq(term);
        
        if (semanticTermVectors.containsVector(term.text()) || indexVectors.containsVector(term.text())) continue;
        if (!luceneUtils.termFilter(term)) continue;
        
        
        Vector termVector = null; 
        //construct table for negative sampling
        if (flagConfig.encodingmethod().equals(pitt.search.semanticvectors.TermTermVectorsFromLucene.EncodingMethod.EMBEDDINGS)) {
            totalPool += Math.pow(luceneUtils.getGlobalTermFreq(new Term(fieldName,term.text())), .75);
            termDic.put(totalPool, term.text());
            if (flagConfig.elementalmethod() == ElementalGenerationMethod.CONTENTHASH)
            		random.setSeed(Bobcat.asLong("input"+term.text()));
            termVector = VectorFactory.generateRandomVector(flagConfig.vectortype(), flagConfig.dimension(), flagConfig.seedlength(), random);
        } else termVector = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());
        
        
        // Place each term vector in the vector store.
        if (!term.text().startsWith(">")) semanticTermVectors.putVector(term.text(), termVector);
        else indexVectors.getVector(term.text());

      }
      VerbatimLogger.info(String.format(
          "There are %d terms (and %d docs)", totalTermCount, this.luceneUtils.getNumDocs()));
    }
    
    
    //precalculate probabilities for subsampling (need to iterate again once total term frequency known)
    if (flagConfig.samplingthreshold() > -1 && flagConfig.samplingthreshold() < 1) {
      subsamplingProbabilities = new ConcurrentHashMap<String, Double>();

      VerbatimLogger.info("Populating subsampling probabilities - total term count = " + totalTermCount + " which is " + (totalTermCount / luceneUtils.getNumDocs()) + " per doc on average");
      int count = 0;
      for (String fieldName : flagConfig.contentsfields()) {
        TermsEnum terms = this.luceneUtils.getTermsForField(fieldName).iterator();
        BytesRef bytes;
        while ((bytes = terms.next()) != null) {
          Term term = new Term(fieldName, bytes);
          if (++count % 10000 == 0) VerbatimLogger.info(".");

          // Skip terms that don't pass the filter.
          if (!semanticTermVectors.containsVector(term.text()) && !indexVectors.containsVector(term.text())) continue;

          
          double globalFreq = (double) luceneUtils.getGlobalTermFreq(term) / (double) totalTermCount;

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

    
  }


  @Override
  public boolean containsVector(Object object) {
    // TODO Auto-generated method stub
    return semanticTermVectors.containsVector(object);
  }

  /**
   * @return a count of the number of vectors in the store.
   */
  public int getNumVectors() {
    return semanticTermVectors.getNumVectors();
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

    new InNoutSubwordEmbeddings(flagConfig);
    VerbatimLogger.info("Number docs ignored on account of instability = " + instabilitycount + "\n");

  }
}

  

