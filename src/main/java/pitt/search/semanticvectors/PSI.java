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
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;

import pitt.search.semanticvectors.LuceneUtils.TermWeight;
import pitt.search.semanticvectors.utils.VerbatimLogger;
import pitt.search.semanticvectors.vectors.PermutationUtils;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.logging.Logger;

/**
 * Generates predication vectors incrementally.Requires as input an index containing 
 * documents with the fields "subject", "predicate" and "object"
 *
 * Produces as output the files: elementalvectors.bin, predicatevectors.bin and semanticvectors.bin
 *
 * @author Trevor Cohen, Dominic Widdows
 */
public class PSI {
  private static final Logger logger = Logger.getLogger(PSI.class.getCanonicalName());
  private FlagConfig flagConfig;
  private VectorStore elementalItemVectors, elementalPredicateVectors;
  private VectorStoreRAM semanticItemVectors, semanticPredicateVectors;
  private static final String SUBJECT_FIELD = "subject";
  private static final String PREDICATE_FIELD = "predicate";
  private static final String OBJECT_FIELD = "object";
  private static final String PREDICATION_FIELD = "predication";
  private String[] itemFields = {SUBJECT_FIELD, OBJECT_FIELD};
  private LuceneUtils luceneUtils;
  private int[] predicatePermutation;

  private PSI(FlagConfig flagConfig) {
	  predicatePermutation = PermutationUtils.getShiftPermutation(flagConfig.vectortype(), flagConfig.dimension(), 1);
 };

  /**
   * Creates PSI vectors incrementally, using the fields "subject" and "object" from a Lucene index.
   */
  public static void createIncrementalPSIVectors(FlagConfig flagConfig) throws IOException {
    PSI incrementalPSIVectors = new PSI(flagConfig);
    incrementalPSIVectors.flagConfig = flagConfig;
    incrementalPSIVectors.initialize();

    VectorStoreWriter.writeVectors(
        flagConfig.elementalvectorfile(), flagConfig, incrementalPSIVectors.elementalItemVectors);
    VectorStoreWriter.writeVectors(
        flagConfig.elementalpredicatevectorfile(), flagConfig, incrementalPSIVectors.elementalPredicateVectors);

    VerbatimLogger.info("Performing first round of PSI training ...");
    incrementalPSIVectors.trainIncrementalPSIVectors("");

    if (flagConfig.trainingcycles() > 0)
    {	
    VerbatimLogger.info("Performing next round of PSI training ...");
    incrementalPSIVectors.elementalItemVectors = incrementalPSIVectors.semanticItemVectors;
    incrementalPSIVectors.elementalPredicateVectors = incrementalPSIVectors.semanticPredicateVectors;
    incrementalPSIVectors.trainIncrementalPSIVectors("1");
    }
    VerbatimLogger.info("Done with createIncrementalPSIVectors.");
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
    elementalPredicateVectors = new ElementalVectorStore(flagConfig);
    semanticPredicateVectors = new VectorStoreRAM(flagConfig);
    flagConfig.setContentsfields(itemFields);

    HashSet<String> addedConcepts = new HashSet<String>();

    // Term counter to track initialization progress.
    int tc = 0;
    for (String fieldName : itemFields) {
      ArrayList<String> terms = luceneUtils.getTermsForField(fieldName);

      if (terms == null) {
        throw new NullPointerException(String.format(
            "No terms for field '%s'. Please check that index at '%s' was built correctly for use with PSI.",
            fieldName, flagConfig.luceneindexpath()));
      }

      for (String bytes:terms) {
        Term term = new Term(fieldName, bytes);

        if (!luceneUtils.termFilter(term)) {
          VerbatimLogger.fine("Filtering out term: " + term + "\n");
          continue;
        }

        if (!addedConcepts.contains(term.text())) {
          addedConcepts.add(term.text());
          elementalItemVectors.getVector(term.text());  // Causes vector to be created.
          semanticItemVectors.putVector(term.text(), VectorFactory.createZeroVector(
              flagConfig.vectortype(), flagConfig.dimension()));

          // Output term counter.
          tc++;
          if ((tc > 0) && ((tc % 10000 == 0) || ( tc < 10000 && tc % 1000 == 0 ))) {
            VerbatimLogger.info("Initialized " + tc + " term vectors ... ");
          }
        }
      }
    }

    // Now elemental vectors for the predicate field.
    ArrayList<String> predicateTerms = luceneUtils.getTermsForField(PREDICATE_FIELD);
    String[] dummyArray = new String[] { PREDICATE_FIELD };  // To satisfy LuceneUtils.termFilter interface.
    for(String bytes:predicateTerms) {
      Term term = new Term(PREDICATE_FIELD, bytes);
      // frequency thresholds do not apply to predicates... but the stopword list does
      if (!luceneUtils.termFilter(term, dummyArray, 0, Integer.MAX_VALUE, Integer.MAX_VALUE, 1)) {
        continue;
      }

      elementalPredicateVectors.getVector(term.text().trim());
      
      if (flagConfig.trainingcycles() > 0)
    	  semanticPredicateVectors.putVector(term.text().trim(), VectorFactory.createZeroVector(
          flagConfig.vectortype(), flagConfig.dimension()));

      // Add inverse vector for the predicates.
      elementalPredicateVectors.getVector(term.text().trim() + "-INV");
      
      if (flagConfig.trainingcycles() > 0)
    	  semanticPredicateVectors.putVector(term.text().trim() + "-INV", VectorFactory.createZeroVector(
          flagConfig.vectortype(), flagConfig.dimension()));
    }
  }

