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
 * Command line term vector search utility.
 *
 * @see VectorSearcher
 * Here is a list of different types of searches that can be
 * performed. Most involve processing combinations of vectors in
 * different ways, in building a query expression, scoring candidates
 * against these query expressions, or both. Most options here
 * correspond directly to a particular subclass of
 * <code>VectorSearcher</code>
 *
 * The search option is set using the --searchtype flag. Options include:
 *
 * @see VectorSearcher.VectorSearcherCosine
 * <br/> <b>sum</b>:
 * Default option - build a query by adding together (weighted)
 * vectors for each of the query terms, and search using cosine
 * similarity.
 *
 * <br/> <b>sparsesum</b>:
 * Build a query as with <code>SUM</code> option, but quantize to
 * sparse vectors before taking scalar product at search time.
 * This can be used to give a guide to how much similarities are
 * changed by only using the most significant coordinates of a
 * vector.
 *
 * @see VectorSearcher.VectorSearcherSubspaceSim
 * <br/> <b>subspace</b>:
 * "Quantum disjunction" - get vectors for each query term, create a
 * representation for the subspace spanned by these vectors, and
 * score by measuring cosine similarity with this subspace.
 *
 * @see VectorSearcher.VectorSearcherMaxSim
 * <br/><b>maxsim</b>:
 * "Closest disjunction" - get vectors for each query term, score
 * by measuring distance to each term and taking the minimum.
 *
 * @see VectorSearcher.VectorSearcherPerm
 * <br/><b>permutation</b>
 * Based on Sahlgren at al. (2008). Searches for the term that best matches
 * the position of a "?" in a sequence of terms. For example
 * 'martin ? king' should retrieve luther as the top ranked match
 * requires the index queried to contain unpermuted vectors, either
 * random vectors or previously learned term vectors, and the index searched must contain
 * permuted vectors.
 *
 * <br/><b>balanced_permutation</b>
 * Based on Sahlgren at al. (2008). Searches for the term that best matches
 * the position of a "?" in a sequence of terms. For example
 * 'martin ? king' should retrieve luther as the top ranked match
 * requires the index queried to contain unpermuted vectors, either
 * random vectors or previously learned term vectors, and the index searched must contain
 * permuted vectors. This is a variant of the method, that takes the mean
 * of the two possible search directions (search with index vectors for permuted vectors,
 * or vice versa).
 *
 * <br/><b>printquery</b>
 * Build an additive query vector (as with <code>SUM</code> and
 * print out the query vector for debugging.
 */
public class Search {
  private static final Logger logger = Logger.getLogger(Search.class.getCanonicalName());

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
      + "\n     CONVOLUTION, BALANCED_PERMUTATION, PERMUTATION, PRINTQUERY"
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
    args = Flags.parseCommandLineFlags(args);

    if (Flags.numsearchresults > 0) numResults = Flags.numsearchresults;

    // If Flags.searchvectorfile wasn't set, it defaults to Flags.queryvectorfile.
    if (Flags.searchvectorfile.equals("")) {
      Flags.searchvectorfile = Flags.queryvectorfile;
    }

    // Stage ii. Open vector stores, and Lucene utils.
    try {
      // Default VectorStore implementation is (Lucene) VectorStoreReader.
      VerbatimLogger.info("Opening query vector store from file: " + Flags.queryvectorfile + "\n");
      queryVecReader = VectorStoreReader.openVectorStore(Flags.queryvectorfile);

      if (Flags.boundvectorfile.length() > 0) {
        VerbatimLogger.info("Opening second query vector store from file: " + Flags.boundvectorfile + "\n");
        boundVecReader = VectorStoreReader.openVectorStore(Flags.boundvectorfile);
      }

      // Open second vector store if search vectors are different from query vectors.
      if (Flags.queryvectorfile.equals(Flags.searchvectorfile)) {
        searchVecReader = queryVecReader;
      } else {
        VerbatimLogger.info("Opening search vector store from file: " + Flags.searchvectorfile + "\n");
        searchVecReader = VectorStoreReader.openVectorStore(Flags.searchvectorfile);
      }

      if (Flags.luceneindexpath != "") {
        try { luceneUtils = new LuceneUtils(Flags.luceneindexpath); }
        catch (IOException e) {
          logger.warning("Couldn't open Lucene index at " + Flags.luceneindexpath
              + ". Will continue without term weighting.");
        }
      }
    }
    catch (IOException e) {
      e.printStackTrace();
    }

    // This takes the slice of args from argc to end.
    if (!Flags.matchcase) {
      for (int i = 0; i < args.length; ++i) {
        args[i] = args[i].toLowerCase();
      }
    }

    // Stage iii. Perform search according to which searchType was selected.
    // Most options have corresponding dedicated VectorSearcher subclasses.
    VectorSearcher vecSearcher = null;
    LinkedList<SearchResult> results = new LinkedList<SearchResult>();
    VerbatimLogger.info("Searching term vectors, searchtype " + Flags.searchtype + "\n");

    // The following is essentially a big "switch" statement on Flags.searchtype that creates the
    // appropriate VectorSearcher and throws a ZeroVectorException if it doesn't work.
    try {
      if (Flags.searchtype.equals("sum")) {
        vecSearcher = new VectorSearcher.VectorSearcherCosine(
            queryVecReader, searchVecReader, luceneUtils, args);
      } else if (Flags.searchtype.equals("subspace")) {
        // Quantum disjunction / subspace similarity.
        vecSearcher = new VectorSearcher.VectorSearcherSubspaceSim(
            queryVecReader, searchVecReader, luceneUtils, args);
      } else if (Flags.searchtype.equals("maxsim")) {
        // Ranks by maximum similarity with any of the query terms.
        vecSearcher = new VectorSearcher.VectorSearcherMaxSim(
            queryVecReader, searchVecReader, luceneUtils, args);
      } else if (Flags.searchtype.equals("boundproduct")) {
        if (args.length == 2) {
          // Binds vectors to faciliate search across specific relations
          vecSearcher = new VectorSearcher.VectorSearcherBoundProduct(
              queryVecReader, boundVecReader, searchVecReader, luceneUtils, args[0],args[1]);
        } else {
          // Binds vectors to faciliate search across specific relations
          vecSearcher = new VectorSearcher.VectorSearcherBoundProduct(
              queryVecReader, boundVecReader, searchVecReader, luceneUtils, args[0]);
        }
      } else if (Flags.searchtype.equals("boundproductsubspace")) {
        // Binds vectors to faciliate search across multiple relationship paths
        vecSearcher = new VectorSearcher.VectorSearcherBoundProductSubSpace(
            queryVecReader, boundVecReader, searchVecReader, luceneUtils, args[0],args[1]);
      } else if (Flags.searchtype.equals("permutation")) {
        // Permutes query vectors such that the most likely term in the position of the "?" is retrieved.
        vecSearcher = new VectorSearcher.VectorSearcherPerm(
            queryVecReader, searchVecReader, luceneUtils, args);
      } else if (Flags.searchtype.equals("balanced_permutation")) {
        // Permutes query vectors such that the most likely term in the position of the "?" is retrieved
        vecSearcher = new VectorSearcher.BalancedVectorSearcherPerm(
            queryVecReader, searchVecReader, luceneUtils, args);
      } else if (Flags.searchtype.equals("analogy")) {
        // Try to find best filler for slot in a is to b as c is to ?
        vecSearcher = new VectorSearcher.AnalogySearcher(
            queryVecReader, searchVecReader, luceneUtils, args);
      } else if (Flags.searchtype.equals("printquery")) {
        // Simply prints out the query vector: doesn't do any searching.
        Vector queryVector = CompoundVectorBuilder.getQueryVector(
            queryVecReader, luceneUtils, args);
        System.out.println(queryVector.toString());
        return new LinkedList<SearchResult>();
      } else {
        throw new IllegalArgumentException("Unknown search type: " + Flags.searchtype);
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
    if (!Flags.queryvectorfile.equals(Flags.searchvectorfile)) {
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
  public static ObjectVector[] getSearchResultVectors(String[] args, int numResults)
      throws IllegalArgumentException {
    List<SearchResult> results = Search.RunSearch(args, numResults);

    try {
      searchVecReader = VectorStoreReader.openVectorStore(Flags.searchvectorfile);
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
