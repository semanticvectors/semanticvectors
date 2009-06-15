package qut.beagle;

import java.util.LinkedList;

import org.apache.lucene.index.IndexReader;

import pitt.search.semanticvectors.LuceneUtils;
import pitt.search.semanticvectors.ObjectVector;
import pitt.search.semanticvectors.SearchResult;
import pitt.search.semanticvectors.VectorSearcher;
import pitt.search.semanticvectors.VectorStore;
import pitt.search.semanticvectors.VectorStoreReader;
import pitt.search.semanticvectors.VectorStoreWriter;
import pitt.search.semanticvectors.ZeroVectorException;

// This class demonstrates use of this package for generating and querying BEAGLE vector stores
public class BeagleTest 
{	
	// Method for querying a BeagleNGramVectors store.
	public void testQuery( String searchfile, String indexfile, String query )
	{
		VectorSearcher vs;
		LuceneUtils lUtils = null;
		VectorStore queryVecReader, searchVecReader;
		LinkedList<SearchResult> results;
		int numResults = 20;
		
		BeagleUtils utils = BeagleUtils.getInstance();
		utils.setFFTCacheSize(100);
				
		try
		{
			queryVecReader = new VectorStoreReader(indexfile);
			searchVecReader = new VectorStoreReader(searchfile);
						
			BeagleCompoundVecBuilder bcb = new BeagleCompoundVecBuilder ();
			
			String[] queryTerms = query.split(" ");
						
			// Create VectorSearcher and search for nearest neighbors.
			vs = new BeagleVectorSearcher( queryVecReader, searchVecReader, lUtils, queryTerms);	
			System.err.print("Searching term vectors, searchtype BEAGLE ... ");
			
			results = vs.getNearestNeighbors(numResults);			
			
		} 
		catch (Exception e) 
		{
			System.err.println(e.getMessage());
			results = new LinkedList<SearchResult>();
		}	
		
		// Print out results.
		if (results.size() > 0) {
			System.err.println("Search output follows ...\n");
			for (SearchResult result: results) {
				System.out.println(result.getScore() + ":" +
													 ((ObjectVector)result.getObject()).getObject().toString());
			}	
		} else {
			System.err.println("No search output.");
		}
	}
			
	// Method for generating a BeagleNGramVectors store.
	public void createNGrams( String fileOut, int vecLength, int numGrams )
	{
		VectorStoreWriter vecWriter;
		BeagleNGramVectors bngv;
		BeagleUtils utils = BeagleUtils.getInstance();
		
		long time;
		
		try
		{
			pitt.search.semanticvectors.ObjectVector.vecLength = vecLength;
			
			time = System.currentTimeMillis();
			
			bngv = new BeagleNGramVectors( "index", 5, 2, new String[] {"contents"}, numGrams, "stoplist.txt" );
			
			time = System.currentTimeMillis() - time;
			
			System.out.println("\nTime to process: " + time/1000 + " secs.");
			System.out.println("\nNumber of convolutions: " + utils.getNumConvolutions());
			
			vecWriter = new VectorStoreWriter();
			vecWriter.WriteVectors(fileOut + "_" + vecLength + "_" + numGrams + ".bin", bngv);
						
			VectorStore indexVectors = bngv.getIndexVectors();
			vecWriter = new VectorStoreWriter();
			vecWriter.WriteVectors( fileOut + "_" + vecLength + "_" + numGrams + "_index.bin", indexVectors);
			
			bngv = null;			
			System.gc();			
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}		
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) 
	{
		BeagleTest bt = new BeagleTest();
		
		// Some example method calls
		bt.createNGrams( "KJB", 1024, 5 );
		
		bt.testQuery( "TASA_1024_5.bin", "TASA_1024_5_index.bin", "? sea" );		
	}

}






