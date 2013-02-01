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
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Logger;

import pitt.search.semanticvectors.vectors.Vector;

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

public class CompareTermsBatch {
  public static String usageMessage = "CompareTermsBatch class in package pitt.search.semanticvectors"
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
  
  private static final Logger logger = Logger.getLogger(
      CompareTermsBatch.class.getCanonicalName());

  /**
   * Main function for command line use.
   * @param args See {@link #usageMessage}.
   */
  public static void main (String[] args) throws IllegalArgumentException {
    FlagConfig flagConfig;
    try {
      flagConfig = FlagConfig.getFlagConfig(args);
      args = flagConfig.remainingArgs;
    }
    catch (java.lang.IllegalArgumentException e) {
      System.err.println(usageMessage);
      throw e;
    }

    LuceneUtils luceneUtils = null;
    String separator = flagConfig.batchcompareseparator();
    boolean ramCache = (flagConfig.vectorstorelocation().equals("ram"));

    /* reading and searching test */
    try {
      VectorStore vecReader;
      if (ramCache) {
        VectorStoreRAM ramReader = new VectorStoreRAM(flagConfig);
        ramReader.initFromFile(flagConfig.queryvectorfile());
        vecReader = ramReader;
        logger.info("Using RAM cache of vectors");
      } else {
        vecReader = new VectorStoreReaderLucene(flagConfig.queryvectorfile(), flagConfig);
        logger.info("Reading vectors directly off disk");
      }

      if (flagConfig.luceneindexpath() != null) {
        try {
          luceneUtils = new LuceneUtils(flagConfig);
        } catch (IOException e) {
          logger.info("Couldn't open Lucene index at " + flagConfig.luceneindexpath());
        }
      }
      if (luceneUtils == null) {
        logger.info("No Lucene index for query term weighting, "
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
        Vector vec1 = CompoundVectorBuilder.getQueryVectorFromString(
            vecReader, luceneUtils, flagConfig, elems[0]);
        Vector vec2 = CompoundVectorBuilder.getQueryVectorFromString(
            vecReader, luceneUtils, flagConfig, elems[1]);
        if (vecReader instanceof VectorStoreReaderLucene) {
          ((VectorStoreReaderLucene)vecReader).close();
        }
        double simScore = vec1.measureOverlap(vec2);
        logger.info("score=" + simScore);
        System.out.println(simScore);
      }
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }
}
