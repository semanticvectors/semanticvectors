/**
   Copyright (c) 2007, University of Pittsburgh

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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;
import org.apache.lucene.util.Version;

import pitt.search.semanticvectors.ElementalVectorStore.ElementalGenerationMethod;
import pitt.search.semanticvectors.utils.VerbatimLogger;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.ZeroVectorException;
import pitt.search.semanticvectors.viz.PathFinder;

/**
 * Command line term vector search utility.
 */
public class SearchBatch {
  private static final Logger logger = Logger.getLogger(SearchBatch.class.getCanonicalName());

  /**
   *  Different types of searches that can be performed, set using {@link FlagConfig#searchtype()}.
   *  
   * <p>Most involve processing combinations of vectors in different ways, in
   * building a query expression, scoring candidates against these query
   * expressions, or both. Most options here correspond directly to a particular
   * subclass of {@link VectorSearcher}.
   * 
   * <p>Names may be passed as command-line arguments, so underscores are avoided.
   * */
  public enum SearchType {
    /**
     * Build a query by adding together (weighted) vectors for each of the query
     * terms, and search using cosine similarity.
     * See {@link VectorSearcher.VectorSearcherCosine}.
     * This is the default search option.
     */
    SUM,

    /** 
     * "Quantum disjunction" - get vectors for each query term, create a
     * representation for the subspace spanned by these vectors, and score by
     * measuring cosine similarity with this subspace.
     * Uses {@link VectorSearcher.VectorSearcherSubspaceSim}.
     */
    SUBSPACE,

    /**    
     * "Closest disjunction" - get vectors for each query term, score by measuring
     * distance to each term and taking the minimum.
     * Uses {@link VectorSearcher.VectorSearcherMaxSim}.
     */
    MAXSIM,

    /**
     * "Farthest conjunction" - get vectors for each query term, score by measuring
     * distance to each term and taking the maximum.
     * Uses {@link VectorSearcher.VectorSearcherMaxSim}.
        */
    MINSIM,

    /**
     * Uses permutation of coordinates to model typed relationships, as
     * introduced by Sahlgren at al. (2008).
     * 
     * <p>Searches for the term that best matches the position of a "?" in a sequence of terms.
     * For example <code>martin ? king</code> should retrieve <code>luther</code> as the top ranked match. 
     * 
     * <p>Requires {@link FlagConfig#queryvectorfile()} to contain
     * unpermuted vectors, either random vectors or previously learned term vectors,
     * and {@link FlagConfig#searchvectorfile()} must contain permuted learned vectors.
     * 
     * <p>Uses {@link VectorSearcher.VectorSearcherPerm}.
     */
    PERMUTATION,

    /**
     * This is a variant of the {@link SearchType#PERMUTATION} method which 
     * takes the mean of the two possible search directions (search
     * with index vectors for permuted vectors, or vice versa).
     * Uses {@link VectorSearcher.VectorSearcherPerm}.
     */
    BALANCEDPERMUTATION,

    /**
     * Used for Predication Semantic Indexing, see {@link PSI}.
     * Uses {@link VectorSearcher.VectorSearcherBoundProduct}.
     */
    BOUNDPRODUCT,
    
    /**
     * Lucene document search, for comparison.
     * 
     */
    
    LUCENE,
    
    /**
     * Used for Predication Semantic Indexing, see {@link PSI}.
     * Finds minimum similarity across query terms to seek middle terms
     */
    
    BOUNDMINIMUM,

    /**
     * Binds vectors to facilitate search across multiple relationship paths.
     * Uses {@link VectorSearcher.VectorSearcherBoundProductSubSpace}
     */
    BOUNDPRODUCTSUBSPACE,

    /**
     * Intended to support searches of the form A is to B as C is to ?, but 
     * hasn't worked well thus far. (dwiddows, 2013-02-03).
     */
    ANALOGY,

    /** 
     * Builds an additive query vector (as with {@link SearchType#SUM} and prints out the query
     * vector for debugging).
     */
    PRINTQUERY
  }

  private static LuceneUtils luceneUtils;

