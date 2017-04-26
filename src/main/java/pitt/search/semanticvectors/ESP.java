/**
 Copyright (c) 2008, Arizona State University.

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

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.BytesRef;
import org.netlib.blas.BLAS;

import pitt.search.semanticvectors.ElementalVectorStore.ElementalGenerationMethod;
import pitt.search.semanticvectors.utils.Bobcat;
import pitt.search.semanticvectors.utils.SigmoidTable;
import pitt.search.semanticvectors.utils.VerbatimLogger;
import pitt.search.semanticvectors.vectors.BinaryVector;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorType;
import pitt.search.semanticvectors.vectors.VectorUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;


/**
 * Generates predication vectors incrementally.Requires as input an index containing 
 * documents with the fields "subject", "predicate" and "object"
 *
 * Produces as output the files: elementalvectors.bin, predicatevectors.bin and semanticvectors.bin
 * 
 *  This class requires an index with a specific structure that can be generated 
 * 
 *
 * @author Trevor Cohen, Dominic Widdows
 */
public class ESP {
  private static final int MAX_EXP = 6;
  private static final Logger logger = Logger.getLogger(ESP.class.getCanonicalName());
  private FlagConfig flagConfig;
  private VectorStore elementalItemVectors, elementalPredicateVectors;
  private VectorStoreRAM semanticItemVectors;
  private static final String SUBJECT_FIELD = "subject";
  private static final String PREDICATE_FIELD = "predicate";
  private static final String OBJECT_FIELD = "object";
  private static final String PREDICATION_FIELD = "predication";
  private String[] itemFields = {SUBJECT_FIELD, OBJECT_FIELD};
  private int tc = 0;
  private double initial_alpha = 0.025;
  private double  alpha		   = 0.025;
  private double min_alpha = 0.0001;
  private SigmoidTable sigmoidTable = new SigmoidTable(MAX_EXP,1000);
  
  private ConcurrentHashMap<String,ConcurrentSkipListMap<Double, String>> termDic;
  private ConcurrentHashMap<String,Double> totalPool; //total pool of terms probabilities for negative sampling corpus

  private LuceneUtils luceneUtils;
  private ArrayList<String> addedConcepts;
  private static Random random;
  private java.util.concurrent.atomic.AtomicInteger dc = new java.util.concurrent.atomic.AtomicInteger(0);
  private java.util.concurrent.atomic.AtomicInteger pc = new java.util.concurrent.atomic.AtomicInteger(0);
  private ConcurrentLinkedQueue<Document> theQ = new ConcurrentLinkedQueue<Document>();
  private ConcurrentLinkedQueue<Integer> randomStartpoints = new ConcurrentLinkedQueue<Integer>();
  private ConcurrentHashMap<String,String> semtypes = new ConcurrentHashMap<String,String>();
  private HashMap<Object,String> cuis = new HashMap<Object,String>();
  private ConcurrentHashMap<String, Double> subsamplingProbabilities;
  
  private final boolean semtypesAndCUIs = false; //set to true to experiment with SemMedDB-derived Lucene index (see LuceneIndexFromSemrepTriples)
  
  private ESP(FlagConfig flagConfig) {
	 };

  /**
   * Creates ESP vectors incrementally, using the fields "subject" and "object" from a Lucene index.
   */
  public static void createIncrementalESPVectors(FlagConfig flagConfig) throws IOException {
    ESP incrementalESPVectors = new ESP(flagConfig);
    random = new Random();
    incrementalESPVectors.flagConfig = flagConfig;
    incrementalESPVectors.initialize();

    VectorStoreWriter.writeVectors(
        flagConfig.elementalpredicatevectorfile(), flagConfig, incrementalESPVectors.elementalPredicateVectors);

    VerbatimLogger.info("Performing first round of ESP training ...");
    incrementalESPVectors.trainIncrementalESPVectors();

    VerbatimLogger.info("Done with createIncrementalESPVectors.");
  }
  
  
  

