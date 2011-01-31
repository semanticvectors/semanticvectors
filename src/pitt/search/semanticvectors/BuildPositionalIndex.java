/**
   Copyright (c) 2008, University of Pittsburgh

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
import java.util.logging.Logger;

/**
 * Command line utility for creating semantic vector indexes using the
 * sliding context window approach (see work on HAL, and by Shutze).
 */
public class BuildPositionalIndex {
  public static final Logger logger = Logger.getLogger(
      BuildPositionalIndex.class.getCanonicalName());
  static VectorStore newBasicTermVectors = null;

  /**
   * Prints the following usage message:
   * <code>
   * <br> BuildPositionalIndex class in package pitt.search.semanticvectors
   * <br> Usage: java pitt.search.semanticvectors.BuildPositionalIndex PATH_TO_LUCENE_INDEX
   * <br> BuildPositionalIndex creates file termtermvectors.bin in local directory.
   * <br> Other parameters that can be changed include windowlength (size of context window),
   * <br>     vector length (number of dimensions), seed length (number of non-zero
   * <br>     entries in basic vectors), and minimum term frequency.
   * <br> To change these use the following command line arguments:
   * <br> -dimension [number of dimensions]
   * <br> -seedlength [seed length]
   * <br> -mintermfreq [minimum term frequency]
   * <br> -windowradius [window half size]
   * <br> -positionalmethod [type of index: basic (default), directional (HAL), permutation (Sahlgren 2008)
   * </code>
   */
  public static void usage() {
    String usageMessage = "\nBuildPositionalIndex class in package pitt.search.semanticvectors"
      + "\nUsage: java pitt.search.semanticvectors.BuildPositionalIndex PATH_TO_LUCENE_INDEX"
      + "\nBuildPositionalIndex creates file termtermvectors.bin in local directory."
      + "\nOther parameters that can be changed include vector length,"
      + "\n windowlength (size of sliding context window),"
      + "\n    (number of dimensions), seed length (number of non-zero"
      + "\n    entries in basic vectors), size of sliding window (including focus term)"
      + "\n and minimum term frequency.\n"
      + "\nTo change these use the command line arguments "
      + "\n  -dimension [number of dimensions]"
      + "\n  -seedlength [seed length]"
      + "\n  -mintermfreq [minimum term frequency]"
      + "\n  -initialtermvectors [name of preexisting vectorstore for term vectors]"
      + "\n  -windowradius [window size]"
      + "\n  -positionalmethod [positional indexing method: basic (default), directional (HAL), permutation (Sahlgren 2008)";

    System.out.println(usageMessage);
  }

  /**
   * Builds term vector stores from a Lucene index - this index must
   * contain TermPositionVectors.
   * @param args
   */
  public static void main (String[] args) throws IllegalArgumentException {
    try {
      args = Flags.parseCommandLineFlags(args);
    } catch (IllegalArgumentException e) {
      usage();
      throw e;
    }

    // Only one argument should remain, the path to the Lucene index.
    if (args.length != 1) {
      usage();
      throw (new IllegalArgumentException("After parsing command line flags, there were "
          + args.length
          + " arguments, instead of the expected 1."));
    }
    String luceneIndex = args[0];
    logger.info("Lucene positional index being set to: " + luceneIndex);

    //If initialtermvectors is defined, read these vectors.
    if (!Flags.initialtermvectors.equals("")) {
      try {
        VectorStoreRAM vsr = new VectorStoreRAM(Flags.dimension);
        vsr.initFromFile(Flags.initialtermvectors);
        newBasicTermVectors = vsr;
        logger.info("Using trained index vectors from vector store " + Flags.initialtermvectors);
      } catch (IOException e) {
        logger.info("Could not read from vector store " + Flags.initialtermvectors);
        usage();
        throw new IllegalArgumentException();
      }
    }

    String termFile = "termtermvectors.bin";
    String docFile = "docvectors.bin";

    if (Flags.positionalmethod.equals("permutation")) termFile = "permtermvectors.bin";
    else if (Flags.positionalmethod.equals("permutation_plus_basic")) termFile = "permplustermvectors.bin";
    else if (Flags.positionalmethod.equals("directional")) termFile = "drxntermvectors.bin";

    logger.info("Lucene index = " + luceneIndex
        + "\nSeedlength = " + Flags.seedlength
        + "\nVector length = " + Flags.dimension
        + "\nMinimum frequency = " + Flags.minfrequency
        + "\nMaximum frequency = " + Flags.maxfrequency
        + "\nNumber non-alphabet characters = " + Flags.maxnonalphabetchars
        + "\nWindow radius = " + Flags.windowradius
        + "\nFields to index = " + Flags.joinStringArray(Flags.contentsfields));

    try {
      TermTermVectorsFromLucene vecStore = new TermTermVectorsFromLucene(
          luceneIndex, Flags.dimension, Flags.seedlength, Flags.minfrequency, Flags.maxfrequency,
            Flags.maxnonalphabetchars, 2 * Flags.windowradius + 1, Flags.positionalmethod,
            newBasicTermVectors, Flags.contentsfields);
      
      VectorStoreWriter vecWriter = new VectorStoreWriter(Flags.dimension);
      logger.info("Writing term vectors to " + termFile);
      vecWriter.writeVectors(termFile, vecStore);

      for (int i = 1; i < Flags.trainingcycles; ++i) {
        newBasicTermVectors = vecStore.getBasicTermVectors();
        logger.info("\nRetraining with learned term vectors ...");
        vecStore = new TermTermVectorsFromLucene(
            luceneIndex, Flags.dimension, Flags.seedlength, Flags.minfrequency, Flags.maxfrequency,
            Flags.maxnonalphabetchars, 2 * Flags.windowradius + 1, Flags.positionalmethod,
            newBasicTermVectors, Flags.contentsfields);
      }

      if (Flags.trainingcycles > 1) {
        termFile = termFile.replaceAll("\\..*", "") + Flags.trainingcycles + ".bin";
        docFile = "docvectors" + Flags.trainingcycles + ".bin";
        logger.info("Writing term vectors to " + termFile);
        vecWriter.writeVectors(termFile, vecStore);
      }

      if (!Flags.docindexing.equals("none")) {
        IncrementalDocVectors docVectors = new IncrementalDocVectors(
            vecStore, luceneIndex, Flags.contentsfields, "incremental_"+docFile, Flags.dimension);
      }
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }
}
