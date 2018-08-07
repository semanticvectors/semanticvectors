package pitt.search.semanticvectors.experiments;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import pitt.search.semanticvectors.CloseableVectorStore;
import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.LuceneUtils;
import pitt.search.semanticvectors.ObjectVector;
import pitt.search.semanticvectors.SearchResult;
import pitt.search.semanticvectors.VectorSearcher;
import pitt.search.semanticvectors.VectorStoreRAM;
import pitt.search.semanticvectors.VectorStoreReader;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.ZeroVectorException;

public class AnalogyTest {
	
	//synchronized variables across to keep track
	//of performance metrics
	List<Double> reciptot =  Collections.synchronizedList(new ArrayList<Double>());
	AtomicInteger exampletot = new AtomicInteger();
	AtomicInteger acctot = new AtomicInteger();
	
	List<Double> recipsubset = Collections.synchronizedList(new ArrayList<Double>());
	AtomicInteger examplessubset = new AtomicInteger();
	AtomicInteger accsubset = new AtomicInteger();
	
	HashSet<String> seenEm = new HashSet<String>();
	
	/***
	 * Class to process a single analogy
	 * @author tcohen1
	 *
	 */
	
	private class AnalogyProcessor implements Runnable
	{
	
		FlagConfig flagConfig;
		VectorStoreRAM termVectors;
		String inLine;
		int threadno;
		
		public AnalogyProcessor(FlagConfig flagConfig, VectorStoreRAM termVectors, String inLine, int threadno)
		{
			this.flagConfig = flagConfig;
			this.termVectors = termVectors;
			this.inLine = inLine;
			this.threadno = threadno;
		
			
			}
		
		
		/**
		 * Method to process a single analogy
		 * Questions will be skipped if any of the four terms are not present
		 * @return
		 */
		
		
		public void processAnalogy()
		{
			
		String[] queryTerms = inLine.toLowerCase().split(" ");
	    
		//not a proportional analogy
		if (queryTerms.length < 4) 
	    	System.out.println(threadno+": "+inLine+": Skipping line");
	    	
		Vector aTermVector = null;
		Vector bTermVector = null;
		Vector cTermVector = null;

		
		String missingTerms = "";
		if (!termVectors.containsVector(queryTerms[0])) missingTerms = missingTerms + queryTerms[0]+"; ";
		if (!termVectors.containsVector(queryTerms[1])) missingTerms = missingTerms + queryTerms[1]+"; ";
		if (!termVectors.containsVector(queryTerms[2])) missingTerms = missingTerms + queryTerms[2]+"; ";
		if (!termVectors.containsVector(queryTerms[3])) missingTerms = missingTerms + queryTerms[3]+"; ";
		
		if (!missingTerms.isEmpty())
		System.out.println(threadno+": "+"Missing terms "+missingTerms);
		
		aTermVector = termVectors.getVector(queryTerms[0]);
		bTermVector = termVectors.getVector(queryTerms[1]);
		cTermVector = termVectors.getVector(queryTerms[2]);
	
		Vector cueVector = bTermVector.copy();
		cueVector.superpose(aTermVector, -1, null);
		cueVector.superpose(cTermVector, +1, null);
		cueVector.normalize();
			
		int rank = 1;
		String object = "";
		
		
		try {
			VectorSearcher.VectorSearcherCosine analogySearcher
			 	 = new VectorSearcher.VectorSearcherCosine(termVectors,termVectors,null, flagConfig, cueVector);
			
			//get top 1000 results plus the three query terms
			LinkedList<SearchResult> results = analogySearcher.getNearestNeighbors(1003);
			
			
			for (SearchResult sr:results)
			{
				object = sr.getObjectVector().getObject().toString();
			
				//result found 
				if (object.equals(queryTerms[3]))
					break;
				
				//ignore query terms
				if (!(object.equals(queryTerms[0])
						|| object.equals(queryTerms[1])
						  || object.equals(queryTerms[2]))
							)
					rank++;
				
			}
			} 	catch (ZeroVectorException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
		System.out.println(threadno+": "+"Error on example "+ inLine);
	}
		
		//calculate reciprocal rank as a more granular metric than accuracy
		double reciprank = 1 / (double) rank;
		if (reciprank < 0.001) reciprank = 0;
		
		examplessubset.incrementAndGet();
		exampletot.incrementAndGet();;
		recipsubset.add(reciprank);
		reciptot.add(reciprank);
		
		//correct result (top ranked other than query terms)
		if (reciprank == 1)
			{
				accsubset.incrementAndGet();;
				acctot.incrementAndGet();;
			}
		
		System.out.println(threadno+": "+inLine +" --> "+object+" "+rank+" "+reciprank);
		
			
	}

	@Override
	public void  run() {
		
		processAnalogy();
		return;
		
	}

	}
	
	
	
