/**
   Copyright 2008, Google Inc.
   All rights reserved.

   Redistribution and use in source and binary forms, with or without
   modification, are permitted provided that the following conditions are
   met:

   * Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.

   * Redistributions in binary form must reproduce the above
   copyright notice, this list of conditions and the following disclaimer
   in the documentation and/or other materials provided with the
   distribution.

   * Neither the name of Google Inc. nor the names of its
   contributors may be used to endorse or promote products derived from
   this software without specific prior written permission.

   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
   A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
   OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
   SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
   LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
   DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
   THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
   (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
   THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
**/

package pitt.search.semanticvectors;

import java.lang.IllegalArgumentException;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Enumeration;
import java.util.Random;
import org.apache.lucene.index.*;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

import java.util.LinkedList;
import java.io.IOException;

/**
 * Command line utility for creating bilingual semantic vector indexes.
 */
public class BuildBilingualIndex{
  // These can be modified with command line arguments.
  static int seedLength = 20;
  static int nonAlphabet =0;
  static int minFreq = 10;

  /**
   * Prints the following usage message:
   * <code>
   * <br> BuildBilingualIndex class in package pitt.search.semanticvectors
   * <br> Usage: java pitt.search.semanticvectors.BuildBilingualIndex PATH_TO_LUCENE_INDEX LANG1 LANG2
   * <br> BuildBilingualIndex creates files termvectors_LANGn.bin and docvectors_LANGn.bin,
   * <br> in local directory, where LANG1 and LANG2 are obtained from fields in index.
   * <br> Other parameters that can be changed include vector length,
   * <br>     (number of dimensions), seed length (number of non-zero
   * <br>     entries in basic vectors), and minimum term frequency.
   * <br> To change these use the following command line arguments:
   * <br> -d [number of dimensions]
   * <br> -s [seed length]
   * <br> -n [number non-alphabet characters (-1 for any number)]
   * <br> -m [minimum term frequency]
   * </code>
   */
  public static void usage(){
    String usageMessage = "\nBuildBilingualIndex class in package pitt.search.semanticvectors"
        + "\nUsage: java pitt.search.semanticvectors.BuildBilingualIndex "
        + "PATH_TO_LUCENE_INDEX LANG1 LANG2"
        + "\nBuildBilingualIndex creates files termvectors_LANGn.bin and docvectors_LANGn.bin,"
        + "\nin local directory, where LANG1 and LANG2 are obtained from fields in index."
        + "\nOther parameters that can be changed include vector length,"
        + "\n    (number of dimensions), seed length (number of non-zero"
        + "\n    entries in basic vectors), and minimum term frequency."
        + "\nTo change these use the command line arguments "
        + "\n  -d [number of dimensions]"
        + "\n  -n [number of non-alphabet characters (-1 for any number)]"
        + "\n  -s [seed length]"
        + "\n  -m [minimum term frequency]";

    System.out.println(usageMessage);
  }

  /**
   * Builds term vector and document vector stores from a Lucene index.
   * @param args
   * @see BuildBilingualIndex#usage
   */
  public static void main (String[] args) throws IllegalArgumentException {
    boolean wellFormed = false;
    /* If only three argument, they should be the path to Lucene index and the language pair. */
    if (args.length == 3) {
      wellFormed = true;
    }
    /* If there is an even number of arguments, there's a problem. */
    else if (args.length % 2 == 0) {
      wellFormed = false;
    }
    /* Parse command line arguments. */
    else {
      for (int x = 0; x < args.length-1; x += 2) {
        String option = args[x];
        String value = args[x+1];

        /* Get number of dimensions. */
        if (option.equalsIgnoreCase("-d")) {
          try {
            ObjectVector.vecLength = Integer.parseInt(value);
            wellFormed = true;
          } catch (NumberFormatException e) {
            System.err.println(value + " is not a number"); 
						usage();
						throw new IllegalArgumentException();
          }
        }
        /* Get seedlength. */
        else if (option.equalsIgnoreCase("-s")) {
          try {
            seedLength = Integer.parseInt(value);
            if (seedLength > ObjectVector.vecLength) {
              System.err.println("Seed length cannot be greater than vector length");
              usage();
            }
            else wellFormed = true;
          } catch (NumberFormatException e) {
            System.err.println(value + " is not a number");
						usage();
						throw new IllegalArgumentException();
          }
        }
    	
        /* Get minimum term frequency. */
        else if (option.equalsIgnoreCase("-m")) {
          try {
            minFreq = Integer.parseInt(value);
            if (minFreq < 0) {
              System.err.println("Minimum frequency cannot be less than zero");
              usage();
							throw new IllegalArgumentException();
            }
            else wellFormed = true;
          } catch (NumberFormatException e) {
            System.err.println(value + " is not a number");
						usage();
						throw new IllegalArgumentException();
          }
        }
        /* Allow n non-alphabet characters, or -1 for no character screening */
        else if (option.equalsIgnoreCase("-n")) {
		    try {
			nonAlphabet = Integer.parseInt(value);
			wellFormed = true;
		    } catch (NumberFormatException e) {
			System.err.println(value + " is not a number");
			usage();   
				throw new IllegalArgumentException();
		    }
		}
        /* All other arguments are unknown. */
        else {
          System.err.println("Unknown command line option: " + option);
          usage();
					throw new IllegalArgumentException();
        }
      }
    }
    if (!wellFormed) {
      usage();
			throw new IllegalArgumentException();
    }

    String luceneIndex = args[args.length - 3];
    String lang1 = args[args.length - 2];
    String lang2 = args[args.length - 1];
    String termFile1 = "termvectors_" + lang1 + ".bin";
    String termFile2 = "termvectors_" + lang2 + ".bin";
    String docFile1 = "docvectors_" + lang1 + ".bin";
    String docFile2 = "docvectors_" + lang2 + ".bin";
    String[] fields1 = new String[] {"contents_" + lang1};
    String[] fields2 = new String[] {"contents_" + lang2};

    System.err.println("seedLength = " + seedLength);
    System.err.println("Vector length = " + ObjectVector.vecLength);
    System.err.println("Non-alphabet characters = "+ nonAlphabet);
    System.err.println("Minimum frequency = " + minFreq);
    try{
      TermVectorsFromLucene vecStore1 =
          new TermVectorsFromLucene(luceneIndex, seedLength, minFreq, nonAlphabet, null, fields1);
      VectorStoreWriter vecWriter = new VectorStoreWriter();
      System.err.println("Writing term vectors to " + termFile1);
      vecWriter.WriteVectors(termFile1, vecStore1);
      DocVectors docVectors = new DocVectors(vecStore1);
      System.err.println("Writing doc vectors to " + docFile1);
      vecWriter.WriteVectors(docFile1, docVectors.makeWriteableVectorStore());

      VectorStore basicDocVectors = vecStore1.getBasicDocVectors();
      System.out.println("Keeping basic doc vectors, number: " + basicDocVectors.getNumVectors());
      TermVectorsFromLucene vecStore2 =
          new TermVectorsFromLucene(luceneIndex, seedLength, minFreq, nonAlphabet, 
                                    basicDocVectors, fields2);
      System.err.println("Writing term vectors to " + termFile2);
      vecWriter.WriteVectors(termFile2, vecStore2);
      docVectors = new DocVectors(vecStore2);
      System.err.println("Writing doc vectors to " + docFile2);
      vecWriter.WriteVectors(docFile2, docVectors.makeWriteableVectorStore());
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }
}