  /**
   * Performs training by iterating over predications. Assumes that elemental vector stores are populated.
   *
   * @throws IOException
   */
  private void trainIncrementalPSIVectors(String iterationTag) throws IOException {
    String fieldName = PREDICATION_FIELD;
    // Iterate through documents (each document = one predication).
    ArrayList<String> allTerms = luceneUtils.getTermsForField(fieldName);
    int pc = 0;
    for(String bytes : allTerms) {
      Term term = new Term(fieldName, bytes);

      // Output progress counter.
      pc++;
      if ((pc > 0) && ((pc % 10000 == 0) || ( pc < 10000 && pc % 1000 == 0 ))) {
        VerbatimLogger.info("Processed " + pc + " unique predications ... ");
      }

      ArrayList<PostingsEnum> allTermDocs = luceneUtils.getDocsForTerm(term);
      for (PostingsEnum termDocs:allTermDocs)
      {
      termDocs.nextDoc();
      Document document = luceneUtils.getDoc(termDocs.docID());

      String subject = document.get(SUBJECT_FIELD);
      String predicate = document.get(PREDICATE_FIELD);
      String object = document.get(OBJECT_FIELD);

      if (!(elementalItemVectors.containsVector(object)
          && elementalItemVectors.containsVector(subject)
          && elementalPredicateVectors.containsVector(predicate))) {
        logger.fine("skipping predication " + subject + " " + predicate + " " + object);
        continue;
      }

      float sWeight = 1;
      float oWeight = 1;
      float pWeight = 1;
      float predWeight = 1;

      // sWeight and oWeight are analogous to global weighting, a function of the number of times these concepts - and predicates - occur
      // such that less frequent concepts and predicates will contribute more 
      predWeight 	= luceneUtils.getGlobalTermWeight(new Term(PREDICATE_FIELD, predicate));
      sWeight 		= luceneUtils.getGlobalTermWeight(new Term(SUBJECT_FIELD, subject));
      oWeight 		= luceneUtils.getGlobalTermWeight(new Term(OBJECT_FIELD, object));
      // pWeight is analogous to local weighting, a function of the total number of times a predication occurs 
      // examples are -termweight sqrt (sqrt of total occurences), and -termweight logentropy (log of 1 + occurrences)
      pWeight = luceneUtils.getLocalTermWeight(luceneUtils.getGlobalTermFreq(term));

      // with -termweight sqrt we don't take global weighting of predicates into account to preserve a probabilistic interpretation
      if (flagConfig.termweight().equals(TermWeight.SQRT)) predWeight = 0; 
      
      Vector subjectSemanticVector = semanticItemVectors.getVector(subject);
      Vector objectSemanticVector = semanticItemVectors.getVector(object);
      Vector subjectElementalVector = elementalItemVectors.getVector(subject);
      Vector objectElementalVector = elementalItemVectors.getVector(object);
      Vector predicateElementalVector = elementalPredicateVectors.getVector(predicate);
      Vector predicateElementalVectorInv = elementalPredicateVectors.getVector(predicate + "-INV");

      Vector objToAdd = objectElementalVector.copy();
      objToAdd.bind(predicateElementalVector);
      subjectSemanticVector.superpose(objToAdd, pWeight * (oWeight + predWeight), null);

      Vector subjToAdd = subjectElementalVector.copy();
      subjToAdd.bind(predicateElementalVectorInv);
      objectSemanticVector.superpose(subjToAdd, pWeight * (sWeight + predWeight), null);

      if (flagConfig.trainingcycles() > 0) //for experiments with generating iterative predicate vectors
      {
    	  
       	  Vector predicateSemanticVector = semanticPredicateVectors.getVector(predicate);
    		  Vector predicateSemanticVectorInv = semanticPredicateVectors.getVector(predicate+ "-INV");
          //construct permuted editions of subject and object vectors (so binding doesn't commute)
          Vector permutedSubjectElementalVector = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());
          Vector permutedObjectElementalVector = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());
          permutedSubjectElementalVector.superpose(subjectElementalVector, 1, predicatePermutation); 
          permutedObjectElementalVector.superpose(objectElementalVector, 1, predicatePermutation); 
          permutedSubjectElementalVector.normalize();
          permutedObjectElementalVector.normalize();  
    	  
      Vector predToAdd = subjectElementalVector.copy();
      predToAdd.bind(permutedObjectElementalVector);
      predicateSemanticVector.superpose(predToAdd, sWeight * oWeight, null);

      Vector predToAddInv = objectElementalVector.copy();
      predToAddInv.bind(permutedSubjectElementalVector);
      predicateSemanticVectorInv.superpose(predToAddInv, oWeight * sWeight, null);
      }
      } // Finish iterating through predications in one PostingsEnum  
    } // Finish iterating through predications.

    // Normalize semantic vectors and write out.
    Enumeration<ObjectVector> e = semanticItemVectors.getAllVectors();
    while (e.hasMoreElements())	{
      e.nextElement().getVector().normalize();
    }

    e = semanticPredicateVectors.getAllVectors();
    while (e.hasMoreElements()) {
      e.nextElement().getVector().normalize();
    }

    VectorStoreWriter.writeVectors(
        flagConfig.semanticvectorfile() + iterationTag, flagConfig, semanticItemVectors);
   
    if (flagConfig.trainingcycles() > 0)
    {	
    VectorStoreWriter.writeVectors(
        flagConfig.semanticpredicatevectorfile() + iterationTag, flagConfig, semanticPredicateVectors);
    }
    VerbatimLogger.info("Finished writing this round of semantic item and predicate vectors.\n");
    
    }

  /**
   * Main method for building PSI indexes.
   */
  public static void main(String[] args) throws IllegalArgumentException, IOException {
    FlagConfig flagConfig = FlagConfig.getFlagConfig(args);
    args = flagConfig.remainingArgs;

    if (flagConfig.luceneindexpath().isEmpty()) {
      throw (new IllegalArgumentException("-luceneindexpath argument must be provided."));
    }

    VerbatimLogger.info("Building PSI model from index in: " + flagConfig.luceneindexpath() + "\n");
    VerbatimLogger.info("Minimum frequency = " + flagConfig.minfrequency() + "\n");
    VerbatimLogger.info("Maximum frequency = " + flagConfig.maxfrequency() + "\n");
    VerbatimLogger.info("Number non-alphabet characters = " + flagConfig.maxnonalphabetchars() + "\n");

    createIncrementalPSIVectors(flagConfig);
  }
}
