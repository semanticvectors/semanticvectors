package pitt.search.semanticvectors;

import java.util.ArrayList;
import java.util.Random;

import pitt.search.semanticvectors.utils.VerbatimLogger;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;


/**
 * This class stores a fixed number of vectors for an unbounded number of entities,
 * by hashing the keys for these entities to a value between 0 and the "maxVectors"
 * variable. Collisions are not resolved by design, so it is probable that entities
 * will share vectors at times. This provides a memory-bounded way to index
 * n-grams, as proposed in Bojanowski et al 2017
 * @author tcohen1
 *
 */

public class CompressedVectorStoreRAM  {

	private int maxVectors = 2000000; 
	private Random random;
	private FlagConfig flagConfig;
	private Vector[] vectorTable;
	long[] observationCounts;
	long totalCounts;
	
	
	
	public CompressedVectorStoreRAM(FlagConfig flagConfig) {
		random = new Random();
		this.flagConfig = flagConfig;
		observationCounts = new long[maxVectors];
		vectorTable = new Vector[maxVectors];
	}
	
	/**
	 * Load previous model
	 * @param flagConfig
	 * @param vectorStoreRAM
	 */
	
	public CompressedVectorStoreRAM(FlagConfig flagConfig, VectorStoreRAM vectorStoreRAM) 
	{
		random = new Random();
		this.flagConfig = flagConfig;
		observationCounts = new long[maxVectors];
		vectorTable = new Vector[maxVectors];
		int loadCount = 0;
		
		for (int q=0; q < maxVectors; q++)
			if (vectorStoreRAM.containsVector(""+q))
				{
					vectorTable[q] = vectorStoreRAM.getVector(""+q);
					loadCount++;
				}
		
		VerbatimLogger.info("Loaded "+loadCount+" subword vectors");
	} 
	
	
	 /**
	  * Get the hashed key for an object. Bojanowski et al use the FNV1a hash, which may
	  * (or may not) offer advantages over Java's built-in hash function, which has 
	  * been employed as a first pass here as it is considerably faster than 
	  * the Bobcat hash we've been using to generate deterministic vectors 
	  */
	
	 public int getHashedKey(String originalKey)
	 {
		
		int hashedKey = Math.abs(originalKey.hashCode()) % maxVectors;
		return hashedKey;
	 }
	
	

	  
	  /**
	   * Given an object, get its corresponding vector.
	   * 
	   * <p>
	   * This implementation only works for string objects so far.
	   * 
	   * @param subsSample - if true, this method will return "null" instead of a vector for Strings that 
	   * 					 hash to keys that have been encountered with a frequency > .01*flagConfig.samplingthreshold()
	   * 					 In practice this means n-grams are subsampled more aggressively than terms
	   * 					 Subsampling follows the procedure described in Mikolov 2013, 1-sqrt(T/F), with F
	   * 					 being the cumulative count of the observations of the relevant hash code / total observed hash codes 
	   * 
	   * @param desiredObject - the string you're searching for
	   * @return vector from the VectorStore, or null if not found. 
	   */
	  public Vector getVector(String desiredObject, boolean subSample) {
		int hashedKey = getHashedKey(desiredObject);
		
		observationCounts[hashedKey]++;
		totalCounts++;
		
		//the hashed key has been seen before - a vector exists
		if (vectorTable[hashedKey] != null)	
		{
			if (subSample)
			{
			double frequency 	= observationCounts[hashedKey]/ (double) totalCounts;
			double subsamp 	= Math.sqrt(flagConfig.samplingthreshold() / frequency);
			if (subsamp > 0 && random.nextDouble() <= subsamp)
				return null;
			}
			return vectorTable[hashedKey];
		} //previously unseen hash (and therefore previously unseen key)
		else {
	      Vector toBeAdded = VectorFactory.generateRandomVector(flagConfig.vectortype(), flagConfig.dimension(), flagConfig.seedlength, random);
	       vectorTable[hashedKey] = toBeAdded;
	      return toBeAdded;
	    }
	  }

	  /**
	   * Check if an entity maps to a hash for which a vector is present
	   * @param object
	   * @return
	   */

	  public boolean containsVector(String object) {
		  
		  int hashedKey = getHashedKey(object);
		  return (vectorTable[hashedKey] != null);
	  }

	  /**
	   * Get component n-grams for a given term (for subword embeddings)
	   * for testing only - the "live" version is in TermTermVectorsFrom Lucene
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
	    * Export as VectorStoreRAM to facilitate
	    * writing to disk
	    * @return
	    */
	  public VectorStoreRAM exportVectorStoreRAM()
	  {
		  VectorStoreRAM vectorStore = new VectorStoreRAM(flagConfig);
		  for (int q=0; q < vectorTable.length; q++)
		  {
			  if (vectorTable[q] != null)
			  {
				  vectorStore.putVector(""+q, vectorTable[q]);
			  }
		  }
		   return vectorStore;
	  }
	  
	  
	  /**
	   * For test purposes only
	   * @param args
	   */
	  
	  public static void main(String[] args)
	  {
		  FlagConfig flagConfig = FlagConfig.parseFlagsFromString("-dimension 100 -seedlength 100");
		  CompressedVectorStoreRAM cvs = new CompressedVectorStoreRAM(flagConfig);
		 
		 
		 // for (int i=0; i < 10; i++)
			//  System.out.println(cvs.getHashedKey("incontrovertible"));
		  
		  double time = System.currentTimeMillis();
		  for (int i=0; i < 100; i++)
			  
		  for (String subword:cvs.getComponentNgrams("incontrovertible"))
		  { //System.out.println(subword);
			  cvs.getHashedKey(subword);
			//System.out.println(cvs.getVector(subword));
			//cvs.getVector(subword).superpose(cvs.getVector("<testi"),1/(double) 8,null);
			  }
		  
		  System.out.println(System.currentTimeMillis()-time);
		  
		  
		  for (String subword:cvs.getComponentNgrams("testing"))
		  { System.out.println(subword);
			System.out.println(cvs.getVector(subword,false));
		  }
		  
	  }
	  
}
