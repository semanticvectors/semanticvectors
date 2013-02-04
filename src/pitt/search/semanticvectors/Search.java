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

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import pitt.search.semanticvectors.vectors.Vector;

/**
 * Command line term vector search utility. <br/>
 */
public class Search {
  private static final Logger logger = Logger.getLogger(Search.class.getCanonicalName());

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
     * Build a query as with {@link SearchType#SUM} option, but quantize to sparse vectors before
     * taking scalar product at search time. This can be used to give a guide to how
     * much similarities are changed by only using the most significant coordinates
     * of a vector. Also uses {@link VectorSearcher.VectorSearcherCosine}.
     */
    SPARSESUM, 

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

  /** Principal vector store for finding query vectors. */
  private static CloseableVectorStore queryVecReader = null;
  /** Auxiliary vector store used when searching for boundproducts. Used only in some searchtypes. */
  private static CloseableVectorStore boundVecReader = null;
  /** 
   * Vector store for searching. Defaults to being the same as queryVecReader.
   * May be different from queryVecReader, e.g., when using terms to search for documents.
   */
  private static CloseableVectorStore searchVecReader = null;
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
      + "\n-searchtype can be one of SUM, SPARSESUM, SUBSPACE, MAXSIM,"
      + "\n    BALANCED_PERMUTATION, PERMUTATION, PRINTQUERY"
      + "\n<QUERYTERMS> should be a list of words, separated by spaces."
      + "\n    If the term NOT is used, terms after that will be negated.";

  /**
   * Takes a user's query, creates a query vector, and searches a vector store.
   * @param args See usage();
   * @param numResults Number of search results to be returned in a ranked list.
   * @return List containing <code>numResults</code> search results.
   */
  public static List<SearchResult> RunSearch (String[] args, int numResults)
      throws IllegalArgumentException {
    /**
     * The RunSearch function has four main stages:
     * i. Parse command line arguments, with a tiny bit of extra logic for vector stores.
     *    (Ideally this would be done outside this method to make programmatic interfaces clearer.)
     * ii. Open corresponding vector and lucene indexes.
     * iii. Based on search type, build query vector and perform search.
     * iv. Return LinkedList of results, usually for main() to print out.
     */

    // Stage i. Assemble command line options.
    FlagConfig flagConfig = FlagConfig.getFlagConfig(args);
    args = flagConfig.remainingArgs;

    if (flagConfig.numsearchresults() > 0) numResults = flagConfig.numsearchresults();

    // Stage ii. Open vector stores, and Lucene utils.
    try {
      // Default VectorStore implementation is (Lucene) VectorStoreReader.
      VerbatimLogger.info("Opening query vector store from file: " + flagConfig.queryvectorfile() + "\n");
      queryVecReader = VectorStoreReader.openVectorStore(flagConfig.queryvectorfile(), flagConfig);

      if (flagConfig.boundvectorfile().length() > 0) {
        VerbatimLogger.info("Opening second query vector store from file: " + flagConfig.boundvectorfile() + "\n");
        boundVecReader = VectorStoreReader.openVectorStore(flagConfig.boundvectorfile(), flagConfig);
      }

      // Open second vector store if search vectors are different from query vectors.
      if (flagConfig.queryvectorfile().equals(flagConfig.searchvectorfile())
          || flagConfig.searchvectorfile().isEmpty()) {
        searchVecReader = queryVecReader;
      } else {
        VerbatimLogger.info("Opening search vector store from file: " + flagConfig.searchvectorfile() + "\n");
        searchVecReader = VectorStoreReader.openVectorStore(flagConfig.searchvectorfile(), flagConfig);
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

    // This takes the slice of args from argc to end.
    if (!flagConfig.matchcase()) {
      for (int i = 0; i < args.length; ++i) {
        args[i] = args[i].toLowerCase();
      }
    }

    // Stage iii. Perform search according to which searchType was selected.
    // Most options have corresponding dedicated VectorSearcher subclasses.
    VectorSearcher vecSearcher = null;
    LinkedList<SearchResult> results = new LinkedList<SearchResult>();
    VerbatimLogger.info("Searching term vectors, searchtype " + flagConfig.searchtype() + "\n");

    try {
      switch (flagConfig.searchtype()) {
      case SUM:
        vecSearcher = new VectorSearcher.VectorSearcherCosine(
            queryVecReader, searchVecReader, luceneUtils, flagConfig, args);
        break;
      case SUBSPACE:    
        vecSearcher = new VectorSearcher.VectorSearcherSubspaceSim(
            queryVecReader, searchVecReader, luceneUtils, flagConfig, args);
        break;
      case MAXSIM:
        vecSearcher = new VectorSearcher.VectorSearcherMaxSim(
            queryVecReader, searchVecReader, luceneUtils, flagConfig, args);
        break;
      case BOUNDPRODUCT:
        if (args.length == 2) {
          vecSearcher = new VectorSearcher.VectorSearcherBoundProduct(
              queryVecReader, boundVecReader, searchVecReader, luceneUtils, flagConfig, args[0],args[1]);
        } else {
          vecSearcher = new VectorSearcher.VectorSearcherBoundProduct(
              queryVecReader, boundVecReader, searchVecReader, luceneUtils, flagConfig, args[0]);
        }
        break;
      case BOUNDPRODUCTSUBSPACE:
        if (args.length == 2)
        {
          vecSearcher = new VectorSearcher.VectorSearcherBoundProductSubSpace(
              queryVecReader, boundVecReader, searchVecReader, luceneUtils, flagConfig, args[0],args[1]);
        } else {
          vecSearcher = new VectorSearcher.VectorSearcherBoundProductSubSpace(
              queryVecReader, boundVecReader, searchVecReader, luceneUtils, flagConfig, args[0]);
        }
        break;
      case PERMUTATION:
        vecSearcher = new VectorSearcher.VectorSearcherPerm(
            queryVecReader, searchVecReader, luceneUtils, flagConfig, args);
        break;
      case BALANCEDPERMUTATION:
        vecSearcher = new VectorSearcher.BalancedVectorSearcherPerm(
            queryVecReader, searchVecReader, luceneUtils, flagConfig, args);
        break;
      case ANALOGY:
        vecSearcher = new VectorSearcher.AnalogySearcher(
            queryVecReader, searchVecReader, luceneUtils, flagConfig, args);
        break;
      case PRINTQUERY:    
        Vector queryVector = CompoundVectorBuilder.getQueryVector(
            queryVecReader, luceneUtils, flagConfig, args);
        System.out.println(queryVector.toString());
        return new LinkedList<SearchResult>();
      default:
        throw new IllegalArgumentException("Unknown search type: " + flagConfig.searchtype());
      }
    } catch (ZeroVectorException zve) {
      logger.info(zve.getMessage());
      results = new LinkedList<SearchResult>();
    }

    results = vecSearcher.getNearestNeighbors(numResults);

    // Release filesystem resources.
    //
    // TODO(widdows): This is not the cleanest control flow, since these are
    // opened in RunSearch but also needed in getSearchResultsVectors.
    // Really there should be a global variable for indexformat (text
    // or lucene), and general "openIndexes" and "closeIndexes" methods.
    queryVecReader.close();
    if (!(searchVecReader == queryVecReader)) {
      searchVecReader.close();
    }
    if (boundVecReader != null) {
      boundVecReader.close();
    }

    return results;
  }

  /**
   * Search wrapper that returns the list of ObjectVectors.
   */
  public static ObjectVector[] getSearchResultVectors(FlagConfig flagConfig, String[] args, int numResults)
      throws IllegalArgumentException {
    List<SearchResult> results = Search.RunSearch(args, numResults);

    try {
      searchVecReader = VectorStoreReader.openVectorStore(flagConfig.searchvectorfile(), flagConfig);
    } catch (IOException e) {
      e.printStackTrace();
    }
    ObjectVector[] resultsList = new ObjectVector[results.size()];
    for (int i = 0; i < results.size(); ++i) {
      String term = ((ObjectVector)results.get(i).getObjectVector()).getObject().toString();
      Vector tmpVector = searchVecReader.getVector(term);
      resultsList[i] = new ObjectVector(term, tmpVector);
    }
    searchVecReader.close();
    return resultsList;
  }

  /**
   * Takes a user's query, creates a query vector, and searches a vector store.
   * @param args See {@link #usageMessage}
   */
  public static void main (String[] args) throws IllegalArgumentException {
    int defaultNumResults = 20;
    List<SearchResult> results = RunSearch(args, defaultNumResults);
    // Print out results.
    if (results.size() > 0) {
      VerbatimLogger.info("Search output follows ...\n");
      for (SearchResult result: results) {
        System.out.println(result.getScore() + ":" +
            ((ObjectVector)result.getObjectVector()).getObject().toString());

      }
    } else {
      VerbatimLogger.info("No search output.\n");
    }
  }
}
