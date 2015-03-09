package qut.beagle;

import pitt.search.semanticvectors.CloseableVectorStore;
import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.LuceneUtils;
import pitt.search.semanticvectors.ObjectVector;
import pitt.search.semanticvectors.SearchResult;
import pitt.search.semanticvectors.VectorSearcher;
import pitt.search.semanticvectors.VectorStore;
import pitt.search.semanticvectors.VectorStoreReader;
import pitt.search.semanticvectors.VectorStoreWriter;

import java.util.LinkedList;

// This class demonstrates use of this package for generating and querying BEAGLE vector stores
public class BeagleTest
{
	// Method for querying a BeagleNGramVectors store.
	public void testQuery(FlagConfig flagConfig, String searchfile, String indexfile, String query )
	{
		VectorSearcher vs;
		LuceneUtils lUtils = null;
		CloseableVectorStore queryVecReader, searchVecReader;
		LinkedList<SearchResult> results;
		int numResults = 20;

		BeagleUtils utils = BeagleUtils.getInstance();
		utils.setFFTCacheSize(100);

		try
		{
			queryVecReader = VectorStoreReader.openVectorStore(indexfile, flagConfig);
			searchVecReader = VectorStoreReader.openVectorStore(searchfile, flagConfig);

			//BeagleCompoundVecBuilder bcb = new BeagleCompoundVecBuilder ();

			String[] queryTerms = query.split(" ");

			// Create VectorSearcher and search for nearest neighbors.
			vs = new BeagleVectorSearcher( queryVecReader, searchVecReader, lUtils, flagConfig, queryTerms);
			System.err.print("Searching term vectors, searchtype BEAGLE ... ");
			queryVecReader.close();
			searchVecReader.close();

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
                                                   ((ObjectVector)result.getObjectVector()).getObject().toString());
			}
		} else {
			System.err.println("No search output.");
		}
	}

	// Method for generating a BeagleNGramVectors store.
	public void createNGrams(String fileOut, FlagConfig flagConfig, int numGrams )
	{
		BeagleNGramVectors bngv;
		BeagleUtils utils = BeagleUtils.getInstance();

		long time;

		try
		{
			time = System.currentTimeMillis();

			bngv = new BeagleNGramVectors(
			    flagConfig, "index", 5, 2, new String[] {"contents"}, numGrams, "stoplist.txt");

			time = System.currentTimeMillis() - time;

			System.out.println("\nTime to process: " + time/1000 + " secs.");
			System.out.println("\nNumber of convolutions: " + utils.getNumConvolutions());

			VectorStoreWriter.writeVectors(
			    fileOut + "_" + flagConfig.dimension() + "_" + numGrams + ".bin", flagConfig, bngv);

			VectorStore indexVectors = bngv.getIndexVectors();
			VectorStoreWriter.writeVectors(
			    fileOut + "_" + flagConfig.dimension() + "_" + numGrams + "_index.bin", flagConfig, indexVectors);

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
	  FlagConfig flagConfig = FlagConfig.getFlagConfig(
	      new String[] {"-vectortype", "real", "-dimension", "512"});

		// Some example method calls
		bt.createNGrams("KJB", flagConfig, 3 );

		bt.testQuery(flagConfig, "KJB_512_3.bin", "KJB_512_3_index.bin", "king ?" );
	}

}
