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

import pitt.search.semanticvectors.CompressedVectorStoreRAM;
import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.ObjectVector;
import pitt.search.semanticvectors.TermTermVectorsFromLucene.PositionalMethod;
import pitt.search.semanticvectors.VectorStoreRAM;
import pitt.search.semanticvectors.VectorStoreWriter;
import pitt.search.semanticvectors.utils.SigmoidTable;
import pitt.search.semanticvectors.utils.VerbatimLogger;
import pitt.search.semanticvectors.vectors.PermutationVector;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
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
	
	  /**
	   * Get incoming term vector from component ngram vectors (for subword embeddings)
	   */
	  public static Vector  getNgramVector(String term, VectorStoreRAM termVectors, CompressedVectorStoreRAM subwordEmbeddings, FlagConfig flagConfig)
	  {
		     ArrayList<String> subwordStrings = getComponentNgrams(term, flagConfig.minimum_ngram_length(), flagConfig.maximum_ngram_length());
			 
			 //used in the EARP paper: weight of each subword (ngram) == weight of original word
			 float weightReduction = 1 / ((float) subwordStrings.size()+1);
			 Vector wordVec = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());
			 
			 Vector termVector = null;
			 	if (termVectors.containsVector(term))
				 termVector = termVectors.getVector(term);
			 
			 //if flagConfig.balanced_subwords(), combined weight of all subwords (ngrams) == weight of original word
			 if (flagConfig.balanced_subwords()) weightReduction = 1; 
			 	
			 if (termVector != null)
				 wordVec.superpose(termVector, weightReduction, null);
				
			 if (flagConfig.balanced_subwords()) weightReduction = 1 / ((float) subwordStrings.size()); 
			 			 
			  for (String subword: subwordStrings)
			  { 
				  if (subwordEmbeddings.containsVector(subword))
				  {
					   Vector subwordVector = subwordEmbeddings.getVector(subword, false);
					  wordVec.superpose(subwordVector, weightReduction,null);
				  }
			 }
		  
			  if (! flagConfig.notnormalized() && !wordVec.isZeroVector())
				  wordVec.normalize();
			  return wordVec;
	  }
	
	  
	  public static ArrayList<String> getComponentNgrams(String incomingString, int minimum_ngram_length, int maximum_ngram_length)
	  {
		  ArrayList<String> outgoingNgrams = new ArrayList<String>();
		  String toDecompose = "<"+incomingString+">";
		  
		  for (int ngram_length = minimum_ngram_length; ngram_length <= maximum_ngram_length; ngram_length++)
			  for (int j=0; j <= (toDecompose.length() - ngram_length); j++)
				  {
				  	String toAdd = toDecompose.substring(j,j+ngram_length);
				  	//don't include the term itself 
				  	if (!toAdd.equals(toDecompose))
				  		outgoingNgrams.add(toAdd);
				  }
		  
		  return outgoingNgrams;
	  }
	
	public static void main(String[] args) throws IOException {
		
		SigmoidTable sigmoidTable = new SigmoidTable(50,1000);
		FlagConfig flagConfig = FlagConfig.getFlagConfig(args);
		String theFile		  = flagConfig.remainingArgs[0];

		
		BufferedReader theReader = new BufferedReader(new FileReader(new File(theFile)));
		
		VectorStoreRAM permutationCache = null;
		boolean doPermute = false;
		
		//If this is an EARP model, load the permutations into memory
		if (!flagConfig.positionalmethod().equals(PositionalMethod.BASIC) && !flagConfig.permutationcachefile().isEmpty())
		{
			VectorType originalVectorType = flagConfig.vectortype();
			permutationCache=new VectorStoreRAM(flagConfig);
			permutationCache.initFromFile(flagConfig.permutationcachefile());
			doPermute = true; 
			flagConfig.setVectortype(originalVectorType);			
		}
		
		//If subword embeddings requested and were generated during training, load them into memory
		CompressedVectorStoreRAM subwordEmbeddings
		 	= null; 
		
		if (flagConfig.subword_embeddings())
		try 
		{
			VectorStoreRAM incomingSubwordEmbeddings = 
					new VectorStoreRAM(flagConfig);
			
			incomingSubwordEmbeddings.initFromFile("subwordembeddings.bin");
			
			subwordEmbeddings = 
					new CompressedVectorStoreRAM(flagConfig, incomingSubwordEmbeddings);
			//System.err.println("Loaded subword embeddings from subwordembeddings.bin");
		}
		catch (Exception e)
		{
			VerbatimLogger.info("No pretrained subword embeddings found as subwordembeddingvectors.bin");
		}
		
		//Load the input and output weights into memory
		VectorStoreRAM semanticVectors 	= new VectorStoreRAM(flagConfig);
		semanticVectors.initFromFile(flagConfig.semanticvectorfile());
		
		VectorStoreRAM contextVectors 	= new VectorStoreRAM(flagConfig);
		contextVectors.initFromFile(flagConfig.elementalvectorfile());
		
		String nextLine = theReader.readLine();
		String allTerms = "";
		
		
		ArrayList<String> focusTerms = new ArrayList<String>();
		Hashtable<String,Double> termPerplexities = new Hashtable<String,Double>();
		Hashtable<String,Integer> allCounts = new Hashtable<String,Integer>();
		
		//this is used to calculate the scalar product (actually, with the basic model only)
		BLAS blas = BLAS.getInstance();
		
		double logProbability  = 0; //capture sum of log probability of observed word pairs
		double allCountz = 0; //count the number of observed word pairs
		int lineCount=0;
		
		//collapse line into a string, removing all non-alphabet characters (except ')
		while (nextLine != null)
		{
			lineCount++;
			double localLogProbability  = 0; //capture sum of log probability of observed word pairs
			
			allTerms=nextLine.replaceAll("[^a-z']", " ")+" ";
			String[] terms = allTerms.split(" +"); //tokenize on any number of spaces
			double windowCount = 0; //keep track of number windows processed
			
			for (int q=0; q < terms.length; q++) //move sliding window through the terms
				{
					double windowProbabilities = 0;
				
					int windowStart = Math.max(0, q-flagConfig.windowradius());
					int windowEnd  =  Math.min(terms.length-1, q+flagConfig.windowradius());
			  
					String focusTerm = terms[q]; //the center of the sliding window
			  
					double localErrors = 0; //keep track of windows with some predictions
					double localCounts = 0; //keep track of local (window-level) counts
					double localProbs  = 1; //keep track of product of local probabilities
			  
					Vector focusTermVector = null;
				
					if (!flagConfig.subword_embeddings())
					{
						if (semanticVectors.containsVector(focusTerm)) 
						focusTermVector = semanticVectors.getVector(focusTerm);
					}
					else  focusTermVector = getNgramVector(focusTerm, semanticVectors, subwordEmbeddings, flagConfig);
				
		
				
					if (focusTermVector != null && !focusTermVector.isZeroVector()) //skip words that were not found in the training corpus unless subword embeddings are used
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
					   {  contextTerm="**"+contextTerm+"**";}
					   
					   window += contextTerm+" ";
					   
					   //work out the position relative to the focus term for permutation-based models
					   String nextPos = ""+(cursor-q);
					   
					   //for the "directional" model - is this before or after the focus term?
					   if (flagConfig.positionalmethod().equals(PositionalMethod.DIRECTIONAL))
					   { nextPos = "" + (int) Math.signum(cursor-q);}
					   
					   //get the relevant permutation if this is an EARP model (otherwise leave permutation as null)
					   if (doPermute) permutation = ((PermutationVector) permutationCache.getVector(nextPos).copy()).getCoordinates();
					   
					   
					    if (contextVectors.containsVector(contextTerm) &&  ! contextVectors.getVector(contextTerm).isZeroVector())
					    {
					    	//a relic - initial experiments used mean squared error instead of perplexity
					    	double error =  Math.pow(2,1-sigmoidTable.sigmoid(VectorUtils.scalarProduct(focusTermVector,contextVectors.getVector(contextTerm), flagConfig, blas,permutation))); 
					    double pWord = sigmoidTable.sigmoid(VectorUtils.scalarProduct(focusTermVector,contextVectors.getVector(contextTerm), flagConfig, blas,permutation)); 
					    	
					    	if (pWord > 0) 
					    	{
					    		//global stats
					    	    localLogProbability += Math.log(pWord)/Math.log(2);   //not exactly perplexity, but no underflow errors which is nice
					    		allCountz++;
						    		
					    		//local stats
						    	localProbs *= pWord;
						    	localErrors +=	error;
						    	localCounts++;
					    
					    	}
					    	else 
					    	{
					    	
					    	 System.out.println("P(word) <= 0 -"+pWord+"- at "+contextTerm+"|"+focusTerm);
					    	 System.out.println(contextTerm+"\n"+contextVectors.getVector(contextTerm));
					    	 System.out.println(focusTerm+"\n"+semanticVectors.getVector(focusTerm));
						 System.out.println("Scalar product "+VectorUtils.scalarProduct(semanticVectors.getVector(focusTerm),contextVectors.getVector(contextTerm), flagConfig, blas,permutation)); 
						 System.out.println("Sigmoid "+ sigmoidTable.sigmoid(VectorUtils.scalarProduct(semanticVectors.getVector(focusTerm),contextVectors.getVector(contextTerm), flagConfig, blas,permutation))); 
					    	
					    	}
					    }
				  	} //end current sliding window position
				  
		      if (localCounts > 0) //i.e. some non-zero predictions occurred
		      {
		    	     windowCount++;
		    	  	//window-level perplexity
		    	  	double localPerplexity = Math.pow(localProbs, -1/ (double) localCounts);	   
		    	  	
		    	  	//term-level perplexity (for downstream analysis) - average of all window-level perplexities for term
		    	  	if (!focusTerms.contains(focusTerm)) focusTerms.add(focusTerm);
		    	  	double prior = termPerplexities.get(focusTerm);
		    	  	prior += localPerplexity;
		    	  	termPerplexities.replace(focusTerm,prior);
		    	  	Integer priorInt = allCounts.get(focusTerm);
		    	  	priorInt++;
		    	  	allCounts.replace(focusTerm, priorInt);
			  
		    	  	//if the -bindnotreleasehack flag is used, output the window-level perplexity
			  if (flagConfig.bindnotreleasehack()) 
				  System.out.println("-E-\t"+(localPerplexity)+"\t"+window);
			  
			  //sum log prob for sliding window
				windowProbabilities += localLogProbability; 
			
		      } //end counts > 0 condition
			  
		   
		      
			  } //end focus term exists condition
					
				if (windowCount > 0)	
					logProbability += windowProbabilities; //sum log probs (double) windowCount; 
					
				} //end current line
		
			
		nextLine = theReader.readLine();
		
		}
		theReader.close();
		System.out.print("\n"+theFile+"_perplexity\t");
		System.out.println(-logProbability / allCountz); //average -log(probability) for all term/context pairs in the file
		
		//untested - if bindnotreleasehack, output term level perplexities
		if (flagConfig.bindnotreleasehack())
		{
			VectorStoreRAM outputVectors = new VectorStoreRAM(flagConfig);
			try {
					outputVectors.initFromFile("perplexityvectors.bin");
				}
			catch (Exception e)
			{
				//this is expected when no such file exists
			}
			
			Vector perplexityVector = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());
			System.out.println("----------------");
			
			Enumeration<String> nation = termPerplexities.keys();
			System.out.print("\nALLTERMS_"+theFile+"_errors\t");
		
		while (nation.hasMoreElements())
		{
			String next = nation.nextElement();
			double score = termPerplexities.get(next) / new Double(allCounts.get(next));
			System.out.print(next+":" +score+" ");
			
			if (flagConfig.subword_embeddings())
			{
				perplexityVector.superpose(getNgramVector(next, semanticVectors, subwordEmbeddings, flagConfig), score, null);

			}
			else
			perplexityVector.superpose(semanticVectors.getVector(next), score, null);
		}
		
		perplexityVector.normalize();
		
		if (theFile.contains("Dementia"))
			outputVectors.putVector("1_"+theFile, perplexityVector);
		else
			outputVectors.putVector("0_"+theFile, perplexityVector);
	
		System.out.println();
		
		VectorStoreWriter.writeVectorsInLuceneFormat("perplexityvectors.bin", flagConfig, outputVectors);
		}
	}

}