  /**
   * Creates elemental and semantic vectors for each concept, and elemental vectors for predicates.
   *
   * @throws IOException
   */
  private void initialize() throws IOException {
    if (this.luceneUtils == null) {
      this.luceneUtils = new LuceneUtils(flagConfig);
    }

    
    elementalItemVectors = new ElementalVectorStore(flagConfig);
    semanticItemVectors = new VectorStoreRAM(flagConfig);
    
    //to accommodate pre-training
    if (!flagConfig.initialtermvectors().isEmpty())
    {
    	String[] initialVectorFiles = flagConfig.initialtermvectors().split(",");
    	if (initialVectorFiles.length == 2)
    	{
    		elementalItemVectors = new VectorStoreRAM(flagConfig);
    		((VectorStoreRAM) elementalItemVectors).initFromFile(initialVectorFiles[0]);
    		((VectorStoreRAM) semanticItemVectors).initFromFile(initialVectorFiles[1]);
    		
    	
     VerbatimLogger.info("Initialized elemental vectors from "+initialVectorFiles[0]+"\n");
     VerbatimLogger.info("Initialized semantic vectors from "+initialVectorFiles[1]+"\n");
    	}
    }
    
    elementalPredicateVectors = new ElementalVectorStore(flagConfig);
     flagConfig.setContentsfields(itemFields);

    termDic 	= new ConcurrentHashMap<String, ConcurrentSkipListMap<Double, String>>();
    totalPool	= new ConcurrentHashMap<String, Double>();
    
    addedConcepts = new ArrayList<String>();
    
    // Term counter to track initialization progress.
    int termCounter = 0;
    for (String fieldName : itemFields) {
      Terms terms = luceneUtils.getTermsForField(fieldName);

      if (terms == null) {
        throw new NullPointerException(String.format(
            "No terms for field '%s'. Please check that index at '%s' was built correctly for use with ESP.",
            fieldName, flagConfig.luceneindexpath()));
      }

      TermsEnum termsEnum = terms.iterator(null);
      BytesRef bytes;
      while((bytes = termsEnum.next()) != null) {
        Term term = new Term(fieldName, bytes);

        if (!luceneUtils.termFilter(term)) {
          VerbatimLogger.fine("Filtering out term: " + term + "\n");
          continue;
        }

        if (!addedConcepts.contains(term.text())) {
          addedConcepts.add(term.text());
          
          if (!elementalItemVectors.containsVector(term.text()))
          {
        	  if (flagConfig.initialtermvectors().isEmpty())   // Can I put in a check for whether the term is in the right year?
        		  elementalItemVectors.getVector(term.text());  // Causes vector to be created.
        	  else //pretraining
        	  {
        		  if (flagConfig.elementalmethod().equals(ElementalGenerationMethod.CONTENTHASH))
        			  random.setSeed(Bobcat.asLong(term.text())); //deterministic initialization
                  ((VectorStoreRAM) elementalItemVectors).putVector(term.text(), VectorFactory.generateRandomVector(
                              flagConfig.vectortype(), flagConfig.dimension(), flagConfig.seedlength(), random));    
                	  
        	  }
          
          }
          //store the semantic type
          DocsEnum docEnum 	= luceneUtils.getDocsForTerm(term);
          docEnum.nextDoc();
          Document theDoc 	= luceneUtils.getDoc(docEnum.docID());
          String semtype = "";
          
          if (semtypesAndCUIs)
          {
          if (term.field().equals(SUBJECT_FIELD)) semtype = theDoc.get("subject_semtype");
          else if (term.field().equals(OBJECT_FIELD)) semtype = theDoc.get("object_semtype");
          semtypes.put(term.text(), semtype);
           
          String cui = "";
          if (term.field().equals(SUBJECT_FIELD)) cui = theDoc.get("subject_CUI");
          else if (term.field().equals(OBJECT_FIELD)) cui = theDoc.get("object_CUI");
          cuis.put(term.text(), cui);

          if (!semanticItemVectors.containsVector(term.text()))
          semanticItemVectors.putVector(term.text(), VectorFactory.generateRandomVector(
                      flagConfig.vectortype(), flagConfig.dimension(), flagConfig.seedlength(), random));    
          
          }
          else
          {
        	  if (!semanticItemVectors.containsVector(term.text()))
        	  semanticItemVectors.putVector(term.text(), VectorFactory.generateRandomVector(
                      flagConfig.vectortype(), flagConfig.dimension(), flagConfig.seedlength(), random));
        	  
        	  semtype = "universal"; //ignore semantic types
          }
        	
          
          //table for negative sampling, stratified by semantic type (if available)
          if (! termDic.containsKey(semtype))
          {
        	  	totalPool.put(semtype,0d);
        	  	termDic.put(semtype, new ConcurrentSkipListMap<Double, String>());
          }
         
          //determine frequency with which a concept is drawn as a negative sample
          //following the word2vec work, we use unigram^.75
          //totalPool is a ConcurrentHashMap - it defines a range for each concept (for each semantic type, if these are used)
          //the relative size of the range determines the negative sampling frequency for each concept
          totalPool.put(semtype, totalPool.get(semtype)+Math.pow(luceneUtils.getGlobalTermFreq(term), .75));
          termDic.get(semtype).put(totalPool.get(semtype), term.text());
     
          // Output term counter.
          termCounter++;
          if ((termCounter > 0) && ((termCounter % 10000 == 0) || ( termCounter < 10000 && termCounter % 1000 == 0 ))) {
            VerbatimLogger.info("Initialized " + termCounter + " term vectors ... ");
          }
        }
      }
    }

    // Now elemental vectors for the predicate field.
    Terms predicateTerms = luceneUtils.getTermsForField(PREDICATE_FIELD);
    String[] dummyArray = new String[] { PREDICATE_FIELD };  // To satisfy LuceneUtils.termFilter interface.
    TermsEnum termsEnum = predicateTerms.iterator(null);
    BytesRef bytes;
    while((bytes = termsEnum.next()) != null) {
      Term term = new Term(PREDICATE_FIELD, bytes);
      // frequency thresholds do not apply to predicates... but the stopword list does
      if (!luceneUtils.termFilter(term, dummyArray, 0, Integer.MAX_VALUE, Integer.MAX_VALUE, 1)) {
        continue;
      }

      elementalPredicateVectors.getVector(term.text().trim());
   
      // Add inverse vector for the predicates.
      elementalPredicateVectors.getVector(term.text().trim() + "-INV");
      

    }
    
    //precalculate probabilities for subsampling (need to iterate again once total term frequency known)
    if (flagConfig.samplingthreshold() > -1 && flagConfig.samplingthreshold() < 1) {
      subsamplingProbabilities = new ConcurrentHashMap<String, Double>();

      double totalConceptCount = luceneUtils.getNumDocs()*2; //there are two concepts per predication
      VerbatimLogger.info("Populating subsampling probabilities - total concept count = " + totalConceptCount + " which is " + (totalConceptCount / luceneUtils.getNumDocs()) + " per doc on average");
      int count = 0;
      
      Set<Object> uniqueRepresentedTerms = cuis.keySet();
      Iterator<Object> termIterator = uniqueRepresentedTerms.iterator();
        Object termName;
        while (termIterator.hasNext() && (termName = termIterator.next()) != null) {
           if (++count % 10000 == 0) VerbatimLogger.info(".");

          // Skip terms that don't pass the filter.
          if (!semanticItemVectors.containsVector(termName.toString())) continue;
          double subFreq  	= luceneUtils.getGlobalDocFreq(new Term("subject",termName.toString()));
          double obFreq  	= luceneUtils.getGlobalDocFreq(new Term("object",termName.toString()));
          double globalFreq = (subFreq + obFreq) / (double) totalConceptCount;

          if (globalFreq > flagConfig.samplingthreshold()) {
            double discount = 1; //(globalFreq - flagConfig.samplingthreshold()) / globalFreq;
            subsamplingProbabilities.put(termName.toString(), (discount - Math.sqrt(flagConfig.samplingthreshold() / globalFreq)));
            //VerbatimLogger.info(globalFreq+" "+term.text()+" "+subsamplingProbabilities.get(fieldName+":"+bytes.utf8ToString()));
          }
        }  //all terms for one field

      VerbatimLogger.info("\n");
      if (subsamplingProbabilities !=null && subsamplingProbabilities.size() > 0)
        VerbatimLogger.info("Selected for subsampling: " + subsamplingProbabilities.size() + " terms.\n");
    } 
    
  }

