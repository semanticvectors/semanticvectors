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

import java.util.LinkedList;
import java.io.IOException;

/**
 * Command line term vector search utility.
 */
public class Search {
	public enum SearchType { SUM, SUBSPACE, MAXSIM, TENSOR, CONVOLUTION } 

	// Experimenting with class-level static variables to enable several
	// methods to use this state. Initialize each with default values.
	static String queryFile = "termvectors.bin";
	static String searchFile = "";
	static String lucenePath = null;
	static boolean textIndex = false; 
	static LuceneUtils lUtils = null;
	static SearchType searchType = SearchType.SUM;

  /**
   * Prints the following usage message:
   * <code>
   * <br> Search class in package pitt.search.semanticvectors
   * <br> Usage: java pitt.search.semanticvectors.Search [-q query_vector_file]
   * <br>                                                [-s search_vector_file]
   * <br>                                                [-l path_to_lucene_index]
   * <br>                                                &lt;QUERYTERMS&gt;
   * <br> -q argument must precede -s argument if they differ;
   * <br>     otherwise -s will default to -q.
   * <br> If no query or search file is given, default will be
   * <br>     termvectors.bin in local directory.
   * <br> -l argument my be used to get term weights from
   * <br>     term frequency, doc frequency, etc. in lucene index.
   * <br> &lt;QUERYTERMS&gt; should be a list of words, separated by spaces.
   * <br> If the term NOT is used, terms after that will be negated.
   * </code>
   */
  public static void usage() {
    String usageMessage = "\nSearch class in package pitt.search.semanticvectors"
			+ "\nUsage: java pitt.search.semanticvectors.Search [-q query_vector_file]"
			+ "\n                                               [-s search_vector_file]"
			+ "\n                                               [-l path_to_lucene_index]"
			+ "\n                                               <QUERYTERMS>"
			+ "\n-q argument must precede -s argument if they differ;"
			+ "\n    otherwise -s will default to -q."
			+ "\nIf no query or search file is given, default will be"
			+ "\n    termvectors.bin in local directory."
			+ "\n-l argument is needed if to get term weights from"
			+ "\n    term frequency, doc frequency, etc. in lucene index."
			+ "\n<QUERYTERMS> should be a list of words, separated by spaces."
			+ "\n    If the term NOT is used, terms after that will be negated.";
    System.out.println(usageMessage);
    System.exit(-1);
  }

