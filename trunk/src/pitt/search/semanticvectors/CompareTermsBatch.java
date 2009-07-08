/**
   Copyright (c) 2009, the SemanticVectors AUTHORS.

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
import java.io.InputStreamReader;
import java.io.IOException;
import java.lang.ArrayIndexOutOfBoundsException;
import java.lang.IllegalArgumentException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Vector;

import org.apache.lucene.index.Term;


/**
 * Command line term vector comparison utility designed to be run in
 batch mode. This enables users to
 get raw similarities between two concepts. These concepts may be
 individual words or lists of words. For example, if your vectorfile
 is the (default) termvectors.bin, you should be able to run
 comparisons like

 <br>
 <code>echo 'blue | red green' | java pitt.search.semanticvectors.CompareTermsBatch
 </code>

 <br>
 which will give you the cosine similarity of the "blue"
 vector with the sum of the "red" and "green" vectors.

 <br>
 The process can be set up to accept long lists of piped input without
 requiring the overhead of reloading the lists of vectors, and can store the
 vectors in memory.


 <br> If the term NOT is used in one of the lists, subsequent terms in
 that list will be negated.

 @see Search
 @author Andrew MacKinlay
*/

public class CompareTermsBatch{
  /**
   * Prints the following usage message:
   * <code>
    * <br>Usage: java pitt.search.semanticvectors.CompareTermsBatch [-queryvectorfile vectorfile]
    * <br>                                         [-luceneindexpath path_to_lucene_index]
    * <br>                                         [-batchcompareseparator separator]
    * <br>                                         [-vectorstorelocation loc]
    * <br>-luceneindexpath argument may be used to get term weights from
    * <br>     term frequency, doc frequency, etc. in lucene index.
    * <br>-batchcompareseparator separator which is used to split each input line into strings of terms
    * <br>  (default '|')
    * <br>-vectorstorelocation: 'ram' or 'disk', for where to store vectors
    * <br>For each line of input from STDIN, this will split the input into two strings
    * <br>   of terms at the separator, and output a similarity score to STDOUT.
    * <br>If the term NOT is used in one of the lists, subsequent terms in
    * <br>that list will be negated (as in Search class).
    * </code>
   * @see Search
   */
  public static void usage(){
    String usageMessage = "CompareTermsBatch class in package pitt.search.semanticvectors"
      + "\nUsage: java pitt.search.semanticvectors.CompareTermsBatch "
      + "\n   [-queryvectorfile vecfile] [-luceneindexpath path]"
      + "\n   [-batchcompareseparator sep] [-vectorstorelocation loc]"
      + "\n-luceneindexpath argument may be used to get term weights from"
      + "\n   term frequency, doc frequency, etc. in lucene index."
      + "\n-batchcompareseparator separator which is used to split each input line into "
      + "\n   strings of terms (default '|')"
      + "\n-vectorstorelocation: 'ram' or 'disk', for where to store vectors"
      + "\nFor each line of input from STDIN, this will split the input into two strings"
      + "\n   of terms at the separator, and output a similarity score to STDOUT."
      + "\nIf the term NOT is used in one of the lists, subsequent terms in "
      + "\nthat list will be negated (as in Search class).";
    System.err.println(usageMessage);
  }

  /**
   * Main function for command line use.
   * @param args See usage();
   */
  public static void main (String[] args) throws IllegalArgumentException {
    try {
      args = Flags.parseCommandLineFlags(args);
    }
    catch (java.lang.IllegalArgumentException e) {
      usage();
      throw e;
    }

    LuceneUtils luceneUtils = null;
    int argc = 0;
    String separator = Flags.batchcompareseparator;
    boolean ramCache = (Flags.vectorstorelocation == "ram");

    /* reading and searching test */
    try {
      VectorStore vecReader;
      if (ramCache) {
        VectorStoreRAM ramReader = new VectorStoreRAM();
        ramReader.InitFromFile(Flags.queryvectorfile);
        vecReader = ramReader;
        System.err.println("Using RAM cache of vectors");
      } else {
        vecReader = new VectorStoreReaderLucene(Flags.queryvectorfile);
        System.err.println("Reading vectors directly off disk");
      }

      if (Flags.luceneindexpath != null) {
        try {
          luceneUtils = new LuceneUtils(Flags.luceneindexpath);
        } catch (IOException e) {
          System.err.println("Couldn't open Lucene index at " + Flags.luceneindexpath);
        }
      }
      if (luceneUtils == null) {
        System.err.println("No Lucene index for query term weighting, "
                           + "so all query terms will have same weight.");
      }

      BufferedReader input = new BufferedReader(new InputStreamReader(System.in));

      String line;
      while ((line = input.readLine()) != null) {
        String[] elems = line.split(separator);
        if (elems.length != 2) {
          throw new IllegalArgumentException("The separator '" + separator +
            "' must occur exactly once (found " + (elems.length - 1) + " occurrences)");
        }
        float[] vec1 = CompoundVectorBuilder.getQueryVectorFromString(vecReader,
                                                                    luceneUtils,
                                                                    elems[0]);
        float[] vec2 = CompoundVectorBuilder.getQueryVectorFromString(vecReader,
                                                                    luceneUtils,
                                                                    elems[1]);

        float simScore = VectorUtils.scalarProduct(vec1, vec2);
        System.err.println("score=" + simScore);
      // Printing prompt to stderr and score to stdout, this should enable
      // easier batch scripting to combine input and output data.
        System.out.println(simScore);
      }
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }
}
