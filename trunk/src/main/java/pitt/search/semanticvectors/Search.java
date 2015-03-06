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
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import pitt.search.semanticvectors.ElementalVectorStore.ElementalGenerationMethod;
import pitt.search.semanticvectors.utils.PsiUtils;
import pitt.search.semanticvectors.utils.VerbatimLogger;
import pitt.search.semanticvectors.vectors.BinaryVector;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorType;
import pitt.search.semanticvectors.vectors.ZeroVectorException;
import pitt.search.semanticvectors.viz.PathFinder;

/**
 * Command line term vector search utility.
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
     * Uses intersection between supplied predicate_concept combinations
     * Implemented for binary vectors only currently
     * Uses {@link VectorSearcher.VectorSearcherBoundProductSubSpace}
     */
    INTERSECTION,

    /**
     * Intended to support searches of the form A is to B as C is to ?, but
     * hasn't worked well thus far. (dwiddows, 2013-02-03).
     */
    ANALOGY,

    /**
     * Builds an additive query vector (as with {@link SearchType#SUM} and prints out the query
     * vector for debugging).
     */
    PRINTQUERY,
    

    /**
     * Builds an additive query vector (as with {@link SearchType#SUM} and prints out the query
     * vector for debugging).
     */
    PRINTPSIQUERY, 
    
    /**
     * Looks for documents in which the terms passed as parameters occur in proximity. Requires document vectors
     * built with graded vectors, as in pitt.search.orthography.SentenceVectors
     * 
     */
    
    
    PROXIMITY
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
  public static List<SearchResult> runSearch(FlagConfig flagConfig)
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
    CloseableVectorStore queryVecReader = null;
    /** Auxiliary vector store used when searching for boundproducts. Used only in some searchtypes. */
    CloseableVectorStore boundVecReader = null;

    /** Auxiliary vector stores used when searching for boundproducts. Used only in some searchtypes. */
    CloseableVectorStore elementalVecReader = null, semanticVecReader = null, predicateVecReader = null;

    /**
     * Vector store for searching. Defaults to being the same as queryVecReader.
     * May be different from queryVecReader, e.g., when using terms to search for documents.
     */
    CloseableVectorStore searchVecReader = null;

    // Stage ii. Open vector stores, and Lucene utils.
    try {
      // Default VectorStore implementation is (Lucene) VectorStoreReader.
      if (!flagConfig.elementalvectorfile().equals("elementalvectors") && !flagConfig.semanticvectorfile().equals("semanticvectors") && !flagConfig.predicatevectorfile().equals("predicatevectors")) {
        //for PSI search

        VerbatimLogger.info("Opening query vector store from file: " + flagConfig.queryvectorfile() + "\n");
        if (flagConfig.elementalvectorfile().equals("deterministic"))
        {
          if (flagConfig.elementalmethod().equals(ElementalGenerationMethod.ORTHOGRAPHIC)) elementalVecReader = new VectorStoreOrthographical(flagConfig);
          else if (flagConfig.elementalmethod().equals(ElementalGenerationMethod.CONTENTHASH)) elementalVecReader = new VectorStoreDeterministic(flagConfig);
          else VerbatimLogger.info("Please select either -elementalmethod orthographic OR -elementalmethod contenthash depending upon the deterministic approach you would like used.");
        }
        else elementalVecReader = VectorStoreReader.openVectorStore(flagConfig.elementalvectorfile(), flagConfig);

        VerbatimLogger.info("Opening elemental query vector store from file: " + flagConfig.elementalvectorfile() + "\n");
        VerbatimLogger.info("Opening semantic query vector store from file: " + flagConfig.semanticvectorfile() + "\n");
        VerbatimLogger.info("Opening predicate query vector store from file: " + flagConfig.predicatevectorfile() + "\n");

        semanticVecReader = VectorStoreReader.openVectorStore(flagConfig.semanticvectorfile(), flagConfig);
        predicateVecReader = VectorStoreReader.openVectorStore(flagConfig.predicatevectorfile(), flagConfig);
      }
      else {
        VerbatimLogger.info("Opening query vector store from file: " + flagConfig.queryvectorfile() + "\n");
        if (flagConfig.queryvectorfile().equals("deterministic")) {
          if (flagConfig.elementalmethod().equals(ElementalGenerationMethod.ORTHOGRAPHIC)) queryVecReader = new VectorStoreOrthographical(flagConfig);
          else if (flagConfig.elementalmethod().equals(ElementalGenerationMethod.CONTENTHASH)) queryVecReader = new VectorStoreDeterministic(flagConfig);
          else VerbatimLogger.info("Please select either -elementalmethod orthographic OR -elementalmethod contenthash depending upon the deterministic approach you would like used.");
        }
        else queryVecReader = VectorStoreReader.openVectorStore(flagConfig.queryvectorfile(), flagConfig);
      }

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
      for (int i = 0; i < queryArgs.length; ++i) {
        queryArgs[i] = queryArgs[i].toLowerCase();
      }
    }

    // Stage iii. Perform search according to which searchType was selected.
    // Most options have corresponding dedicated VectorSearcher subclasses.
    VectorSearcher vecSearcher;
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
        case INTERSECTION:
           {
              vecSearcher = new VectorSearcher.VectorSearcherIntersection(
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
        case PROXIMITY:
            vecSearcher = new VectorSearcher.VectorSearcherProximity(
                queryVecReader, searchVecReader, boundVecReader, luceneUtils, flagConfig, queryArgs);
            break;
        case LUCENE:
          vecSearcher = new VectorSearcher.VectorSearcherLucene(
              luceneUtils, flagConfig, queryArgs);
          break;
        case PRINTQUERY:
          Vector queryVector = CompoundVectorBuilder.getQueryVector(
              queryVecReader, luceneUtils, flagConfig, queryArgs);
          System.out.println(queryVector.toString());
          return new LinkedList<>();
        case PRINTPSIQUERY:
          Vector psiQueryVector = CompoundVectorBuilder.getBoundProductQueryVectorFromString(flagConfig, elementalVecReader, semanticVecReader, predicateVecReader, luceneUtils, queryArgs[0]);
          	if (flagConfig.vectortype().equals(VectorType.BINARY))
          		BinaryVector.setDebugPrintLength(flagConfig.dimension());
          System.out.println(psiQueryVector.toString());
            return new LinkedList<>();
        default:
          throw new IllegalArgumentException("Unknown search type: " + flagConfig.searchtype());
      }
    } catch (ZeroVectorException zve) {
      logger.info(zve.getMessage());
      return new LinkedList<>();
    }

    results = vecSearcher.getNearestNeighbors(flagConfig.numsearchresults());

    // Optional: Release filesystem resources. Temporarily removed because of errors in
    // ThreadSafetyTest.
    //
    // This was not the cleanest control flow anyway, since these are
    // opened in runSearch but also needed in getSearchResultsVectors.
    // Really there should be a global variable for indexformat (text
    // or lucene), and general "openIndexes" and "closeIndexes" methods.
    if (queryVecReader != null) {
      queryVecReader.close();
    }
    if (searchVecReader != null) {
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
  public static ObjectVector[] getSearchResultVectors(FlagConfig flagConfig)
      throws IllegalArgumentException {
    List<SearchResult> results = Search.runSearch(flagConfig);

    CloseableVectorStore searchVecReader = null;
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
   * @throws IllegalArgumentException
   * @throws IOException if filesystem resources referred to in arguments are unavailable
   */
  public static void main (String[] args) throws IllegalArgumentException, IOException {
    FlagConfig flagConfig;
    List<SearchResult> results;
    try {
      flagConfig = FlagConfig.getFlagConfig(args);
      results = runSearch(flagConfig);
    } catch (IllegalArgumentException e) {
      System.err.println(usageMessage);
      throw e;
    }

    // Print out results.
    int ranking = 0;
    if (results.size() > 0) {
      VerbatimLogger.info("Search output follows ...\n");
      for (SearchResult result: results) {
        ++ranking;
        if (flagConfig.treceval() == -1) {
          System.out.println(result.toSimpleString());
        } else {
          System.out.println(result.toTrecString(flagConfig.treceval(), ranking));
        }

        if (flagConfig.boundvectorfile().isEmpty() && flagConfig.elementalvectorfile().isEmpty()) {
          PsiUtils.printNearestPredicate(flagConfig);
        }
      }

      if (!flagConfig.jsonfile().isEmpty()) {
        PathFinder.pathfinderWriterWrapper(flagConfig, results);
      }
    } else {
      VerbatimLogger.info("No search output.\n");
    }
  }
}
