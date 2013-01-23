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
import java.util.Arrays;
import java.util.logging.Logger;

import pitt.search.semanticvectors.vectors.VectorType;

/**
 * Command line utility for creating semantic vector indexes using the
 * sliding context window approach (see work on HAL, and by Schutze).
 */
public class BuildPositionalIndex {
  public static final Logger logger = Logger.getLogger(
      BuildPositionalIndex.class.getCanonicalName());
  static VectorStore newBasicTermVectors = null;

  public static String usageMessage =
    "BuildPositionalIndex class in package pitt.search.semanticvectors"
    + "\nUsage: java pitt.search.semanticvectors.BuildPositionalIndex -luceneindexpath PATH_TO_LUCENE_INDEX"
    + "\nBuildPositionalIndex creates file termtermvectors.bin in local directory."
    + "\nOther parameters that can be changed include"
    + "\n    windowlength (size of sliding context window),"
    + "\n    dimension (number of dimensions), vectortype (real, complex, binary)"
    + "\n    seedlength (number of non-zero entries in basic vectors),"
    + "\n    minimum term frequency.\n"
    + "\nTo change these use the command line arguments "
    + "\n  -vectortype [real, complex, or binary]"
    + "\n  -dimension [number of dimensions]"
    + "\n  -seedlength [seed length]"
    + "\n  -minfrequency [minimum term frequency]"
    + "\n  -initialtermvectors [name of preexisting vectorstore for term vectors]"
    + "\n  -windowradius [window size]"
    + "\n  -positionalmethod [positional indexing method: basic (default), directional (HAL), permutation (Sahlgren 2008)";

  /**
   * Builds term vector stores from a Lucene index - this index must
   * contain TermPositionVectors.
   * @param args
   */
  public static void main (String[] args) throws IllegalArgumentException {
    FlagConfig flagConfig;
    try {
      flagConfig = FlagConfig.getFlagConfig(args);
      args = flagConfig.remainingArgs;
    } catch (IllegalArgumentException e) {
      System.out.println(usageMessage);
      throw e;
    }

    if (flagConfig.getLuceneindexpath().isEmpty()) {
      throw (new IllegalArgumentException("-luceneindexpath must be set."));
    }
    String luceneIndex = flagConfig.getLuceneindexpath();

    // If initialtermvectors is defined, read these vectors.
    if (!flagConfig.getInitialtermvectors().isEmpty()) {
      try {
        VectorStoreRAM vsr = new VectorStoreRAM(flagConfig);
        vsr.initFromFile(flagConfig.getInitialtermvectors());
        newBasicTermVectors = vsr;
        VerbatimLogger.info("Using trained index vectors from vector store " + flagConfig.getInitialtermvectors());
      } catch (IOException e) {
        logger.info("Could not read from vector store " + flagConfig.getInitialtermvectors());
        System.out.println(usageMessage);
        throw new IllegalArgumentException();
      }
    }

    String termFile = flagConfig.getTermtermvectorsfile();
    String docFile = flagConfig.getDocvectorsfile();

    if (flagConfig.getPositionalmethod().equals("permutation")) termFile = flagConfig.getPermutedvectorfile();
    else if (flagConfig.getPositionalmethod().equals("permutation_plus_basic")) termFile = flagConfig.getPermplustermvectorfile();
    else if (flagConfig.getPositionalmethod().equals("directional")) termFile = flagConfig.getDirectionalvectorfile();

    VerbatimLogger.info("Building positional index, Lucene index: " + luceneIndex
        + ", Seedlength: " + flagConfig.getSeedlength()
        + ", Vector length: " + flagConfig.getDimension()
        + ", Vector type: " + flagConfig.getVectortype()
        + ", Minimum term frequency: " + flagConfig.getMinfrequency()
        + ", Maximum term frequency: " + flagConfig.getMaxfrequency()
        + ", Number non-alphabet characters: " + flagConfig.getMaxnonalphabetchars()
        + ", Window radius: " + flagConfig.getWindowradius()
        + ", Fields to index: " + Arrays.toString(flagConfig.getContentsfields())
        + "\n");

    try {
      TermTermVectorsFromLucene vecStore = new TermTermVectorsFromLucene(
          flagConfig,
          luceneIndex,  VectorType.valueOf(flagConfig.getVectortype().toUpperCase()),
          flagConfig.getDimension(), flagConfig.getSeedlength(), flagConfig.getMinfrequency(), flagConfig.getMaxfrequency(),
          flagConfig.getMaxnonalphabetchars(), flagConfig.getFilteroutnumbers(), 2 * flagConfig.getWindowradius() + 1, flagConfig.getPositionalmethod(),
          newBasicTermVectors, flagConfig.getContentsfields());
      
      VectorStoreWriter.writeVectors(termFile, flagConfig, vecStore);

      for (int i = 1; i < flagConfig.getTrainingcycles(); ++i) {
        newBasicTermVectors = vecStore.getBasicTermVectors();
        VerbatimLogger.info("\nRetraining with learned term vectors ...");
        vecStore = new TermTermVectorsFromLucene(
            flagConfig,
            luceneIndex,  VectorType.valueOf(flagConfig.getVectortype().toUpperCase()),
            flagConfig.getDimension(), flagConfig.getSeedlength(), flagConfig.getMinfrequency(), flagConfig.getMaxfrequency(),
            flagConfig.getMaxnonalphabetchars(), flagConfig.getFilteroutnumbers(), 2 * flagConfig.getWindowradius() + 1, flagConfig.getPositionalmethod(),
            newBasicTermVectors, flagConfig.getContentsfields());
      }

      if (flagConfig.getTrainingcycles() > 1) {
        termFile = termFile.replaceAll("\\..*", "") + flagConfig.getTrainingcycles() + ".bin";
        docFile = "docvectors" + flagConfig.getTrainingcycles() + ".bin";
        VectorStoreWriter.writeVectors(termFile, flagConfig, vecStore);
      }

      if (!flagConfig.getDocindexing().equals("none")) {
        IncrementalDocVectors.createIncrementalDocVectors(
            vecStore, flagConfig, luceneIndex, flagConfig.getContentsfields(), docFile);
      }
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }
}
