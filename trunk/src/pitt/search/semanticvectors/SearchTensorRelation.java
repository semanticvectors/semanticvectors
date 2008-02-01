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
import org.apache.lucene.index.Term;

/**
 * Command line tensor relation search utility.
 * Individual search terms are constructed using the same syntax as in Search class.
 * @see Search
 */
public class SearchTensorRelation{
    /**
     * Prints the following usage message:
     * <code>
     * <br> SearchTensorRelation class in package pitt.search.semanticvectors
     * <br> Usage: java pitt.search.semanticvectors.SearchTensorRelation [-q query_vector_file]
     * <br>           [-s search_vector_file]"
     * <br>           [-l path_to_lucene_index]
     * <br>           "&lt;QUERYTERMS1&gt;" "&lt;QUERYTERMS2&gt;" "&lt;QUERYTERMS3&gt;"
     * <br>-l argument may be used to get term weights from
     * <br>term frequency, doc frequency, etc. in lucene index.
     * <br>"&lt;QUERYTERMS1,2,3&gt;" should be lists of words, separated by spaces.
     * <br> The quotes are mandatory unless each argument is a single word.
     * <br> If the term NOT is used in one of the lists, subsequent terms in 
     * <br> that list will be negated.
     * </code>
     * @see Search
     */
    public static void usage(){
	String usageMessage = "CompareTerms class in package pitt.search.semanticvectors"
	    + "\nUsage: java pitt.search.semanticvectors.SearchTensorRelation [-q query_vector_file]"
	    + "\n              [-s search_vector_file]"
	    + "\n              [-l path_to_lucene_index]"
	    + "\n              \"<QUERYTERMS1>\" \"<QUERYTERMS2>\" \"<QUERYTERMS3>\""
	    + "\n-l argument may be used to get term weights from"
	    + "\n    term frequency, doc frequency, etc. in lucene index."
	    + "\n<QUERYTERMS1,2,3> should be lists of words, separated by spaces."
	    + "\nThe quotes are mandatory unless each argument is a single word."
	    + "\nIf the term NOT is used in one of the lists, subsequent terms in"
	    + "\nthat list will be negated (as in Search class).";
	System.out.println(usageMessage);
	System.exit(-1);
    }

    /**
     * Main function for command line use.
     * @param args See usage();
     */
    public static void main( String[] args ){
	if( args.length == 0 ){
	    usage();
	}

	String queryFile = "termvectors.bin"; // default value
	String searchFile = "termvectors.bin"; // default value
	String lucenePath = null;
	LuceneUtils lUtils = null;
	int argc = 0;

	// parse command-line args
	while( args[argc].substring(0, 1).equals("-") ){
	    if( args[argc].equals("-q") ){
		queryFile = args[argc + 1];
		searchFile = queryFile;
		argc += 2;
	    }
	    else if( args[argc].equals("-s") ){
		searchFile = args[argc + 1];
		argc += 2;
	    }
	    else if( args[argc].equals("-l") ){
		lucenePath = args[argc + 1];
		argc += 2;
	    }
	    else{ usage(); }
	}

	if (args.length - argc != 3) {
	    System.err.println("After parsing command line options there must be " +
			       "exactly three queryterm expressions, two for seeding " +
			       "tensor relation and one for searching.");
	    usage();
	}


	/* reading and searching test */
	try{
	    VectorStoreReader vecReader = new VectorStoreReader(queryFile);
	    System.err.println("Opening query vector store from file: " + queryFile);

	    if (lucenePath != null) {
		try{ lUtils = new LuceneUtils( lucenePath ); }
		catch (IOException e) {
		    System.err.println("Couldn't open Lucene index at " + lucenePath);
		}
	    }
	    if (lUtils == null) {
		System.err.println("No Lucene index for query term weighting, "
				   + "so all query terms will have same weight.");
	    }

	    float[] rel1 = CompoundVectorBuilder.getQueryVector(vecReader, lUtils, args[argc]);
	    float[] rel2 = CompoundVectorBuilder.getQueryVector(vecReader, lUtils, args[argc + 1]);
	    float[] queryVec = CompoundVectorBuilder.getQueryVector(vecReader, lUtils, args[argc + 2]);

	    // Reopen vector store if search vectors are different from query vectors.
	    if( queryFile != searchFile ){
		System.err.println("Opening file of vectors to search ...");
		vecReader = new VectorStoreReader(searchFile);
	    }

	    // Create VectorSearcher and search for nearest neighbors.
	    VectorSearcher.VectorSearcherTensorSim vecSearcher =
		new VectorSearcher.VectorSearcherTensorSim(vecReader, rel1, rel2);
	    LinkedList<SearchResult> results =
		vecSearcher.getNearestNeighbors(queryVec, 20);

	    System.err.println("Search output follows ...");
	    for (SearchResult result: results) {
		System.out.println(result.getScore() + ":" +
				   ((ObjectVector)result.getObject()).getObject().toString());
	    }



	}
	catch (IOException e) {
	    e.printStackTrace();
	}
    }
}
