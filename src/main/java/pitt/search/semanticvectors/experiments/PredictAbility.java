package pitt.search.semanticvectors.experiments;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;

import org.netlib.blas.BLAS;

import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.ObjectVector;
import pitt.search.semanticvectors.TermTermVectorsFromLucene.PositionalMethod;
import pitt.search.semanticvectors.VectorStoreRAM;
import pitt.search.semanticvectors.utils.SigmoidTable;
import pitt.search.semanticvectors.vectors.PermutationVector;
import pitt.search.semanticvectors.vectors.VectorType;
import pitt.search.semanticvectors.vectors.VectorUtils;

public class PredictAbility {

	/**
	 * This class takes as input the input (semantic) and output (context) vectors for
	 * a neural embedding model, and proceeds to calculate the error when using
	 * these weights to make predictions about an unseen body of text
	 *  
	 *  command line parameters, which may include
 	 * -semanticvectorfile   - the embeddingvectors.bin file (input weights)
     * -elementalvectorfile  - the elementalvectors.bin file (output weights)
     * -permutationcachefile - the permutationvectors.bin file (permutations used for EARP models)
     * -windowradius 2 		 - the radius of the sliding window
	 * -positionalmethod     - basic | directional | permutation | proximity with the last three indicating EARP models
	 *
	 *  The last argument should be the filename of said body of text
	 *  
	 * @param args
	 * @throws IOException 
	 */
	
