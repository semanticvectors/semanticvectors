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

import pitt.search.semanticvectors.utils.VerbatimLogger;

/**
 * Command line utility for creating bilingual semantic vector indexes.
 * 
 * TODO: This code has been refactored to accommodate FlagConfig changes: needs testing.
 */
public class BuildBilingualIndex {
  public static Logger logger = Logger.getLogger("pitt.search.semanticvectors.BuildBilingualIndex");

  /**
   * Usage message for the BuildBilingualIndex program.
   */
  private static final String usageMessage = "\nBuildBilingualIndex class in package pitt.search.semanticvectors"
	      + "\nUsage: java pitt.search.semanticvectors.BuildBilingualIndex [args] -luceneindexpath PATH_TO_LUCENE_INDEX LANG1 LANG2."
	      + "\nBuildBilingualIndex creates files termvectors_LANGn.bin and docvectors_LANGn.bin,"
	      + "\nin local directory, where LANG1 and LANG2 are obtained from fields in index.";

  /**
   * Prints {@link #usageMessage} to the console.
   */
  public static void usage() {
    System.out.println(usageMessage);
  }

  /**
   * Builds term vector and document vector stores from a Lucene index.
   * @param args
   * @see BuildBilingualIndex#usage
   */
  public static void main (String[] args) throws IllegalArgumentException {
    // Internal hack to set the expectation that -docidfield is set to "filename".
    // This is consistent with pitt.search.lucene.IndexBilingualFiles.
    // Of course, we could make an explicit setter for this flag, but I'd rather keep the
    // hack isolated near the outside of the system.
    String[] argsWithDocIdField = new String[2 + args.length];
    argsWithDocIdField[0] = "-docidfield";
    argsWithDocIdField[1] = "filename";
    System.arraycopy(args, 0, argsWithDocIdField, 2, args.length);
    
    // This is actually just a test; the "real" configs will be created below.
    FlagConfig flagConfig;
    try {
      flagConfig = FlagConfig.getFlagConfig(argsWithDocIdField);
    } catch (IllegalArgumentException e) {
      usage();
      throw e;
    }

    if (!flagConfig.docidfield().equals("filename")) {
      logger.log(Level.WARNING, "Docid field is normally 'filename' for bilingual indexes." + 
      " Are you sure you wanted to change this?");
    }

    // Only two arguments should remain, the identification strings for each language.
    if (flagConfig.remainingArgs.length != 2) {
      usage();
      throw (new IllegalArgumentException("After parsing command line flags, there were " +
    	flagConfig.remainingArgs.length + " arguments, instead of the expected 2."));
    }

    String lang1 = args[args.length - 2];
    String lang2 = args[args.length - 1];
    String termFile1 = "termvectors_" + lang1 + ".bin";
    String termFile2 = "termvectors_" + lang2 + ".bin";
    String docFile1 = "docvectors_" + lang1 + ".bin";
    String docFile2 = "docvectors_" + lang2 + ".bin";
    
    String[] argsWithDocIdAndContentsField = new String[2 + argsWithDocIdField.length];
    System.arraycopy(argsWithDocIdField, 0, argsWithDocIdAndContentsField, 2, argsWithDocIdField.length);
    argsWithDocIdAndContentsField[0] = "-contentsfields";
    argsWithDocIdAndContentsField[1] = "contents_" + lang1;
    FlagConfig actualConfigLang1 = FlagConfig.getFlagConfig(argsWithDocIdAndContentsField);
    argsWithDocIdAndContentsField[1] = "contents_" + lang2;
    FlagConfig actualConfigLang2 = FlagConfig.getFlagConfig(argsWithDocIdAndContentsField);

    VerbatimLogger.info("Creating bilingual indexes ...");
    try {
      TermVectorsFromLucene vecStore1 =
        TermVectorsFromLucene.createTermVectorsFromLucene(actualConfigLang1, null);
      logger.info("Writing term vectors to " + termFile1);
      VectorStoreWriter.writeVectors(termFile1, actualConfigLang1, vecStore1);
      DocVectors docVectors = new DocVectors(
          vecStore1, actualConfigLang1, new LuceneUtils(actualConfigLang1));
      logger.info("Writing doc vectors to " + docFile1);
      VectorStoreWriter.writeVectors(docFile1, actualConfigLang1, docVectors.makeWriteableVectorStore());

      VectorStore basicDocVectors = vecStore1.getBasicDocVectors();
      System.out.println("Keeping basic doc vectors, number: " + basicDocVectors.getNumVectors());
      TermVectorsFromLucene vecStore2 =
        TermVectorsFromLucene.createTermVectorsFromLucene(actualConfigLang2, basicDocVectors);
      logger.info("Writing term vectors to " + termFile2);
      VectorStoreWriter.writeVectors(termFile2, actualConfigLang2, vecStore2);
      docVectors = new DocVectors(
          vecStore2, actualConfigLang2, new LuceneUtils(actualConfigLang2));
      logger.info("Writing doc vectors to " + docFile2);
      VectorStoreWriter.writeVectors(docFile2, actualConfigLang2, docVectors.makeWriteableVectorStore());
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }
}