	public void runTests(String[] args) throws IOException, InterruptedException, ExecutionException {
		// TODO Auto-generated method stub
		FlagConfig flagConfig = FlagConfig.getFlagConfig(args);
		VectorStoreRAM termVectors = new VectorStoreRAM(flagConfig);
		
		termVectors.initFromFile(flagConfig.queryvectorfile());
		
		//add input and output weights (or just superpose any two vector stores)
		if (!flagConfig.initialtermvectors().isEmpty())
		{
			CloseableVectorStore secondSet = VectorStoreReader.openVectorStore(flagConfig.initialtermvectors(), flagConfig);
			Enumeration<ObjectVector> secondSetObjectVectors = secondSet.getAllVectors();
			
			while (secondSetObjectVectors.hasMoreElements())
			{
				ObjectVector nextOV = secondSetObjectVectors.nextElement();
				termVectors.getVector(nextOV.getObject()).superpose(nextOV.getVector(), 1, null);
			}
		
			
			System.err.println("Added "+flagConfig.initialtermvectors()+ " to "+flagConfig.queryvectorfile()+"");
		}
			
		//restrict the search space to a subset of terms
		if (!flagConfig.startlistfile().isEmpty())
		{
			Enumeration<ObjectVector> allObjectVectors = termVectors.getAllVectors();
			LuceneUtils lUtils = new LuceneUtils(flagConfig);
			
			while (allObjectVectors.hasMoreElements())
			{
				ObjectVector nextOV = allObjectVectors.nextElement();
				
				if (!lUtils.startlistContains(nextOV.getObject().toString()))
					termVectors.removeVector(nextOV.getObject());
			}
		}
		
		BufferedReader inputFileReader = new BufferedReader(new FileReader(new File(flagConfig.remainingArgs[0])));
		
		String header= "";
		int numthreads =  flagConfig.numthreads();
		 
	 

	      String inLine = inputFileReader.readLine();
			
  	  	ExecutorService executor = Executors.newFixedThreadPool(numthreads);
  	  	int threadno = 0;
  	  	List<Future> futures = new ArrayList<Future>();
	      
	      while (inLine != null)
	    		{
	    	  
	    	  	if (inLine.isEmpty())
	    	  	{
	    	  		inLine = inputFileReader.readLine();
	    	  		continue;
	    	  	}
	   
	    	  if (inLine.startsWith("#") || inLine.startsWith(":")) { //headers for analogy files (todo: add reference)
						
	    	  	  		//make sure prior jobs are finished before calculating category-level totals
	    	    	  	//while (!executor.isTerminated()) {}
	    		  		boolean allDone = false;
	    		  		
	    		  		//System.out.println(inLine);
	    		  		
	    		  		while (!allDone)
	    		  			{
	    		  			allDone = true;
	    		  			for (Future theFuture:futures)
	    		  				allDone = Boolean.logicalAnd(allDone, theFuture.isDone());
	    		  			}
	    		  		futures = new ArrayList<Future>();
	    		  
	    	  	  		String toReturn = "";
						
						double recipsubsetTot = 0;
						for (Double nextD:recipsubset)
							recipsubsetTot += nextD;
						
						if (!header.isEmpty())
						{
						toReturn = toReturn +flagConfig.remainingArgs[0]+" "+header+": mean reciprocal rank " +recipsubsetTot / (double) examplessubset.get()+"\n";
						toReturn = toReturn +flagConfig.remainingArgs[0]+" "+header+": overall accuracy "+ accsubset.get() / (double) examplessubset.get()+"\n";;
						toReturn = toReturn +flagConfig.remainingArgs[0]+" "+header+": total number of examples "+examplessubset+"\n";;
						System.out.println(toReturn);
						}
						
						
						
						header = inLine;
						recipsubset.clear();
						examplessubset.set(0);
						accsubset.set(0);
						}
	    	  	  	else
		    	  	{
		    	  		futures.add(executor.submit(new AnalogyProcessor(flagConfig, termVectors, inLine, ++threadno)));
		    	  	}
	    	  
	    	  inLine = inputFileReader.readLine();
	    		
	    	  	}
	    	  	
	    	  	executor.shutdown();
	    	  	while (!executor.isTerminated()) {}
	    
	      		inputFileReader.close();
	      		
	    		double recipsubsetTot = 0;
	    		for (Double nextD:recipsubset)
	    			recipsubsetTot += nextD;
	    		
	    		System.out.println(flagConfig.remainingArgs[0]+" "+header+": mean reciprocal rank " +recipsubsetTot / (double) examplessubset.get());
	    		System.out.println(flagConfig.remainingArgs[0]+" "+header+": overall accuracy "+ accsubset.get() / (double) examplessubset.get());
	    		System.out.println(flagConfig.remainingArgs[0]+" "+header+": total number of examples "+examplessubset.get());
	    		
	    		header = "total";
	    		double reciptotTot = 0;
	    		for (Double nextD:reciptot)
	    			reciptotTot += nextD;
	    		
	    		System.out.println(flagConfig.remainingArgs[0]+" "+header+": mean reciprocal rank " +reciptotTot / (double) exampletot.get());
	    		System.out.println(flagConfig.remainingArgs[0]+" "+header+": overall accuracy "+ acctot.get() / (double) exampletot.get());
	    		System.out.println(flagConfig.remainingArgs[0]+" "+header+": total number of examples "+exampletot.get());
	    	
	    		
	    		
	    		
	    	 
	    		
	}
	    	  

	      
	
	
	public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {
		new AnalogyTest().runTests(args);
	}
		   
		
	

}
