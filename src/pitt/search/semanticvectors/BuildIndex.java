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

import pitt.search.semanticvectors.vectors.VectorType;

/**
 * Command line utility for creating semantic vector indexes.
 */
public class BuildIndex {
  public static Logger logger = Logger.getLogger("pitt.search.semanticvectors");

  public static String usageMessage = "\nBuildIndex class in package pitt.search.semanticvectors"
    + "\nUsage: java pitt.search.semanticvectors.BuildIndex -luceneindexpath PATH_TO_LUCENE_INDEX"
    + "\nBuildIndex creates termvectors and docvectors files in local directory."
    + "\nOther parameters that can be changed include number of dimensions, "
    + "vector type (real, binary or complex), seed length (number of non-zero entries in "
    + "basic vectors), minimum term frequency, max. number of non-alphabetical characters per term, "
    + "filtering of numeric terms (i.e. numbers), and number of iterative training cycles."
    + "\nTo change these use the command line arguments "
    + "\n  -vectortpe [real, complex or binary]"
    + "\n  -dimension [number of dimension]"
    + "\n  -seedlength [seed length]"
    + "\n  -minfrequency [minimum term frequency]"
    + "\n  -maxnonalphabetchars [number non-alphabet characters (-1 for any number)]"
    + "\n  -filternumbers [true or false]"
    + "\n  -trainingcycles [training cycles]"
    + "\n  -docindexing [incremental|inmemory|none] Switch between building doc vectors incrementally"
    + "\n        (requires positional index), all in memory (default case), or not at all";

  /**
   * Builds term vector and document vector stores from a Lucene index.
   * @param args [command line options to be parsed] then path to Lucene index
   */
  public static void main (String[] args) throws IllegalArgumentException {
    FlagConfig flagConfig = null;
    try {
      flagConfig = FlagConfig.getFlagConfig(args);
      args = flagConfig.remainingArgs;
    } catch (IllegalArgumentException e) {
      System.err.println(usageMessage);
      throw e;
    }

    if (flagConfig.luceneindexpath().isEmpty()) {
      throw (new IllegalArgumentException("-luceneindexpath must be set."));
    }

    String luceneIndex = flagConfig.luceneindexpath();
    VerbatimLogger.info("Seedlength: " + flagConfig.seedlength()
        + ", Dimension: " + flagConfig.dimension()
        + ", Vector type: " + flagConfig.vectortype()
        + ", Minimum frequency: " + flagConfig.minfrequency()
        + ", Maximum frequency: " + flagConfig.maxfrequency()
        + ", Number non-alphabet characters: " + flagConfig.maxnonalphabetchars()
        + ", Contents fields are: " + Arrays.toString(flagConfig.contentsfields()) + "\n");

    String termFile = flagConfig.termvectorsfile();
    String docFile = flagConfig.docvectorsfile();

    try{
      TermVectorsFromLucene vecStore;
      if (!flagConfig.initialtermvectors().isEmpty()) {
        // If Flags.initialtermvectors="random" create elemental (random index)
        // term vectors. Recommended to iterate at least once (i.e. -trainingcycles = 2) to
        // obtain semantic term vectors.
        // Otherwise attempt to load pre-existing semantic term vectors.
        VerbatimLogger.info("Creating term vectors ... \n");
        vecStore = TermVectorsFromLucene.createTermBasedRRIVectors(flagConfig);
      } else {
        VerbatimLogger.info("Creating elemental document vectors ... \n");
        vecStore = TermVectorsFromLucene.createTermVectorsFromLucene(flagConfig, null);
      }

      // Create doc vectors and write vectors to disk.
      if (flagConfig.docindexing().equals("incremental")) {
        VectorStoreWriter.writeVectors(termFile, flagConfig, vecStore);
        IncrementalDocVectors.createIncrementalDocVectors(
            vecStore, flagConfig, luceneIndex, flagConfig.contentsfields(), "incremental_"+docFile);
        IncrementalTermVectors itermVectors = null;
        
        for (int i = 1; i < flagConfig.trainingcycles(); ++i) {
          itermVectors = new IncrementalTermVectors(flagConfig,
              luceneIndex, flagConfig.vectortype(),
              flagConfig.dimension(), flagConfig.contentsfields(), docFile);

          VectorStoreWriter.writeVectors(
              "incremental_termvectors"+flagConfig.trainingcycles()+".bin", flagConfig, itermVectors);

        // Write over previous cycle's docvectors until final
        // iteration, then rename according to number cycles
        if (i == flagConfig.trainingcycles() - 1)
          docFile = "docvectors" + flagConfig.trainingcycles() + ".bin";

        IncrementalDocVectors.createIncrementalDocVectors(
            itermVectors, flagConfig, luceneIndex, flagConfig.contentsfields(),
            "incremental_"+docFile);
        }
      } else if (flagConfig.docindexing().equals("inmemory")) {
        DocVectors docVectors = new DocVectors(vecStore, flagConfig);
        for (int i = 1; i < flagConfig.trainingcycles(); ++i) {
          VerbatimLogger.info("\nRetraining with learned document vectors ...");
          vecStore = TermVectorsFromLucene.createTermVectorsFromLucene(flagConfig, docVectors);
          docVectors = new DocVectors(vecStore, flagConfig);
        }
        // At end of training, convert document vectors from ID keys to pathname keys.
        VectorStore writeableDocVectors = docVectors.makeWriteableVectorStore();

        if (flagConfig.trainingcycles() > 1) {
          termFile = "termvectors" + flagConfig.trainingcycles() + ".bin";
          docFile = "docvectors" + flagConfig.trainingcycles() + ".bin";
        }
        VerbatimLogger.info("Writing term vectors to " + termFile + "\n");
        VectorStoreWriter.writeVectors(termFile, flagConfig, vecStore);
        VerbatimLogger.info("Writing doc vectors to " + docFile + "\n");
        VectorStoreWriter.writeVectors(docFile, flagConfig, writeableDocVectors);
      } else {
        // Write term vectors to disk even if there are no docvectors to output.
        VerbatimLogger.info("Writing term vectors to " + termFile + "\n");
        VectorStoreWriter.writeVectors(termFile, flagConfig, vecStore);
      }
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }
}
