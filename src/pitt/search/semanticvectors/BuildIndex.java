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
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * Command line utility for creating semantic vector indexes.
 */
public class BuildIndex {
  public static Logger logger = Logger.getLogger("pitt.search.semanticvectors");
  
  /**
   * Prints the following usage message:
   * <code>
   * <br> BuildIndex class in package pitt.search.semanticvectors
   * <br> Usage: java pitt.search.semanticvectors.BuildIndex PATH_TO_LUCENE_INDEX
   * <br> BuildIndex creates termvectors and docvectors files in local directory.
   * <br> Other parameters that can be changed include vector length,
   * <br>     (number of dimensions), seed length (number of non-zero
   * <br>     entries in basic vectors), minimum term frequency,
   * <br>     and number of iterative training cycles.
   * <br> To change these use the following command line arguments:
   * <br> -dimension [number of dimensions]
   * <br> -seedlength [seed length]
   * <br> -minfrequency [minimum term frequency]
   * <br> -maxnonalphabetchars [number non-alphabet characters (-1 for any number)]
   * <br> -trainingcycles [training cycles]
   * <br> -docindexing [incremental|inmemory] Switch between building doc vectors incrementally"
   * <br>       (requires positional index) or all in memory (default case).
   * </code>
   */
  public static void usage() {
    String usageMessage = "\nBuildIndex class in package pitt.search.semanticvectors"
        + "\nUsage: java pitt.search.semanticvectors.BuildIndex PATH_TO_LUCENE_INDEX"
        + "\nBuildIndex creates termvectors and docvectors files in local directory."
        + "\nOther parameters that can be changed include vector length,"
        + "\n    (number of dimensions), seed length (number of non-zero"
        + "\n    entries in basic vectors), minimum term frequency,"
        + "\n    and number of iterative training cycles."
        + "\nTo change these use the command line arguments "
        + "\n  -dimension [number of dimensions]"
        + "\n  -seedlength [seed length]"
        + "\n  -minfrequency [minimum term frequency]"
        + "\n  -maxnonalphabetchars [number non-alphabet characters (-1 for any number)]"
        + "\n  -trainingcycles [training cycles]"
        + "\n  -docindexing [incremental|inmemory|none] Switch between building doc vectors incrementally"
        + "\n        (requires positional index), all in memory (default case), or not at all";
    System.out.println(usageMessage);
  }

  /**
   * Builds term vector and document vector stores from a Lucene index.
   * @param args
   * @see BuildIndex#usage
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
      throw (new IllegalArgumentException("After parsing command line flags, there were " + args.length
                                          + " arguments, instead of the expected 1."));
    }

    String luceneIndex = args[0];
    logger.info("Seedlength = " + Flags.seedlength 
        + "\nDimension = " + Flags.dimension
        + "\nMinimum frequency = " + Flags.minfrequency
        + "\nMaximum frequency = " + Flags.maxfrequency
        + "\nNumber non-alphabet characters = " + Flags.maxnonalphabetchars
        + "\nContents fields are: " + Arrays.toString(Flags.contentsfields));

    String termFile = "termvectors.bin";
    String docFile = "docvectors.bin";
    VectorStoreRAM initialdocvectors = null;

    try{
      TermVectorsFromLucene vecStore;
      if (Flags.initialtermvectors.length() > 0) {
        // If Flags.initialtermvectors="random" create elemental (random index)
        // term vectors. Recommended to iterate at least once (i.e. -trainingcycles = 2) to
        // obtain semantic term vectors.
        // Otherwise attempt to load pre-existing semantic term vectors.
        logger.info("Creating term vectors ...");
        vecStore = TermVectorsFromLucene.createTermBasedRRIVectors(
            luceneIndex, Flags.dimension, Flags.seedlength, Flags.minfrequency, Flags.maxfrequency,
            Flags.maxnonalphabetchars, Flags.initialtermvectors, Flags.contentsfields);
      } else {
        logger.info("Creating elemental document vectors ...");
        vecStore = TermVectorsFromLucene.createTermVectorsFromLucene(
            luceneIndex, Flags.dimension, Flags.seedlength, Flags.minfrequency, Flags.maxfrequency,
            Flags.maxnonalphabetchars, null, Flags.contentsfields);
      }

      // Create doc vectors and write vectors to disk.
      VectorStoreWriter vecWriter = new VectorStoreWriter(Flags.dimension);
      if (Flags.docindexing.equals("incremental")) {
        logger.info("Writing term vectors to " + termFile);
        vecWriter.writeVectors(termFile, vecStore);
        IncrementalDocVectors idocVectors = new IncrementalDocVectors(
            vecStore, luceneIndex, Flags.contentsfields,
            "incremental_"+docFile, Flags.dimension);
        IncrementalTermVectors itermVectors = null;

        for (int i = 1; i < Flags.trainingcycles; ++i) {
          itermVectors = new IncrementalTermVectors(luceneIndex,  Flags.dimension,
                                                    Flags.contentsfields, "incremental_"+docFile);

          new VectorStoreWriter(Flags.dimension).writeVectors(
              "incremental_termvectors"+Flags.trainingcycles+".bin", itermVectors);

        // Write over previous cycle's docvectors until final
        // iteration, then rename according to number cycles
        if (i == Flags.trainingcycles-1) docFile = "docvectors"+Flags.trainingcycles+".bin";

        idocVectors = new IncrementalDocVectors(
            itermVectors, luceneIndex, Flags.contentsfields,
            "incremental_"+docFile, Flags.dimension);
        }
      } else if (Flags.docindexing.equals("inmemory")) {
        DocVectors docVectors = new DocVectors(vecStore);
        for (int i = 1; i < Flags.trainingcycles; ++i) {
          logger.info("\nRetraining with learned document vectors ...");
          vecStore = TermVectorsFromLucene.createTermVectorsFromLucene(
              luceneIndex, Flags.dimension, Flags.seedlength, Flags.minfrequency,
              Flags.maxfrequency, Flags.maxnonalphabetchars, docVectors, Flags.contentsfields);
          docVectors = new DocVectors(vecStore);
        }
        // At end of training, convert document vectors from ID keys to pathname keys.
        VectorStore writeableDocVectors = docVectors.makeWriteableVectorStore();

        if (Flags.trainingcycles > 1) {
          termFile = "termvectors" + Flags.trainingcycles + ".bin";
          docFile = "docvectors" + Flags.trainingcycles + ".bin";
        }
        logger.info("Writing term vectors to " + termFile);
        vecWriter.writeVectors(termFile, vecStore);
        logger.info("Writing doc vectors to " + docFile);
        vecWriter.writeVectors(docFile, writeableDocVectors);
      } else {
        // Write term vectors to disk even if there are no docvectors to output.
        logger.info("Writing term vectors to " + termFile);
        vecWriter.writeVectors(termFile, vecStore);
      }
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }
}