  public static String usageMessage = "\nSearch class in package pitt.search.semanticvectors"
      + "\nUsage: java pitt.search.semanticvectors.Search [-queryvectorfile query_vector_file]"
      + "\n                                               [-searchvectorfile search_vector_file]"
      + "\n                                               [-luceneindexpath path_to_lucene_index]"
      + "\n                                               [-searchtype TYPE]"
      + "\n                                               <QUERYTERMS>"
      + "\nIf no query or search file is given, default will be"
      + "\n    termvectors.bin in local directory."
      + "\n-luceneindexpath argument is needed if to get term weights from"
      + "\n    term frequency, doc frequency, etc. in lucene index."
      + "\n-searchtype can be one of SUM, SUBSPACE, MAXSIM, MINSIM"
      + "\n    BALANCEDPERMUTATION, PERMUTATION, PRINTQUERY"
      + "\n<QUERYTERMS> should be a list of words, separated by spaces."
      + "\n    If the term NOT is used, terms after that will be negated.";

  /**
   * Takes a user's query, creates a query vector, and searches a vector store.
   * @param flagConfig configuration object for controlling the search
   * @return list containing search results.
   */
  public static void runSearch(FlagConfig flagConfig)
      throws IllegalArgumentException {
    /**
     * The runSearch function has four main stages:
     * i. Check flagConfig for null (but so far fails to check other dependencies).
     * ii. Open corresponding vector and lucene indexes.
     * iii. Based on search type, build query vector and perform search.
     * iv. Return LinkedList of results, usually for main() to print out.
     */
    // Stage i. Check flagConfig for null, and there being at least some remaining query terms.
    if (flagConfig == null) {
      throw new NullPointerException("flagConfig cannot be null");
    }
    if (flagConfig.remainingArgs == null) {
      throw new IllegalArgumentException("No query terms left after flag parsing!");
    }

    String[] queryArgs = flagConfig.remainingArgs;

    /** Principal vector store for finding query vectors. */
    VectorStore queryVecReader = null;
    /** Auxiliary vector store used when searching for boundproducts. Used only in some searchtypes. */
    VectorStore boundVecReader = null;
    
    /** Auxiliary vector stores used when searching for boundproducts. Used only in some searchtypes. */
    VectorStore elementalVecReader = null, semanticVecReader = null, predicateVecReader = null;
    
    /**
     * Vector store for searching. Defaults to being the same as queryVecReader.
     * May be different from queryVecReader, e.g., when using terms to search for documents.
     */
    VectorStore searchVecReader = null;

    // Stage ii. Open vector stores, and Lucene utils.
    try {
      // Default VectorStore implementation is (Lucene) VectorStoreReader.
      
        if (!flagConfig.elementalvectorfile().equals("elementalvectors") && !flagConfig.semanticvectorfile().equals("semanticvectors") && !flagConfig.predicatevectorfile().equals("predicatevectors")) {
            //for PSI search
        	
  
        	VerbatimLogger.info("Opening elemental query vector store from file: " + flagConfig.elementalvectorfile() + "\n");
            VerbatimLogger.info("Opening semantic query vector store from file: " + flagConfig.semanticvectorfile() + "\n");
            VerbatimLogger.info("Opening predicate query vector store from file: " + flagConfig.predicatevectorfile() + "\n");
            
            if (flagConfig.elementalvectorfile().equals("deterministic")) 
    		{
    			if (flagConfig.elementalmethod().equals(ElementalGenerationMethod.ORTHOGRAPHIC)) elementalVecReader = new VectorStoreOrthographical(flagConfig);
    			else if (flagConfig.elementalmethod().equals(ElementalGenerationMethod.CONTENTHASH)) elementalVecReader = new VectorStoreDeterministic(flagConfig);
    			else VerbatimLogger.info("Please select either -elementalmethod orthographic OR -elementalmethod contenthash depending upon the deterministic approach you would like used.");
    		} else
            {elementalVecReader = new VectorStoreRAM(flagConfig);
            ((VectorStoreRAM) elementalVecReader).initFromFile(flagConfig.elementalvectorfile());}
             semanticVecReader = new VectorStoreRAM(flagConfig);
             ((VectorStoreRAM) semanticVecReader).initFromFile(flagConfig.semanticvectorfile());
            predicateVecReader = new VectorStoreRAM(flagConfig);
            ((VectorStoreRAM) predicateVecReader).initFromFile(flagConfig.predicatevectorfile());
            
        	}	
        	else
        	{
        	VerbatimLogger.info("Opening query vector store from file: " + flagConfig.queryvectorfile() + "\n");
        	if (flagConfig.queryvectorfile().equals("deterministic")) 
        		{
        			if (flagConfig.elementalmethod().equals(ElementalGenerationMethod.ORTHOGRAPHIC)) queryVecReader = new VectorStoreOrthographical(flagConfig);
        			else if (flagConfig.elementalmethod().equals(ElementalGenerationMethod.CONTENTHASH)) queryVecReader = new VectorStoreDeterministic(flagConfig);
        			else VerbatimLogger.info("Please select either -elementalmethod orthographic OR -elementalmethod contenthash depending upon the deterministic approach you would like used.");
        		}
        		else 
        		{queryVecReader = new VectorStoreRAM(flagConfig);
        	((VectorStoreRAM) queryVecReader).initFromFile(flagConfig.queryvectorfile());
        		}
        	}
      
      if (flagConfig.boundvectorfile().length() > 0) {
        VerbatimLogger.info("Opening second query vector store from file: " + flagConfig.boundvectorfile() + "\n");
        boundVecReader = new VectorStoreRAM(flagConfig);
        ((VectorStoreRAM) boundVecReader).initFromFile(flagConfig.boundvectorfile());
   
      }


      
      
      // Open second vector store if search vectors are different from query vectors.
      if (flagConfig.queryvectorfile().equals(flagConfig.searchvectorfile())
          || flagConfig.searchvectorfile().isEmpty()) {
        searchVecReader = queryVecReader;
      } else {
        VerbatimLogger.info("Opening search vector store from file: " + flagConfig.searchvectorfile() + "\n");
        searchVecReader = new VectorStoreRAM(flagConfig);
        ((VectorStoreRAM) searchVecReader).initFromFile(flagConfig.searchvectorfile());

      }

      if (!flagConfig.luceneindexpath().isEmpty()) {
        try {
          luceneUtils = new LuceneUtils(flagConfig);
        } catch (IOException e) {
          logger.warning("Couldn't open Lucene index at " + flagConfig.luceneindexpath()
              + ". Will continue without term weighting.");
        }
      }
    }
    catch (IOException e) {
      e.printStackTrace();
    }

    try {
		BufferedReader queryReader = new BufferedReader(new FileReader(new File(queryArgs[0])));
		String queryString = queryReader.readLine();
		int qcnt = 0;
		
	while (queryString != null)
	{
		ArrayList<String> queryTerms = new ArrayList<String>();
		qcnt++;
		
		//have Lucene parse the query string, for consistency
		StandardAnalyzer  analyzer = new StandardAnalyzer(Version.LUCENE_46);
		TokenStream stream = analyzer.tokenStream(null, new StringReader(queryString));
		CharTermAttribute cattr = stream.addAttribute(CharTermAttribute.class);
		stream.reset();
		
		//for each token in the query string
		while (stream.incrementToken()) 
		{
		
			String term = cattr.toString();
			
			if (!luceneUtils.stoplistContains(term)) 
			{
				if (! flagConfig.matchcase()) term = term.toLowerCase();
				queryTerms.add(term);
			}
		}
		stream.end();
		stream.close();
		analyzer.close();
	 
		//transform to String[] array
		queryArgs = queryTerms.toArray(new String[0]);
		
    // Stage iii. Perform search according to which searchType was selected.
    // Most options have corresponding dedicated VectorSearcher subclasses.
    VectorSearcher vecSearcher = null;
    LinkedList<SearchResult> results;
    VerbatimLogger.info("Searching term vectors, searchtype " + flagConfig.searchtype() + "\n");

    try {
      switch (flagConfig.searchtype()) {
      case SUM:
        vecSearcher = new VectorSearcher.VectorSearcherCosine(
            queryVecReader, searchVecReader, luceneUtils, flagConfig, queryArgs);
        break;
      case SUBSPACE:    
        vecSearcher = new VectorSearcher.VectorSearcherSubspaceSim(
            queryVecReader, searchVecReader, luceneUtils, flagConfig, queryArgs);
        break;
      case MAXSIM:
        vecSearcher = new VectorSearcher.VectorSearcherMaxSim(
            queryVecReader, searchVecReader, luceneUtils, flagConfig, queryArgs);
        break;
      case MINSIM:
          vecSearcher = new VectorSearcher.VectorSearcherMinSim(
              queryVecReader, searchVecReader, luceneUtils, flagConfig, queryArgs);
          break;
      case BOUNDPRODUCT:
        if (queryArgs.length == 2) {
          vecSearcher = new VectorSearcher.VectorSearcherBoundProduct(
              queryVecReader, boundVecReader, searchVecReader, luceneUtils, flagConfig, queryArgs[0],queryArgs[1]);
        } else {
          vecSearcher = new VectorSearcher.VectorSearcherBoundProduct(
              elementalVecReader, semanticVecReader, predicateVecReader, searchVecReader, luceneUtils, flagConfig, queryArgs[0]);
        }
        break;
      case BOUNDPRODUCTSUBSPACE:
        if (queryArgs.length == 2) {
          vecSearcher = new VectorSearcher.VectorSearcherBoundProductSubSpace(
              queryVecReader, boundVecReader, searchVecReader, luceneUtils, flagConfig, queryArgs[0], queryArgs[1]);
        } else {
          vecSearcher = new VectorSearcher.VectorSearcherBoundProductSubSpace(
              elementalVecReader, semanticVecReader, predicateVecReader, searchVecReader, luceneUtils, flagConfig, queryArgs[0]);
        }
        break;
      case BOUNDMINIMUM:
          if (queryArgs.length == 2) {
            vecSearcher = new VectorSearcher.VectorSearcherBoundMinimum(
                queryVecReader, boundVecReader, searchVecReader, luceneUtils, flagConfig, queryArgs[0], queryArgs[1]);
          } else {
            vecSearcher = new VectorSearcher.VectorSearcherBoundMinimum(
                elementalVecReader, semanticVecReader, predicateVecReader, searchVecReader, luceneUtils, flagConfig, queryArgs[0]);
          }
          break;
      case PERMUTATION:
        vecSearcher = new VectorSearcher.VectorSearcherPerm(
            queryVecReader, searchVecReader, luceneUtils, flagConfig, queryArgs);
        break;
      case BALANCEDPERMUTATION:
        vecSearcher = new VectorSearcher.BalancedVectorSearcherPerm(
            queryVecReader, searchVecReader, luceneUtils, flagConfig, queryArgs);
        break;
      case ANALOGY:
        vecSearcher = new VectorSearcher.AnalogySearcher(
            queryVecReader, searchVecReader, luceneUtils, flagConfig, queryArgs);
        break;
      case LUCENE:
          vecSearcher = new VectorSearcher.VectorSearcherLucene(
              luceneUtils, flagConfig, queryArgs);
          break;
      case PRINTQUERY:    
        Vector queryVector = CompoundVectorBuilder.getQueryVector(
            queryVecReader, luceneUtils, flagConfig, queryArgs);
        System.out.println(queryVector.toString());
      default:
        throw new IllegalArgumentException("Unknown search type: " + flagConfig.searchtype());
      }
    } catch (ZeroVectorException zve) {
      logger.info(zve.getMessage());
        }

    results = new LinkedList<SearchResult>();
    
    try {
    results = vecSearcher.getNearestNeighbors(flagConfig.numsearchresults());
    	}
    catch (Exception e)
    	{
    	//no search results returned
    	}
    
    int cnt = 0;
    // Print out results.
    if (results.size() > 0) {
      VerbatimLogger.info("Search output follows ...\n");
      for (SearchResult result: results) {
    	  
    	  if (flagConfig.treceval() != -1) //results in trec_eval format
    	  {  	System.out.println(
    		  			String.format("%s\t%s\t%s\t%s\t%f\t%s",
    		  				qcnt,
    		  				"Q0",
    		  				result.getObjectVector().getObject().toString(),
    		                ++cnt,
    		  			 	result.getScore(),
    		  			 	"DEFAULT")
    		  			 	 );
    	  }
    	  else System.out.println(  //results in cosine:object format
    			  		String.format("%f:%s",
    			  			result.getScore(),
    			  			result.getObjectVector().getObject().toString()));
      									}
    }
   queryString = queryReader.readLine();
    
	}
    queryReader.close();
    }
    catch (FileNotFoundException e1) {
 		// TODO Auto-generated catch block
 		e1.printStackTrace();
 	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
   

  

  }



  /**
   * Takes a user's query, creates a query vector, and searches a vector store.
   * @param args See {@link #usageMessage}
   * @throws IllegalArgumentException
   * @throws IOException if filesystem resources referred to in arguments are unavailable
   */
  public static void main (String[] args) throws IllegalArgumentException, IOException {
    FlagConfig flagConfig;
   
    try {
      flagConfig = FlagConfig.getFlagConfig(args);
      runSearch(flagConfig);
    } catch (IllegalArgumentException e) {
      System.err.println(usageMessage);
      throw e;
    }
    

        
     
  }
}