  /**
   * Each TrainPredThred draws from the predication queue, and sends the predication for processing. 
   * TrainPredThreads operate in parallel
   * @author tcohen
   *
   */
  private class TrainPredThread implements Runnable {
	     BLAS blas = null;

	    public TrainPredThread(int threadno) {
	       this.blas = BLAS.getInstance();
	    }

	    @Override
	    public void run() {

	    	
      Document document = null;
      int complete = 0;
      
      while (! (complete == -1 && theQ.isEmpty())) 
      {
    	  document = theQ.poll();
    	  if (document == null)
    	  	{
    		  complete = populateQueue();
    		  document = theQ.poll();
    	  	}
    	  if (document != null)
    		  processPredicationDocument(document, blas);
    	  
	  }
	    }
  }
  
 /**
  * Look up the sigmoid function of input "z" in a pregenerated lookup table
  * @param z
  * @return
  */
  
  
public double sigmoid(double z)
{
	return sigmoidTable.sigmoid(z);
	
  }

/**
 * Returns zero minus the extent to which a negative-sample/predicate product is related to an index concept
 * This scalar is applied to the negative sample vector upon superposition,
 * drawing the system state closer to a state in which the similarity between these vectors is minimal 
 * @param v1
 * @param v2
 * @param flagConfig
 * @param blas
 * @return
 */
  
public double shiftAway(Vector v1, Vector v2, FlagConfig flagConfig, BLAS blas)
{
		if (!flagConfig.vectortype().equals(VectorType.BINARY))
		 return -sigmoid(VectorUtils.scalarProduct(v1, v2, flagConfig, blas));
		 else
  		 return -100*Math.max(VectorUtils.scalarProduct(v1, v2, flagConfig, blas),0);
}

/**
 * Returns 1 minus the extent to which a positive-sample/predicate product is related to an index concept
 * This scalar is applied to the negative sample vector upon superposition,
 * drawing the system state closer to a state in which the similarity between these vectors is maximal 
 * @param v1
 * @param v2
 * @param flagConfig
 * @param blas
 * @return
 */

public double shiftToward(Vector v1, Vector v2, FlagConfig flagConfig, BLAS blas)
{
		if (!flagConfig.vectortype().equals(VectorType.BINARY))
		 return 1-sigmoid(VectorUtils.scalarProduct(v1, v2, flagConfig, blas));
		 else
  		 return 100-100*Math.max(VectorUtils.scalarProduct(v1, v2, flagConfig, blas),0);
}

/**
 * Encode a single predication
 * @param subject
 * @param predicate
 * @param object
 * @param subsem	subject semantic type
 * @param obsem		object semantic type
 * @param blas
 */


private void processPredication(String subject, String predicate, String object, String subsem, String obsem, BLAS blas)
{
	  Vector subjectSemanticVector 			= semanticItemVectors.getVector(subject);
      Vector copyOfSubjectSemanticVector 	= semanticItemVectors.getVector(subject).copy();
      
      Vector objectElementalVector 			= elementalItemVectors.getVector(object);
      
      Vector elementalBoundProduct 			= elementalPredicateVectors.getVector(predicate).copy();
      Vector predicateElementalVector 		= elementalPredicateVectors.getVector(predicate);
      
      
      
      if (!semtypesAndCUIs) //if UMLS semantic types not available
      {
		subsem = "universal";
		obsem  = "universal";
      }

      ArrayList<Vector> objNegSamples = new ArrayList<Vector>();
      HashSet<String>  duplicates = new HashSet<String>();
      
      
      //get flagConfig.negsamples() negative samples as counterpoint to E(object)
      while (objNegSamples.size() <= flagConfig.negsamples())
      {
    	  Vector objectsNegativeSample 	= null;
    	  int ocnt=0;
    	     
      //draw negative samples, using a unigram distribution for now
      while (objectsNegativeSample == null)
      	{   
    	  double test = random.nextDouble()*totalPool.get(obsem);
              if (termDic.get(obsem).ceilingEntry(test) != null) {
            
            	  String testConcept = termDic.get(obsem).ceilingEntry(test).getValue();
            	 
            	  if  (++ocnt > 10 && semtypesAndCUIs) //probably a rare semantic type
            	  {
            		  test = random.nextDouble()*totalPool.get("dsyn");
            		  testConcept 	= termDic.get("dsyn").ceilingEntry(test).getValue();
            	  }
            	  
            	  if (duplicates.contains(testConcept)) continue;
              	  duplicates.add(testConcept);  
    	  if (!testConcept.equals(object)) // don't use the observed object as a negative sample
    			  objectsNegativeSample =  elementalItemVectors.getVector(testConcept);
    			  
    	}
      	}
      
      objNegSamples.add(objectsNegativeSample);
      }
  
	     //alter subject's semantic vector
      elementalBoundProduct.bind(objectElementalVector);          //eg. E(TREATS)*E(schizophrenia)
      copyOfSubjectSemanticVector.release(predicateElementalVector);  //e.g. S(haloperidol)/E(TREATS) ?= E(schizophrenia)
      
     //observed predication
     double shiftToward = shiftToward(subjectSemanticVector,elementalBoundProduct,flagConfig, blas);
     subjectSemanticVector.superpose(elementalBoundProduct, alpha*shiftToward, null); //sim (S(haloperidol), E(TREATS)*E(schizophrenia) \approx sim(S(haloperidol)/E(TREATS), E(schizophrenia))
	
     shiftToward = shiftToward(copyOfSubjectSemanticVector,objectElementalVector,flagConfig, blas);
     objectElementalVector.superpose(copyOfSubjectSemanticVector, alpha*shiftToward, null); 
      
	 
     //negative samples
     for (Vector objNegativeSample:objNegSamples)
     {
    	 Vector negativeElementalBoundProduct 	= elementalPredicateVectors.getVector(predicate).copy();
		   		negativeElementalBoundProduct.bind(objNegativeSample);  //eg. E(TREATS)*E(diabetes)
		   		
		 double shiftAway   = shiftAway(subjectSemanticVector, negativeElementalBoundProduct,flagConfig, blas);
		 subjectSemanticVector.superpose(negativeElementalBoundProduct, alpha*shiftAway, null);
		
		 shiftAway   = shiftAway(copyOfSubjectSemanticVector, objNegativeSample,flagConfig, blas);
		 objNegativeSample.superpose(copyOfSubjectSemanticVector, alpha*shiftAway, null);
	     
     }
	 
      }
      

  
/**
 * Process an individual predication (each Document object contains one predication)
 * in both directions (i.e. a PRED b; b PRED-INV a)
 * @param document
 **/

private void processPredicationDocument(Document document, BLAS blas)
{
	   	  String subject 		= document.get(SUBJECT_FIELD);
	      String predicate 		= document.get(PREDICATE_FIELD);
	      String object 		= document.get(OBJECT_FIELD);
	      String predication   =  subject+predicate+object;
	      String subsem 		= document.get("subject_semtype");
	      String obsem			= document.get("object_semtype");

	      boolean encode 	 = true;

	      if (!(elementalItemVectors.containsVector(object)
	          && elementalItemVectors.containsVector(subject)
	          && elementalPredicateVectors.containsVector(predicate))) {
	        logger.fine("skipping predication " + subject + " " + predicate + " " + object);
	        encode = false;
	      }
	      
	      //subsampling of predications
	      int    predCount	= luceneUtils.getGlobalTermFreq(new Term(PREDICATION_FIELD,predication));
	         
	      double predFreq   =  (predCount / (double) luceneUtils.getNumDocs());
	      if (predFreq > flagConfig.samplingthreshold()*0.01)
	    	  if (random.nextDouble() <= ( 1 - Math.sqrt(flagConfig.samplingthreshold()*0.01) / predFreq))
	    	  	encode = false;
	         
	      //subsampling of terms above some threshold
	      if (this.subsamplingProbabilities != null && this.subsamplingProbabilities.contains(subject) && random.nextDouble() <= this.subsamplingProbabilities.get(subject))
	       { encode = false;
	    	 logger.fine("skipping predication " + object + " " + predicate + "-INV " + subject);
		    }  
	       if (this.subsamplingProbabilities != null && this.subsamplingProbabilities.contains(object) && random.nextDouble() <= this.subsamplingProbabilities.get(object))
	       { encode = false;
	    	 logger.fine("skipping predication " + subject + " " + predicate + " " + object);
		    }
	       

	      if (encode)
	      {
	    	  this.processPredication(subject, predicate, object, subsem, obsem, blas);
	    	  this.processPredication(object, predicate+"-INV", subject, obsem, subsem, blas);
	    	  pc.incrementAndGet();
	      }
	      
	      
	   	  if (pc.get() > 0 && pc.get() % 10000 == 0) {
            VerbatimLogger.info("Processed " + pc + " predications ... ");
            double progress 	= (tc*luceneUtils.getNumDocs() + dc.get()) / ((double) luceneUtils.getNumDocs()*(flagConfig.trainingcycles() +1) );
            VerbatimLogger.info((100*progress)+"% complete ...");
            alpha = Math.max(initial_alpha  - (initial_alpha-min_alpha)*progress, min_alpha);
            VerbatimLogger.info("\nUpdated alpha to "+alpha+"..");
          }
	      
}

/**
 * Points in total document collection to draw queue from (for randomization without excessive seek time)
 */

private void initializeRandomizationStartpoints()
{
	this.randomStartpoints = new ConcurrentLinkedQueue<Integer>();
	int increments 		   = luceneUtils.getNumDocs() / 100000;
	boolean remainder 	   = luceneUtils.getNumDocs() % 100000 > 0;
	
	if (remainder) increments++;
	
	ArrayList<Integer> toRandomize = new ArrayList<Integer>();
	
	for (int x = 0; x < increments; x++)
		toRandomize.add(x * 100000);

	Collections.shuffle(toRandomize);
	
	randomStartpoints.addAll(toRandomize);
	
}


  
  /**
   * 
   * Populate the queue of predication-documents
   * 100,000 (or fewer if fewer remain) predication-documents are drawn, beginning 
   * at a random start point in the Lucene index
   * 
   * These random start points are retained in a separate queue and shuffled upon each epoch.
   * So the chunks of 100,000 predications are presented in different order across epochs.
   * 
   * @return the number of documents added to the queue
   */
  
