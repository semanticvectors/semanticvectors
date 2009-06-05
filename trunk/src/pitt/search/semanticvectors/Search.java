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
import java.lang.IllegalArgumentException;
import java.util.LinkedList;

/**
 * Command line term vector search utility.
 *
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
 * @see VectorSearcher.VectorSearcherTensorSim
 * <br/><b>tensor</b>:
 * A product similarity that trains by taking ordered pairs of
 * terms, a target query term, and searches for the term whose tensor
 * product with the target term gives the largest similarity with training tensor.
 * Will almost certainly not work well until convolution / tensor relations are
 * built into indexing phase.
 *
 * @see VectorSearcher.VectorSearcherConvolutionSim
 * <br/><b>convolution</b>:
 * Similar to <code>TENSOR</code>, product similarity that trains
 * by taking ordered pairs of terms, a target query term, and
 * searches for the term whose convolution product with the target
 * term gives the largest similarity with training convolution.
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

  private static CloseableVectorStore queryVecReader;
  private static CloseableVectorStore searchVecReader;
  private static LuceneUtils luceneUtils;

  /**
   * Prints the following usage message:
   * <code>
   * <br> Search class in package pitt.search.semanticvectors
   * <br> Usage: java pitt.search.semanticvectors.Search [-queryfile query_vector_file]
   * <br>                                                [-searchfile search_vector_file]
   * <br>                                                [-luceneindexpath path_to_lucene_index]
   * <br>                                                [-searchtype TYPE]
   * <br>                                                [-numsearchresults num_results]
   * <br>                                                [-lowercasequery]
   * <br>                                                &lt;QUERYTERMS&gt;
   * <br> -luceneindexpath argument my be used to get term weights from
   * <br>     term frequency, doc frequency, etc. in lucene index.
   * <br> -searchtype can be one of SUM, SPARSESUM, SUBSPACE, MAXSIM,
   * <br> TENSOR, CONVOLUTION, PERMUTATION, BALANCED_PERMUTATION, PRINTQUERY
   * <br> &lt;QUERYTERMS&gt; should be a list of words, separated by spaces.
   * <br> If the term NOT is used, terms after that will be negated.
   * </code>
   */
  public static void usage() {
    String usageMessage = "\nSearch class in package pitt.search.semanticvectors"
        + "\nUsage: java pitt.search.semanticvectors.Search [-queryvectorfile query_vector_file]"
        + "\n                                               [-searchvectorfile search_vector_file]"
        + "\n                                               [-luceneindexpath path_to_lucene_index]"
        + "\n                                               [-searchtype TYPE]"
        + "\n                                               [-numsearchresults num_results]"
        + "\n                                               [-lowercasequery]"
        + "\n                                               <QUERYTERMS>"
        + "\nIf no query or search file is given, default will be"
        + "\n    termvectors.bin in local directory."
        + "\n-luceneindexpath argument is needed if to get term weights from"
        + "\n    term frequency, doc frequency, etc. in lucene index."
        + "\n-searchtype can be one of SUM, SPARSESUM, SUBSPACE, MAXSIM,"
        + "\n     TENSOR, CONVOLUTION, BALANCED_PERMUTATION, PERMUTATION, PRINTQUERY"
        + "\n<QUERYTERMS> should be a list of words, separated by spaces."
        + "\n    If the term NOT is used, terms after that will be negated.";
    System.out.println(usageMessage);
  }


  /**
   * Takes a user's query, creates a query vector, and searches a vector store.
   * @param args See usage();
   * @param numResults Number of search results to be returned in a ranked list.
   * @return Linked list containing <code>numResults</code> search results.
   */
  public static LinkedList<SearchResult> RunSearch (String[] args, int numResults)
      throws IllegalArgumentException {
    /**
     * The RunSearch function has four main stages:
     * i. Parse command line arguments, with a tiny bit of extra logic for vector stores.
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
      System.err.println("Opening query vector store from file: " + Flags.queryvectorfile);
      queryVecReader = VectorStoreReader.openVectorStore(Flags.queryvectorfile);

      // Open second vector store if search vectors are different from query vectors.
      if (Flags.queryvectorfile.equals(Flags.searchvectorfile)) {
        searchVecReader = queryVecReader;
      } else {
        System.err.println("Opening search vector store from file: " + Flags.searchvectorfile);
        searchVecReader = VectorStoreReader.openVectorStore(Flags.searchvectorfile);
      }

      if (Flags.luceneindexpath != "") {
        try { luceneUtils = new LuceneUtils(Flags.luceneindexpath); }
        catch (IOException e) {
          System.err.println("Couldn't open Lucene index at " + Flags.luceneindexpath);
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

    VectorSearcher vecSearcher;
    LinkedList<SearchResult> results = new LinkedList<SearchResult>();
    // Stage iii. Perform search according to which searchType was selected.
    // Most options have corresponding dedicated VectorSearcher subclasses.
    if (Flags.searchtype.equals("sum")) {
      // Create VectorSearcher and search for nearest neighbors.
      try {
        vecSearcher =
            new VectorSearcher.VectorSearcherCosine(queryVecReader,
                                                    searchVecReader,
                                                    luceneUtils,
                                                    args);
        System.err.print("Searching term vectors, searchtype SUM ... ");
        results = vecSearcher.getNearestNeighbors(numResults);
      } catch (ZeroVectorException zve) {
        System.err.println(zve.getMessage());
        results = new LinkedList<SearchResult>();
      }
    } else if (Flags.searchtype.equals("sparsesum")) {
      // Option for quantizing to sparse vectors before
      // comparing. This is for experimental purposes to see how much
      // we lose by compressing to a sparse bit vector.
      // Create VectorSearcher and search for nearest neighbors.
      try {
        vecSearcher =
            new VectorSearcher.VectorSearcherCosineSparse(queryVecReader,
                                                          searchVecReader,
                                                          luceneUtils,
                                                          args);
        System.err.print("Searching term vectors, searchtype SPARSESUM ... ");
        results = vecSearcher.getNearestNeighbors(numResults);
      } catch (ZeroVectorException zve) {
        System.err.println(zve.getMessage());
        results = new LinkedList<SearchResult>();
      }
    } else if (Flags.searchtype.equals("tensor")) {
      // Tensor product.
      // Create VectorSearcher and search for nearest neighbors.
      try {
        vecSearcher =
            new VectorSearcher.VectorSearcherTensorSim(queryVecReader,
                                                       searchVecReader,
                                                       luceneUtils,
                                                       args);
        System.err.print("Searching term vectors, searchtype TENSOR ... ");
        results = vecSearcher.getNearestNeighbors(numResults);
      } catch (ZeroVectorException zve) {
        System.err.println(zve.getMessage());
        results = new LinkedList<SearchResult>();
      }
    } else if (Flags.searchtype.equals("convolution")) {
      // Convolution product.
      // Create VectorSearcher and search for nearest neighbors.
      try {
        vecSearcher =
            new VectorSearcher.VectorSearcherConvolutionSim(queryVecReader,
                                                            searchVecReader,
                                                            luceneUtils,
                                                            args);
        System.err.print("Searching term vectors, searchtype CONVOLUTION ... ");
        results = vecSearcher.getNearestNeighbors(numResults);
      } catch (ZeroVectorException zve) {
        System.err.println(zve.getMessage());
        results = new LinkedList<SearchResult>();
      }
    } else if (Flags.searchtype.equals("subspace")) {
      // Quantum disjunction / subspace similarity.
      // Create VectorSearcher and search for nearest neighbors.
      try {
        vecSearcher =
            new VectorSearcher.VectorSearcherSubspaceSim(queryVecReader,
                                                         searchVecReader,
                                                         luceneUtils,
                                                         args);

        System.err.print("Searching term vectors, searchtype SUBSPACE ... ");
        results = vecSearcher.getNearestNeighbors(numResults);
      }	catch (ZeroVectorException zve) {
        System.err.println(zve.getMessage());
        results = new LinkedList<SearchResult>();
      }
    } else if (Flags.searchtype.equals("maxsim")) {
      // Ranks by maximum similarity with any of the query terms.
      // Create VectorSearcher and search for nearest neighbors.
      try {
        vecSearcher =
            new VectorSearcher.VectorSearcherMaxSim(queryVecReader,
                                                    searchVecReader,
                                                    luceneUtils,
                                                    args);
        System.err.print("Searching term vectors, searchtype MAXSIM ... ");
        results = vecSearcher.getNearestNeighbors(numResults);
      }	catch (ZeroVectorException zve) {
        System.err.println(zve.getMessage());
        results = new LinkedList<SearchResult>();
      }
    } else if (Flags.searchtype.equals("permutation")) {
      // Permutes query vectors such that the most likely term in the position
      // of the "?" is retrieved
      try {
        // Create VectorSearcher and search for nearest neighbors.
        vecSearcher =
            new VectorSearcher.VectorSearcherPerm(queryVecReader,
                                                  searchVecReader,
                                                  luceneUtils,
                                                  args);
        System.err.print("Searching term vectors, searchtype PERMUTATION ... ");
        results = vecSearcher.getNearestNeighbors(numResults);
      } catch (ZeroVectorException zve) {
        System.err.println(zve.getMessage());
        results = new LinkedList<SearchResult>();
      }
    } else if (Flags.searchtype.equals("balanced_permutation")) {
      // Permutes query vectors such that the most likely term in the position
      // of the "?" is retrieved
      try {
        // Create VectorSearcher and search for nearest neighbors.
        vecSearcher =
            new VectorSearcher.BalancedVectorSearcherPerm(queryVecReader,
                                                          searchVecReader,
                                                          luceneUtils,
                                                          args);
        System.err.print("Searching term vectors, searchtype BALANCED_PERMUTATION ... ");
        results = vecSearcher.getNearestNeighbors(numResults);
      } catch (ZeroVectorException zve) {
        System.err.println(zve.getMessage());
        results = new LinkedList<SearchResult>();
      }
    } else if (Flags.searchtype.equals("printquery")) {
      // Simply prints out the query vector: doesn't do any searching.
      float[] queryVector = CompoundVectorBuilder.getQueryVector(queryVecReader,
                                                                 luceneUtils,
                                                                 args);
      VectorUtils.printVector(queryVector);
      return new LinkedList<SearchResult>();
    } else {
      // This shouldn't happen: unrecognized options shouldn't have got past the Flags parsing.
      System.err.println("Search type unrecognized ...");
      results = new LinkedList<SearchResult>();
    }

    return results;
  }

  /**
   * Search wrapper that returns the list of ObjectVectors.
   */
  public static ObjectVector[] getSearchResultVectors(String[] args, int numResults)
      throws IllegalArgumentException {
    LinkedList<SearchResult> results = Search.RunSearch(args, numResults);
    ObjectVector[] resultsList = new ObjectVector[results.size()];
    for (int i = 0; i < results.size(); ++i) {
      String term = ((ObjectVector)results.get(i).getObject()).getObject().toString();
      float[] tmpVector = searchVecReader.getVector(term);
      resultsList[i] = new ObjectVector(term, tmpVector);
    }
    return resultsList;
  }

  /**
   * Takes a user's query, creates a query vector, and searches a vector store.
   * @param args See usage();
   */
  public static void main (String[] args)
      throws IllegalArgumentException {
    int defaultNumResults = 20;
    LinkedList<SearchResult> results = RunSearch(args, defaultNumResults);
    // Print out results.
    if (results.size() > 0) {
      System.err.println("Search output follows ...");
      for (SearchResult result: results) {
        System.out.println(result.getScore() + ":" +
                           ((ObjectVector)result.getObject()).getObject().toString());
      }
    } else {
      System.err.println("No search output.");
    }

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
  }
}