	public static void main(String[] args) throws IOException {
		
		SigmoidTable sigmoidTable = new SigmoidTable(6,1000);

		FlagConfig flagConfig = FlagConfig.getFlagConfig(args);
		String theFile		  = flagConfig.remainingArgs[0];
		
		BufferedReader theReader = new BufferedReader(new FileReader(new File(theFile)));
		
		VectorStoreRAM permutationCache = new VectorStoreRAM(flagConfig);
		boolean doPermute = false;
		
		//If this is an EARP model, load the permutations into memory
		if (!flagConfig.permutationcachefile().isEmpty() && !flagConfig.positionalmethod().equals(PositionalMethod.BASIC))
		{
			VectorType originalVectorType = flagConfig.vectortype();
			permutationCache.initFromFile(flagConfig.permutationcachefile());
			doPermute = true; 
			flagConfig.setVectortype(originalVectorType);			
		}
		
		//Load the input and output weights into memory
		VectorStoreRAM semanticVectors 	= new VectorStoreRAM(flagConfig);
		semanticVectors.initFromFile(flagConfig.semanticvectorfile());
		
		VectorStoreRAM contextVectors 	= new VectorStoreRAM(flagConfig);
		contextVectors.initFromFile(flagConfig.elementalvectorfile());
		
		String nextLine = theReader.readLine();
		String allTerms = "";
		
		//collapse file into a string, removing all non-alphabet characters (except ')
		while (nextLine != null)
		{
			allTerms=allTerms.concat((nextLine.replaceAll("[^a-z']", " ")+" "));
			nextLine = theReader.readLine();
		}
		
		
		ArrayList<String> focusTerms = new ArrayList<String>();
		Hashtable<String,Double> termPerplexities = new Hashtable<String,Double>();
		Hashtable<String,Integer> allCounts = new Hashtable<String,Integer>();
		
		//this is used to calculate the scalar product (actually, with the basic model only)
		BLAS blas = BLAS.getInstance();
		
		double logProbability  = 0; //capture sum of log probability of observed word pairs
		double allCountz = 0; //count the number of observed word pairs
		
		String[] terms = allTerms.split(" +"); //tokenize on any number of spaces
		
		for (int q=0; q < terms.length; q++) //move sliding window through the terms
			{
				int windowStart = Math.max(0, q-flagConfig.windowradius());
				int windowEnd  =  Math.min(terms.length-1, q+flagConfig.windowradius());
			  
				String focusTerm = terms[q]; //the center of the sliding window
			  
				double localErrors = 0; //keep track of local (window-level) error
				double localCounts = 0; //keep track of local (window-level) counts
				double localProbs  = 1; //keep track of product of local probabilities
			  
			  if (semanticVectors.containsVector(focusTerm)) //skip words that were not found in the training corpus
			  {
				  //keep track of per-term statistics - may be of interest for clustering downstream
				  if (!termPerplexities.containsKey(focusTerm))
				  {
					  termPerplexities.put(focusTerm, new Double(0));
					  allCounts.put(focusTerm, new Integer(0));
				  }
				
				  int[] permutation = null;
				  
				  String window = ""; //reconstruct the sliding window as text, as this may be interesting to look at later
				  
				  //process a sliding window
				  for (int cursor=windowStart;  cursor <= windowEnd; cursor++)
				  	{
					   if (cursor==q) 
						   {
						   window += " ["+focusTerm+"] ";
						   continue;
						   }
					   
					   String contextTerm = terms[cursor];
					   
					   //if the context term does not have a vector (i.e. wasn't found in the training corpus) surround it with asterisks
					   if (!contextVectors.containsVector(contextTerm) || contextVectors.getVector(contextTerm).isZeroVector())
						   contextTerm="**"+contextTerm+"**";
					   
					   window += contextTerm+" ";
					   
					   //work out the position relative to the focus term for permutation-based models
					   String nextPos = ""+(cursor-q);
					   
					   //for the "directional" model - is this before or after the focus term?
					   if (flagConfig.positionalmethod().equals(PositionalMethod.DIRECTIONAL))
					   { nextPos = "" + (int) Math.signum(cursor-q);}
					   
					   //get the relevant permutation if this is an EARP model (otherwise leave permutation as null)
					   if (doPermute) permutation = ((PermutationVector) permutationCache.getVector(nextPos)).getCoordinates();
					   
					    if (contextVectors.containsVector(contextTerm) &&  ! contextVectors.getVector(contextTerm).isZeroVector())
					    {
					    	//a relic - initial experiments used mean squared error instead of perplexity
					    	double error =  Math.pow(2,1-sigmoidTable.sigmoid(VectorUtils.scalarProduct(semanticVectors.getVector(focusTerm),contextVectors.getVector(contextTerm), flagConfig, blas,permutation))); 
					    
					    	double pWord = sigmoidTable.sigmoid(VectorUtils.scalarProduct(semanticVectors.getVector(focusTerm),contextVectors.getVector(contextTerm), flagConfig, blas,permutation)); 
					    	
					    	if (pWord > 0) //todo: find the source of the zero probabilities 
					    	{
					    		//global stats
					    	    logProbability += Math.log(pWord)/Math.log(2);   //not exactly perplexity, but no underflow errors which is nice
					    		allCountz++;
						    		
					    		//local stats
						    	localProbs *= pWord;
						    	localErrors +=	error;
						    	localCounts++;
					    
					    	}
					    }
				  	}
				  
		      if (localCounts > 0) //i.e. some non-zero predictions occurred
		      {
		    	  	//window-level perplexity
		    	  	double localPerplexity = Math.pow(localProbs, -1/ (double) localCounts);	   
		    	  	
		    	  	//term-level perplexity (for downstream analysis) - average of all window-level perplexities for term
		    	  	if (!focusTerms.contains(focusTerm)) focusTerms.add(focusTerm);
		    	  	double prior = termPerplexities.get(focusTerm);
		    	  	prior += (localPerplexity / localCounts);
		    	  	termPerplexities.replace(focusTerm,prior);
		    	  	Integer priorInt = allCounts.get(focusTerm);
		    	  	priorInt++;
		    	  	allCounts.replace(focusTerm, priorInt);
			  
		    	  	//if the -bindnotreleasehack flag is used, output the window-level perplexity
			  if (flagConfig.bindnotreleasehack()) 
				  System.out.println("-E-\t"+(localPerplexity)+"\t"+window);
			  }
			  
			  }
			}
		
		
		theReader.close();
		System.out.print("\n"+theFile+"_perplexity\t");
		System.out.println(-logProbability / (double) allCountz); //average -log(probabity) for all term/context pairs in the file
		
		//untested - if bindnotreleasehack, output term level perplexities
		if (flagConfig.bindnotreleasehack())
		{
			System.out.println("----------------");
			
			Enumeration<String> nation = termPerplexities.keys();
			System.out.print("\nALLTERMS_"+theFile+"_errors\t");
		
		while (nation.hasMoreElements())
		{
			String next = nation.nextElement();
			System.out.print(next+":" +termPerplexities.get(next) / new Double(allCounts.get(next))+" ");
		}
	
		System.out.println();
		}
	}

}
