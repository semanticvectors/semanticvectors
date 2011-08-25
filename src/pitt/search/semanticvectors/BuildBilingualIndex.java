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

import java.util.logging.Level;
import java.util.logging.Logger;

import java.io.IOException;

import pitt.search.semanticvectors.vectors.VectorType;

/**
 * Command line utility for creating bilingual semantic vector indexes.
 */
public class BuildBilingualIndex{
  public static Logger logger = Logger.getLogger("pitt.search.semanticvectors");

  /**
   * Prints the following usage message:
   * <code>
   * <br> BuildBilingualIndex class in package pitt.search.semanticvectors
   * <br> Usage: java pitt.search.semanticvectors.BuildBilingualIndex PATH_TO_LUCENE_INDEX LANG1 LANG2
   * <br> BuildBilingualIndex creates files termvectors_LANGn.bin and docvectors_LANGn.bin,
   * <br> in local directory, where LANG1 and LANG2 are obtained from fields in index.
   * </code>
   */
  public static void usage() {
    String usageMessage = "\nBuildBilingualIndex class in package pitt.search.semanticvectors"
      + "\nUsage: java pitt.search.semanticvectors.BuildBilingualIndex "
      + "PATH_TO_LUCENE_INDEX LANG1 LANG2"
      + "\nBuildBilingualIndex creates files termvectors_LANGn.bin and docvectors_LANGn.bin,"
      + "\nin local directory, where LANG1 and LANG2 are obtained from fields in index.";
    System.out.println(usageMessage);
  }

  /**
   * Builds term vector and document vector stores from a Lucene index.
   * @param args
   * @see BuildBilingualIndex#usage
   */
  public static void main (String[] args) throws IllegalArgumentException {
    Flags.docidfield = "filename";

    try {
      args = Flags.parseCommandLineFlags(args);
    } catch (IllegalArgumentException e) {
      usage();
      throw e;
    }

    if (!Flags.docidfield.equals("filename")) {
      logger.log(Level.WARNING, "Docid field is normally 'filename' for bilingual indexes." + 
      " Are you sure you wanted to change this?");
    }

    // Only three arguments should remain, the path to Lucene index and the language pair.
    if (args.length != 3) {
      usage();
      throw (new IllegalArgumentException("After parsing command line flags, there were " + args.length
          + " arguments, instead of the expected 3."));
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

    logger.info("seedLength = " + Flags.seedlength);
    logger.info("Vector length = " + Flags.dimension);
    logger.info("Non-alphabet characters = " + Flags.maxnonalphabetchars);
    logger.info("Minimum frequency = " + Flags.minfrequency);
    try{
      TermVectorsFromLucene vecStore1 =
        TermVectorsFromLucene.createTermVectorsFromLucene(
            luceneIndex, VectorType.valueOf(Flags.vectortype), Flags.dimension,
            Flags.seedlength, Flags.minfrequency, Flags.maxfrequency,
            Flags.maxnonalphabetchars, null, fields1);
      VectorStoreWriter vecWriter = new VectorStoreWriter();
      logger.info("Writing term vectors to " + termFile1);
      vecWriter.writeVectors(termFile1, vecStore1);
      DocVectors docVectors = new DocVectors(vecStore1);
      logger.info("Writing doc vectors to " + docFile1);
      vecWriter.writeVectors(docFile1, docVectors.makeWriteableVectorStore());

      VectorStore basicDocVectors = vecStore1.getBasicDocVectors();
      System.out.println("Keeping basic doc vectors, number: " + basicDocVectors.getNumVectors());
      TermVectorsFromLucene vecStore2 =
        TermVectorsFromLucene.createTermVectorsFromLucene(
            luceneIndex, VectorType.valueOf(Flags.vectortype), Flags.dimension,
            Flags.seedlength, Flags.minfrequency, Flags.maxfrequency,
            Flags.maxnonalphabetchars, basicDocVectors, fields2);
      logger.info("Writing term vectors to " + termFile2);
      vecWriter.writeVectors(termFile2, vecStore2);
      docVectors = new DocVectors(vecStore2);
      logger.info("Writing doc vectors to " + docFile2);
      vecWriter.writeVectors(docFile2, docVectors.makeWriteableVectorStore());
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }
}
