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

import java.io.File;
import java.io.IOException;
import java.lang.Integer;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Random;
import java.util.logging.Logger;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.FSDirectory;

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
  private static final Logger logger = Logger.getLogger(
      PSI.class.getCanonicalName());
  private VectorType vectorType;
  private int dimension;
  private int seedlength;
  
  private VectorStoreRAM elementalVectors, semanticVectors, predicateVectors;
  private IndexReader indexReader;
  private String[] fieldsToIndex;
  private LuceneUtils lUtils;


  private PSI() {};

  /**
   * Creates incremental doc vectors, getting everything it needs from a
   * TermVectorsFromLucene object and a Lucene Index directory, and writing to a named file.
   * 
   * @param termVectorData Has all the information needed to create doc vectors.
   * @param indexDir Directory of the Lucene Index used to generate termVectorData
   * @param fieldsToIndex String[] containing fields indexed when generating termVectorData
   * @param vectorFileName Filename for the document vectors
   */
  public static void createIncrementalPSIVectors(
       VectorType vectorType, int dimension, int seedlength, String indexDir) throws IOException {
    PSI incrementalPSIVectors = new PSI();
    incrementalPSIVectors.dimension = dimension;
    incrementalPSIVectors.vectorType = vectorType;
    incrementalPSIVectors.seedlength = seedlength;
    incrementalPSIVectors.indexReader = IndexReader.open(FSDirectory.open(new File(indexDir)));
    
    String[] fieldsToIndex = {"subject", "object"};
    incrementalPSIVectors.fieldsToIndex = fieldsToIndex;
     
    if (incrementalPSIVectors.lUtils == null) {
      incrementalPSIVectors.lUtils = new LuceneUtils(indexDir);
    }
    incrementalPSIVectors.trainIncrementalPSIVectors();
  }

  private void trainIncrementalPSIVectors() throws IOException {
    int numdocs = indexReader.numDocs();
    
    // Create elemental and semantic vectors for each concept, and elemental vectors for predicates
    elementalVectors = new VectorStoreRAM(vectorType, dimension);
    semanticVectors = new VectorStoreRAM(vectorType, dimension);
    predicateVectors = new VectorStoreRAM(vectorType, dimension);
    Random random = new Random();
      
    TermEnum terms = this.indexReader.terms();
    HashSet<String> addedConcepts = new HashSet<String>();
     
    while(terms.next()){
    	
    	Term term = terms.term();
    	String field = term.field();
    	
    	if (field.equals("subject") | field.equals("object"))
		{
			
			
			if (! addedConcepts.contains(term.text()))
			{
				addedConcepts.add(term.text());
				 Vector semanticVector = VectorFactory.createZeroVector(vectorType, dimension);
				 Vector elementalVector = VectorFactory.generateRandomVector(vectorType, dimension, seedlength, random);
				 
				 semanticVectors.putVector(term.text(), semanticVector);
				 elementalVectors.putVector(term.text(), elementalVector);
			
			}
		}
			
		
		else if (field.equals("predicate"))
		{
			 Vector elementalVector = VectorFactory.generateRandomVector(vectorType, dimension, seedlength, random);
			 Vector inverseElementalVector = VectorFactory.generateRandomVector(vectorType, dimension, seedlength, random);
			 
			 predicateVectors.putVector(term.text(), elementalVector);
			 predicateVectors.putVector(term.text()+"-INV", inverseElementalVector);
				
			}
			
		}
		
    	
    
    // Iterate through documents (each document = one predication).
    for (int dc = 0; dc < numdocs; dc++) {
      // Output progress counter.
      if ((dc > 0) && ((dc % 10000 == 0) || ( dc < 10000 && dc % 1000 == 0 ))) {
        VerbatimLogger.info("Processed " + dc + " predications ... ");
      }

      Document document = indexReader.document(dc);
      
      String subject = document.get("subject");
      String predicate = document.get("predicate");
      String object = document.get("object");
      
      float sWeight =1;
      float oWeight =1;
      float pWeight =0;
      
      if (Flags.termweight.equalsIgnoreCase("idf"))
      {
    	  
      sWeight = lUtils.getIDF(new Term("subject",subject));
      oWeight = lUtils.getIDF(new Term("object",object));    
       
     }

      Vector subject_semanticvector = semanticVectors.getVector(subject);
      Vector object_semanticvector = semanticVectors.getVector(object);
      Vector subject_elementalvector = elementalVectors.getVector(subject);
      Vector object_elementalvector = elementalVectors.getVector(object);
      Vector predicate_vector = predicateVectors.getVector(predicate);
      Vector predicate_vector_inv = predicateVectors.getVector(predicate+"-INV");
      
       object_elementalvector.bind(predicate_vector);
       subject_semanticvector.superpose(object_elementalvector, oWeight, null);
       object_elementalvector.release(predicate_vector);
       
       subject_elementalvector.bind(predicate_vector_inv);
        object_semanticvector.superpose(subject_elementalvector, sWeight, null);
        subject_elementalvector.release(predicate_vector_inv);
        

      
    } // Finish iterating through predications.

    
    //Normalize semantic vectors
    Enumeration<ObjectVector> e = semanticVectors.getAllVectors();
    while (e.hasMoreElements())	{
      e.nextElement().getVector().normalize();
    }
    
    new VectorStoreWriter().writeVectors("elementalvectors.bin", elementalVectors);
    new VectorStoreWriter().writeVectors("semanticvectors.bin", semanticVectors);
    new VectorStoreWriter().writeVectors("predicatevectors.bin", predicateVectors);
    
    
    VerbatimLogger.info("Finished writing vectors.\n");

  }

  public static void main(String[] args) throws Exception {
    try {
      args = Flags.parseCommandLineFlags(args);
    } catch (IllegalArgumentException e) {
      throw e;
    }

    // Only two arguments should remain, the path to the Lucene index.
    if (args.length != 1) {
      throw (new IllegalArgumentException("After parsing command line flags, there were "
          + args.length + " arguments, instead of the expected 1."));
    }

  
    logger.info("Minimum frequency = " + Flags.minfrequency);
    logger.info("Maximum frequency = " + Flags.maxfrequency);
    logger.info("Number non-alphabet characters = " + Flags.maxnonalphabetchars);
    logger.info("Contents fields are: " + Arrays.toString(Flags.contentsfields));

    createIncrementalPSIVectors(VectorType.valueOf(Flags.vectortype.toUpperCase()), Flags.dimension, Flags.seedlength, args[0]);
  }
}