  /**
   * Takes a user's query, creates a query vector, and searches a vector store.
   * @param args See usage();
   */
  public static void main (String[] args) {
		/** 
		 * This main function has four main stages:
		 * i. Parse command line arguments.
		 * ii. Open corresponding vector and lucene indexes.
		 * iii. Based on search type, build query vector and perform search.
		 * iv. Print out results to STDOUT. (Everything else prints to STDERR.)
		 *
		 * Stage iii. is a large switch statement, that depends on the searchType.
		 *
		 * The code would be nicer if we combined stages i. and ii., but
		 * this would be hard to implement without forcing the user to use
		 * command line arguments in a fixed order, which would definitely
		 * lead to errors. So the trade-off is to make the code more
		 * complex and the usage simpler.
		 */

    if (args.length == 0) {
      usage();
    }

    int argc = 0;

    // Stage i. Parse all the command-line arguments.
    while (args[argc].substring(0, 1).equals("-")) {
      if (args[argc].equals("-q")) {
        queryFile = args[argc + 1];
        argc += 2;
      }
      else if (args[argc].equals("-s")) {
        searchFile = args[argc + 1];
        argc += 2;
      }
      else if (args[argc].equals("-l")) {
        lucenePath = args[argc + 1];
        argc += 2;
      }
			else if (args[argc].equals("-textindex")) {
				textIndex = true;
				argc += 1;
			}
			// The most complicated option is the search type: this will
			// lead to the most complex implementational differences later.
			else if (args[argc].equals("-searchtype")) {
				String searchTypeString = args[argc + 1];
				searchTypeString.toLowerCase();
				if (searchTypeString.equals("sum")) {
					searchType = SearchType.SUM;
				}
				else if (searchTypeString.equals("subspace")) {
					searchType = SearchType.SUBSPACE;
				}
				else if (searchTypeString.equals("maxsim")) {
					searchType = SearchType.MAXSIM;
				}
				else if (searchTypeString.equals("subspace")) {
					searchType = SearchType.SUBSPACE;
				}
				else if (searchTypeString.equals("tensor")) {
					searchType = SearchType.TENSOR;
				}
				else if (searchTypeString.equals("convolution")) {
					searchType = SearchType.CONVOLUTION;
				}
				// Unrecognized search type.
				else {
					System.err.println("Unrecognized -searchtype: " + args[argc + 1]);
					System.err.print("Options are: ");
					for (SearchType option : SearchType.values()) {
						System.out.print(option + ", ");
					}
					System.out.println();
					usage();
				}
				argc += 2;
			}
			// Unrecognized command line option.
      else {
				System.err.println("The following option is not recognized: " + args[argc]);
				usage();
      }
    }

		// If searchFile wasn't set, it defaults to queryFile.
		if (searchFile.equals("")) {
			searchFile = queryFile;
		}

		// Stage ii. Open vector stores, and Lucene utils.
    try {
			// Default VectorStore implementation is (Lucene) VectorStoreReader.
      System.err.println("Opening query vector store from file: " + queryFile);
			VectorStore queryVecReader, searchVecReader;
			if (textIndex) { queryVecReader = new VectorStoreReaderText(queryFile); }
			else { queryVecReader = new VectorStoreReader(queryFile); }

      // Open second vector store if search vectors are different from query vectors.
      if (queryFile == searchFile) {
				// TODO(dwiddows, comments welcome): Check if this
				// unnecessarily duplicates resources; I think it's just a
				// reference and therefore more or less free, but I don't know
				// with Java.
				searchVecReader = queryVecReader;
			} else {
        System.err.println("Opening search vector store from file: " + searchFile);
				if (textIndex) { searchVecReader = new VectorStoreReaderText(searchFile); }
				else { searchVecReader = new VectorStoreReader(searchFile); }
      }

      if (lucenePath != null) {
        try { lUtils = new LuceneUtils(lucenePath); }
        catch (IOException e) {
          System.err.println("Couldn't open Lucene index at " + lucenePath);
        }
      }

      // This takes the slice of args from argc to end.
      String queryTerms[] = new String[args.length - argc];
      for (int j = 0; j < args.length - argc; ++j) {
				queryTerms[j] = args[j + argc];
      }

			LinkedList<SearchResult> results = new LinkedList();

			VectorSearcher vecSearcher;
			// Stage iii. Perform search according to which searchType was selected.
			// Most options have corresponding dedicated VectorSearcher subclasses.
			switch(searchType) {
			// Simplest option, vector sum for composition, with possible negation.
			case SUM:
				// Create VectorSearcher and search for nearest neighbors.
				vecSearcher =
					new VectorSearcher.VectorSearcherCosine(queryVecReader,
																									searchVecReader,
																									lUtils,
																									queryTerms);
				System.err.print("Searching term vectors ... ");
				results = vecSearcher.getNearestNeighbors(20);

			// Tensor product.
			case TENSOR:
				// Create VectorSearcher and search for nearest neighbors.
				vecSearcher =
					new VectorSearcher.VectorSearcherTensorSim(queryVecReader,
																										 searchVecReader,
																										 lUtils,
																										 queryTerms);
				System.err.print("Searching term vectors ... ");
				results = vecSearcher.getNearestNeighbors(20);
				
			}
			
			// Stage iv. Print out results.
			System.err.println("Search output follows ...");
			for (SearchResult result: results) {
				System.out.println(result.getScore() + ":" +
													 ((ObjectVector)result.getObject()).getObject().toString());
			}


    }
    catch (IOException e){
      e.printStackTrace();
    }
  }
}
