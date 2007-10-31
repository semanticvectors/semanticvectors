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
public class Search{
    /**
     * Prints the following usage message:
     * <code>
     * <br> Search class in package pitt.search.semanticvectors
     * <br> Usage: java pitt.search.semanticvectors.Search [-q queryvectorfile]
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
     * </code>
     */
    public static void usage(){
	String usageMessage = "\nSearch class in package pitt.search.semanticvectors"
	    + "\nUsage: java pitt.search.semanticvectors.Search [-q queryvectorfile]"
	    + "\n                                               [-s search_vector_file]"
	    + "\n                                               [-l path_to_lucene_index]"
	    + "\n                                               <QUERYTERMS>"
	    + "\n-q argument must precede -s argument if they differ;"
	    + "\n    otherwise -s will default to -q."
	    + "\nIf no query or search file is given, default will be"
	    + "\n    termvectors.bin in local directory."
	    + "\n-l argument is needed if to get term weights from"
	    + "\n    term frequency, doc frequency, etc. in lucene index."
	    + "\n<QUERYTERMS> should be a list of words, separated by spaces.";
	System.out.println(usageMessage);
	System.exit(-1);
    }

    /**
     * Takes a user's query, creates a query vector, and searches a vector store.
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

	/* reading and searching test */
	try{
	    VectorStoreReader vecReader = new VectorStoreReader(queryFile);
	    System.err.println("Opening query vector store from file: " + queryFile);

	    if( lucenePath != null ){
		try{ lUtils = new LuceneUtils( lucenePath ); }
		catch( IOException e ){
		    System.err.println("Couldn't open Lucene index at " + lucenePath);
		}
	    }
	    if( lUtils == null ){
		System.err.println("No Lucene index for query term weighting, "
				   + "so all query terms will have same weight.");
	    }

	    // This takes the slice of args from argc to end, joined
	    // into a single string: there may be a more elegant way
	    // of doing this.
	    String queryString = "";
	    for (int j = 0; j < args.length - argc; ++j) {
		queryString += args[j + argc] + " ";
	    }
	    // Now get the query vector for these terms.
	    float[] queryVec = CompoundVectorBuilder.getQueryVector(vecReader,
								    lUtils,
								    queryString);
	    System.err.print("Searching term vectors ... ");

	    /* reopen vector store if search vectors are different from query vectors */ 
	    if( queryFile != searchFile ){
		System.err.println("Opening file of vectors to search ...");
		vecReader = new VectorStoreReader(searchFile);
	    }
	    vecReader.getAllVectors();
	    LinkedList<SearchResult> results = 
		VectorUtils.getNearestNeighbors(queryVec, vecReader.getAllVectors(), 20);

	    System.err.println("Search output follows ...");
	    for (SearchResult result: results) {
		System.out.println(result.getScore() + ":" +
				   ((ObjectVector)result.getObject()).getObject().toString());
	    }
	}
	catch( IOException e ){
	    e.printStackTrace();
	}
    }
}
