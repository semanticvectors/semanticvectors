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
import org.apache.lucene.index.Term;

/**
 * Command line term vector comparison utility. This enables users to
 get raw similarities between two concepts. These concepts may be
 individual words or lists of words. For example, if your vectorfile
 is the (default) termvectors.bin, you should be able to run
 comparisons like 

 <br>
 <code>java pitt.search.semanticvectors.CompareTerms "blue" "red green"
 </code>

 <br> 
 which will give you the cosine similarity of the "blue"
 vector with the sum of the "red" and "green" vectors.

 <br> If the term NOT is used in one of the lists, subsequent terms in 
 that list will be negated.

 @see Search
*/

public class CompareTerms{
  /**
   * Prints the following usage message: 
   * <code>
   * <br> CompareTerms class in package pitt.search.semanticvectors 
   * <br> Usage: java pitt.search.semanticvectors.CompareTerms [-v vectorfile]
   * <br>                                                      [-l path_to_lucene_index]
   * <br>                                         "&lt;QUERYTERMS1&gt;" "&lt;QUERYTERMS2&gt;"
   * <br>-l argument may be used to get term weights from
   * <br>term frequency, doc frequency, etc. in lucene index.
   * <br>"&lt;QUERYTERMS1,2&gt;" should be lists of words, separated by spaces.
   * <br> The quotes are mandatory unless you are comparing two single words.
   * <br> If the term NOT is used in one of the lists, subsequent terms in 
   * <br> that list will be negated (as in Search class).
   * </code>
   * @see Search
   */
  public static void usage(){
    String usageMessage = "CompareTerms class in package pitt.search.semanticvectors"
        + "\nUsage: java pitt.search.semanticvectors.CompareTerms [-v vectorfile]"
        + "\n                                        [-l path_to_lucene_index]"
        + "\n                                        \"<QUERYTERMS1>\" \"<QUERYTERMS2>\""
        + "\n-l argument may be used to get term weights from"
        + "\n    term frequency, doc frequency, etc. in lucene index."
        + "\n<QUERYTERMS1,2> should be lists of words, separated by spaces."
        + "\nThe quotes are mandatory unless you are comparing two single words."
        + "\nIf the term NOT is used in one of the lists, subsequent terms in "
        + "\nthat list will be negated (as in Search class).";
    System.out.println(usageMessage);
  }

  /**
   * Main function for command line use.
   * @param args See usage();
   */
  public static void main (String[] args) throws IllegalArgumentException {
    if (args.length == 0) {
      usage();
      throw new IllegalArgumentException();
    }

    String queryFile = "termvectors.bin"; // default value
    String lucenePath = null;
    LuceneUtils lUtils = null;
    int argc = 0;

    // parse command-line args
    while (args[argc].substring(0, 1).equals("-")) {
      if (args[argc].equals("-v")) {
        queryFile = args[argc + 1];
        argc += 2;
      }
      else if (args[argc].equals("-l")) {
        lucenePath = args[argc + 1];
        argc += 2;
      }
      else if (args[argc].equals("-lookupsyntax")) {
        if (args[argc + 1].equalsIgnoreCase("regex")) {
          CompoundVectorBuilder.lookupSyntax = CompoundVectorBuilder.LookupSyntax.REGEX;
        }
        argc += 2;
      }
      else {
        usage();
        throw new IllegalArgumentException();
      }
    }

    if (args.length - argc != 2) {
      System.err.println("After parsing command line options there must be " +
                         "exactly two queryterm expressions to compare.");
      usage();
      throw new IllegalArgumentException();
    }


    /* reading and searching test */
    try {
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

      float[] vec1 = CompoundVectorBuilder.getQueryVectorFromString(vecReader,
                                                                    lUtils,
                                                                    args[argc]);
      float[] vec2 = CompoundVectorBuilder.getQueryVectorFromString(vecReader,
                                                                    lUtils,
                                                                    args[argc + 1]);
      float simScore = VectorUtils.scalarProduct(vec1, vec2);
      // Printing prompt to stderr and score to stdout, this should enable
      // easier batch scripting to combine input and output data.
      System.err.println("Outputting similarity of \"" + args[argc]
                         + "\" with \"" + args[argc+1] + "\" ...");
      System.out.println(simScore);
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }
}