  private synchronized int populateQueue()
  {
	 	  
	 if (dc.get() >= luceneUtils.getNumDocs()) return -1; 
	 if (theQ.size() > 100000) return 0;
	 if (randomStartpoints.isEmpty()) return -1;
	 
	
 	 int qb = randomStartpoints.poll(); //the index number of the first predication-document to be drawn
 	 int qe = qb + (100000); //the index number of the last predication-document to be drawn
 	 int qplus = 0; //the number of predication-documents added to the queue
     
     for (int qc=qb; qc < qe && qc < luceneUtils.getNumDocs() && dc.get() < luceneUtils.getNumDocs()-1; qc++)
		{
    	 try {   
    		 	Document nextDoc = luceneUtils.getDoc(qc);
    		 	dc.incrementAndGet();
    		 	//Have to parse to short because it was saved as a text field
          short pubyear = Short.parseShort(nextDoc.get("pubyear"));  
          String subject    = nextDoc.get(SUBJECT_FIELD);
          String predicate    = nextDoc.get(PREDICATE_FIELD);
          String object     = nextDoc.get(OBJECT_FIELD);

          //if timerange, skip encoding predications outside of scope
          if (!flagConfig.timerange().isEmpty()) {
            short yearstart = Short.parseShort(flagConfig.timerange().split(",")[0]);
            short yearstop = Short.parseShort(flagConfig.timerange().split(",")[1]);
            if(!(yearstart <= pubyear && pubyear <= yearstop)) {
              logger.fine("Skipping predication: " + subject + " " + predicate + " " + object + ".\t| Not in date range ("+pubyear+").\n");
              continue;
            }
          }

    		 	if (nextDoc != null)
    		 	{
    		 		theQ.add(nextDoc);
    		 		qplus++;
    		 	}

    	 	 } catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		}
  	    
	    VerbatimLogger.info("Added "+qplus+" documents to queue, now carrying "+theQ.size());
		if (qplus == 0) return -1;
		else return qplus;
   	
	  
  }
  
  
  /**
   * Performs training by iterating over predications. Assumes that elemental vector stores are populated.
   *
   * @throws IOException
   */
  private void trainIncrementalESPVectors() throws IOException {
	
	  // For BINARY vectors, assign higher learning rates on account of the limited floating point precision of the voting record  
    if (flagConfig.vectortype().equals(VectorType.BINARY)) 	{
    	  initial_alpha = 0.25;
    	  alpha = 0.25;
    	  min_alpha = 0.001;
    	 }
    
    //loop through the number of assigned epochs
    for (tc=0; tc <= flagConfig.trainingcycles(); tc++)
    {
    	initializeRandomizationStartpoints(); 
    	theQ = new ConcurrentLinkedQueue<Document>();
    	dc.set(0);
    	populateQueue();

    	double time = System.currentTimeMillis();
    	
 
      int numthreads = flagConfig.numthreads();
      ExecutorService executor = Executors.newFixedThreadPool(numthreads);

      for (int q = 0; q < numthreads; q++) {
        executor.execute(new TrainPredThread(q));
      }

      executor.shutdown();
      
      
      // Wait until all threads are finish
      while (!executor.isTerminated()) {
    	  
    	  if (theQ.size() < 50000) 
    		  populateQueue();
    	
      }
      
      VerbatimLogger.info("Time for cycle "+tc+" : "+((System.currentTimeMillis() - time) / (1000*60))  +" minutes");
      VerbatimLogger.info("Processed "+pc.get()+" total predications (total on disk = "+luceneUtils.getNumDocs()+")");
      
      //normalization with each epoch if the vectors are not binary vectors
      if (!flagConfig.vectortype().equals(VectorType.BINARY))
      {
    	  
    	  Enumeration<ObjectVector> semanticVectorEnumeration 	= semanticItemVectors.getAllVectors();
    	  Enumeration<ObjectVector> elementalVectorEnumeration 	= elementalItemVectors.getAllVectors();
          
      	
      while (semanticVectorEnumeration.hasMoreElements())	{
       
    	  semanticVectorEnumeration.nextElement().getVector().normalize();
      	  elementalVectorEnumeration.nextElement().getVector().normalize();
      }  
      }
      
    } // Finished all epochs 

      
      Enumeration<ObjectVector> e = null;
      
     if (semtypesAndCUIs) //write out cui vectors and normalize semantic vectors
     {
    // Also write out cui version of semantic vectors
     File vectorFile = new File("cuivectors.bin");
	    String parentPath = vectorFile.getParent();
	    if (parentPath == null) parentPath = "";
	    FSDirectory fsDirectory = FSDirectory.open(FileSystems.getDefault().getPath(parentPath));
	    IndexOutput outputStream = fsDirectory.createOutput(vectorFile.getName(), IOContext.DEFAULT);
	    outputStream.writeString(VectorStoreWriter.generateHeaderString(flagConfig));
     
    // Tally votes of semantic vectors and write out.
    e = semanticItemVectors.getAllVectors();
    while (e.hasMoreElements())	{
      ObjectVector ov = e.nextElement();
      if (flagConfig.vectortype().equals(VectorType.BINARY))
    	  ((BinaryVector) ov.getVector()).tallyVotes();
      else 
    	  ov.getVector().normalize();
      
      if (cuis.containsKey(ov.getObject()))
      {
	  outputStream.writeString(cuis.get(ov.getObject()));
	  ov.getVector().writeToLuceneStream(outputStream);
      }
      }

    outputStream.close();
     
    } else //just tally votes of semantic vectors
    {
        e = semanticItemVectors.getAllVectors();
        while (e.hasMoreElements())	{
          ObjectVector ov = e.nextElement();
          if (flagConfig.vectortype().equals(VectorType.BINARY))
        	  ((BinaryVector) ov.getVector()).tallyVotes();
          else 
        	  ov.getVector().normalize();
    	
    }
    }
    	

    VectorStore dv = new VectorStoreDeterministic(flagConfig);

    e = elementalItemVectors.getAllVectors();
    while (e.hasMoreElements()) {
    	
      ObjectVector ov = e.nextElement();

    	Vector nextVec =  ov.getVector();
         if (nextVec.measureOverlap(dv.getVector(ov.getObject())) == 1) {
            ((VectorStoreRAM) elementalItemVectors).removeVector(ov);
         }
    	   if (flagConfig.vectortype().equals(VectorType.BINARY))
    	    	  ((BinaryVector) nextVec).tallyVotes();
    	   else 
    		nextVec.normalize();
    }

    VectorStoreWriter.writeVectors(
        flagConfig.semanticvectorfile(), flagConfig, semanticItemVectors);
    
    VectorStoreWriter.writeVectors(
         flagConfig.elementalvectorfile(), flagConfig, elementalItemVectors);
    
    
      
 
    VerbatimLogger.info("Finished writing semantic item and context vectors.\n");

    }

  /**
   * Main method for building ESP indexes.
   */
  public static void main(String[] args) throws IllegalArgumentException, IOException {
    FlagConfig flagConfig = FlagConfig.getFlagConfig(args);
    args = flagConfig.remainingArgs;

    if (flagConfig.luceneindexpath().isEmpty()) {
      throw (new IllegalArgumentException("-luceneindexpath argument must be provided."));
    }

    VerbatimLogger.info("Building ESP model from index in: " + flagConfig.luceneindexpath() + "\n");
    VerbatimLogger.info("Minimum frequency = " + flagConfig.minfrequency() + "\n");
    VerbatimLogger.info("Maximum frequency = " + flagConfig.maxfrequency() + "\n");
    VerbatimLogger.info("Number non-alphabet characters = " + flagConfig.maxnonalphabetchars() + "\n");

    createIncrementalESPVectors(flagConfig);
  }
}
