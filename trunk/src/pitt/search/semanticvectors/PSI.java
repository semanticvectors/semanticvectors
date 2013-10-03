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

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Random;
import java.util.logging.Logger;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.util.BytesRef;

import pitt.search.semanticvectors.utils.Bobcat;
import pitt.search.semanticvectors.utils.VerbatimLogger;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorType;

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
  private VectorStoreRAM elementalVectors, semanticVectors, predicateVectors;
  private static final String SUBJECT_FIELD = "subject";
  private static final String PREDICATE_FIELD = "predicate";
  private static final String OBJECT_FIELD = "object";
  private static final String PREDICATION_FIELD = "predication";
  private String[] itemFields = {SUBJECT_FIELD, OBJECT_FIELD};
  private LuceneUtils luceneUtils;

  private PSI() {};

  /**
   * Creates PSI vectors incrementally, using the fields "subject" and "object" from a Lucene index.
   */
  public static void createIncrementalPSIVectors(FlagConfig flagConfig) throws IOException {
    PSI incrementalPSIVectors = new PSI();
    incrementalPSIVectors.flagConfig = flagConfig;
    if (incrementalPSIVectors.luceneUtils == null) {
      incrementalPSIVectors.luceneUtils = new LuceneUtils(flagConfig);
    }
    incrementalPSIVectors.trainIncrementalPSIVectors();
  }

  private void trainIncrementalPSIVectors() throws IOException {
    // Create elemental and semantic vectors for each concept, and elemental vectors for predicates
    elementalVectors = new VectorStoreRAM(flagConfig);
    semanticVectors = new VectorStoreRAM(flagConfig);
    predicateVectors = new VectorStoreRAM(flagConfig);
    Random random = new Random();
    flagConfig.setContentsfields(itemFields);

    for (String fieldName : itemFields) {
      Terms terms = luceneUtils.getTermsForField(fieldName);
      TermsEnum termsEnum = terms.iterator(null);
      HashSet<String> addedConcepts = new HashSet<String>();
      BytesRef bytes;
      while((bytes = termsEnum.next()) != null) {
        Term term = new Term(fieldName, bytes);
        
        if (!luceneUtils.termFilter(term)) {
          VerbatimLogger.fine("Filtering out term: " + term + "\n");
          continue;
        }
  
        if (!addedConcepts.contains(term.text())) {
          addedConcepts.add(term.text());
          Vector semanticVector = VectorFactory.createZeroVector(
              flagConfig.vectortype(), flagConfig.dimension());

          if (flagConfig.deterministicvectors())
            random.setSeed(Bobcat.asLong(term.text()));

          Vector elementalVector = VectorFactory.generateRandomVector(
              flagConfig.vectortype(), flagConfig.dimension(),
              flagConfig.seedlength(), random);

          semanticVectors.putVector(term.text(), semanticVector);
          elementalVectors.putVector(term.text(), elementalVector);
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
      if (!luceneUtils.termFilter(term, dummyArray, 0, Integer.MAX_VALUE, Integer.MAX_VALUE)) {  
        continue;
      }

      if (flagConfig.deterministicvectors())
        random.setSeed(Bobcat.asLong(term.text().trim()));

      Vector elementalVector = VectorFactory.generateRandomVector(
          flagConfig.vectortype(), flagConfig.dimension(),
          flagConfig.seedlength(), random);
      predicateVectors.putVector(term.text().trim(), elementalVector);

      if (flagConfig.deterministicvectors())
        random.setSeed(Bobcat.asLong(term.text().trim()+"-INV"));

      Vector inverseElementalVector = VectorFactory.generateRandomVector(
          flagConfig.vectortype(), flagConfig.dimension(),
          flagConfig.seedlength(), random);
      predicateVectors.putVector(term.text().trim()+"-INV", inverseElementalVector);
    }

    String fieldName = PREDICATION_FIELD; 
      // Iterate through documents (each document = one predication).
      Terms allTerms = luceneUtils.getTermsForField(fieldName);
      termsEnum = allTerms.iterator(null);
      while((bytes = termsEnum.next()) != null) {
        int pc = 0;
        Term term = new Term(fieldName, bytes);
        pc++;

        // Output progress counter.
        if ((pc > 0) && ((pc % 10000 == 0) || ( pc < 10000 && pc % 1000 == 0 ))) {
          VerbatimLogger.info("Processed " + pc + " unique predications ... ");
        }

        DocsEnum termDocs = luceneUtils.getDocsForTerm(term);
        termDocs.nextDoc();
        Document document = luceneUtils.getDoc(termDocs.docID());

        String subject = document.get(SUBJECT_FIELD);
        String predicate = document.get(PREDICATE_FIELD);
        String object = document.get(OBJECT_FIELD);

        float sWeight =1;
        float oWeight =1;
        float pWeight =1;

        sWeight = luceneUtils.getGlobalTermWeight(new Term(SUBJECT_FIELD, subject));
        oWeight = luceneUtils.getGlobalTermWeight(new Term(OBJECT_FIELD, object));
        // TODO: Explain different weighting for predicates, log(occurrences of predication)
        pWeight = (float) Math.log(1 + luceneUtils.getGlobalTermFreq(term));

        Vector subject_semanticvector = semanticVectors.getVector(subject);
        Vector object_semanticvector = semanticVectors.getVector(object);
        Vector subject_elementalvector = elementalVectors.getVector(subject);
        Vector object_elementalvector = elementalVectors.getVector(object);
        Vector predicate_vector = predicateVectors.getVector(predicate);
        Vector predicate_vector_inv = predicateVectors.getVector(predicate+"-INV");

        if (subject_semanticvector == null || object_semanticvector == null || predicate_vector == null) {	  
          logger.info("skipping predication " + subject + " " + predicate + " " + object);
          continue;
        }

        object_elementalvector.bind(predicate_vector);
        subject_semanticvector.superpose(object_elementalvector, pWeight*oWeight, null);
        object_elementalvector.release(predicate_vector);

        subject_elementalvector.bind(predicate_vector_inv);
        object_semanticvector.superpose(subject_elementalvector, pWeight*sWeight, null);
        subject_elementalvector.release(predicate_vector_inv);      
      } // Finish iterating through predications.
    

    //Normalize semantic vectors
    Enumeration<ObjectVector> e = semanticVectors.getAllVectors();
    while (e.hasMoreElements())	{
      e.nextElement().getVector().normalize();
    }

    VectorStoreWriter.writeVectors(flagConfig.elementalvectorfile(), flagConfig, elementalVectors);
    VectorStoreWriter.writeVectors(flagConfig.semanticvectorfile(), flagConfig, semanticVectors);
    VectorStoreWriter.writeVectors(flagConfig.predicatevectorfile(), flagConfig, predicateVectors);

    VerbatimLogger.info("Finished writing vectors.\n");
  }

  public static void main(String[] args) throws IllegalArgumentException, IOException {
    FlagConfig flagConfig = FlagConfig.getFlagConfig(args);
    args = flagConfig.remainingArgs;

    if (flagConfig.luceneindexpath().isEmpty()) {
      throw (new IllegalArgumentException("-luceneindexpath must be set."));
    }

    VerbatimLogger.info("Building PSI model from index in: " + flagConfig.luceneindexpath() + "\n");
    VerbatimLogger.info("Minimum frequency = " + flagConfig.minfrequency() + "\n");
    VerbatimLogger.info("Maximum frequency = " + flagConfig.maxfrequency() + "\n");
    VerbatimLogger.info("Number non-alphabet characters = " + flagConfig.maxnonalphabetchars() + "\n");

    createIncrementalPSIVectors(flagConfig);
  }
}
