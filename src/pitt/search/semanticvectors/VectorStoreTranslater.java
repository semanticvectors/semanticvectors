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

import java.io.IOException;
import java.lang.IllegalArgumentException;

/**
 * Class providing command-line interface for transforming vector
 * store between the optimized Lucene format and plain text.
 */
public class VectorStoreTranslater {

		/**
		 * Prints the following usage message:
		 * <br> VectorStoreTranslater class in pitt.search.semanticvectors
		 * <br> Usage: java pitt.search.semanticvector.VectorStoreTranslater -option INFILE OUTFILE"
		 * <br> -option can be: -lucenetotext or -texttolucene"
		 */
  public static void usage() {
    String usageMessage = "VectorStoreTranslater class in pitt.search.semanticvectors"
        + "\nUsage: java pitt.search.semanticvector.VectorStoreTranslater -option INFILE OUTFILE"
        + "\n -option can be: -lucenetotext or -texttolucene";
    System.out.println(usageMessage);
  }

  private enum Options { LUCENE_TO_TEXT, TEXT_TO_LUCENE }

		/**
		 * Command line method for performing index translation.
		 * @see #usage
		 */
  public static void main(String[] args) {
    // Parse command line args.
    if (args.length != 3) {
      System.err.println("You gave " + args.length + " arguments ...");
			usage();
			throw new IllegalArgumentException();
    }
    Options option = null;
    if (args[0].equalsIgnoreCase("-lucenetotext")) { option = Options.LUCENE_TO_TEXT; }
    else if (args[0].equalsIgnoreCase("-texttolucene")) { option = Options.TEXT_TO_LUCENE; }
    else {
			usage();
			throw new IllegalArgumentException();
		}

    String infile = args[1];
    String outfile = args[2];

		// Convert Lucene-style index to plain text.
    if (option == Options.LUCENE_TO_TEXT) {
      try {
        VectorStoreReader vecReader = new VectorStoreReader(infile);
        VectorStoreWriter vecWriter = new VectorStoreWriter();
        System.err.println("Writing term vectors to " + outfile);
        vecWriter.WriteVectorsAsText(outfile, vecReader);
      }
      catch (IOException e) {
        e.printStackTrace();
      }
    }

		// Convert plain text index to Lucene-style.
    if (option == Options.TEXT_TO_LUCENE) {
      try {
        VectorStoreReaderText vecReader = new VectorStoreReaderText(infile);
        VectorStoreWriter vecWriter = new VectorStoreWriter();
        System.err.println("Writing term vectors to " + outfile);
        vecWriter.WriteVectors(outfile, vecReader);
      }
      catch (IOException e) {
        e.printStackTrace();
      }
    }
  }
}
