/**
 * Copyright (c) 2007, University of Pittsburgh
 * <p>
 * All rights reserved.
 * <p>
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * <p>
 * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * <p>
 * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided
 * with the distribution.
 * <p>
 * Neither the name of the University of Pittsburgh nor the names
 * of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written
 * permission.
 * <p>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 **/

package pitt.search.semanticvectors;

import pitt.search.semanticvectors.utils.VerbatimLogger;

import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Logger;

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
			+ "\n  -vectortype [real, complex or binary]"
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
	 * @throws IOException  If filesystem resources including Lucene index are unavailable.
	 */
	public static void main(String[] args) throws IllegalArgumentException, IOException {
		FlagConfig flagConfig;
		try {
			flagConfig = FlagConfig.getFlagConfig(args);
		} catch (IllegalArgumentException e) {
			System.err.println(usageMessage);
			throw e;
		}

		buildIndex(flagConfig);
	}

	public static boolean buildIndex(FlagConfig flagConfig) throws IOException {
		if (flagConfig.luceneindexpath().isEmpty()) {
			throw (new IllegalArgumentException("-luceneindexpath must be set."));
		}

		VerbatimLogger.info("Seedlength: " + flagConfig.seedlength()
				+ ", Dimension: " + flagConfig.dimension()
				+ ", Vector type: " + flagConfig.vectortype()
				+ ", Minimum frequency: " + flagConfig.minfrequency()
				+ ", Maximum frequency: " + flagConfig.maxfrequency()
				+ ", Number non-alphabet characters: " + flagConfig.maxnonalphabetchars()
				+ ", Contents fields are: " + Arrays.toString(flagConfig.contentsfields()) + "\n");

		String termFile = flagConfig.termvectorsfile();
		String docFile = flagConfig.docvectorsfile();
		LuceneUtils luceneUtils = new LuceneUtils(flagConfig);

		try {
			// Create term vectors and write them to disk.
			TermVectorsFromLucene termVectorIndexer;
			if (!flagConfig.initialtermvectors().isEmpty()) {
				// If Flags.initialtermvectors="random" create elemental (random index)
				// term vectors. Recommended to iterate at least once (i.e. -trainingcycles = 2) to
				// obtain semantic term vectors.
				// Otherwise attempt to load pre-existing semantic term vectors.
				VerbatimLogger.info("Creating elemental term vectors ... \n");
				termVectorIndexer = TermVectorsFromLucene.createTermBasedRRIVectors(flagConfig);
			} else {
				VectorStore initialDocVectors = null;
				if (!flagConfig.initialdocumentvectors().isEmpty()) {
					VerbatimLogger.info(String.format(
							"Loading initial document vectors from file: '%s'.\n", flagConfig.initialdocumentvectors()));
					initialDocVectors = VectorStoreRAM.readFromFile(flagConfig, flagConfig.initialdocumentvectors());
					VerbatimLogger.info(String.format(
							"Loaded %d document vectors to use as elemental vectors.\n", initialDocVectors.getNumVectors()));
				}
				VerbatimLogger.info("Creating term vectors as superpositions of elemental document vectors ... \n");
				termVectorIndexer = TermVectorsFromLucene.createTermVectorsFromLucene(flagConfig, initialDocVectors);
			}

			// Should happen inside the loops ... I think. This has become messy. TODO: cleanup.
			VerbatimLogger.info("Writing term vectors to " + termFile + "\n");
			VectorStoreWriter.writeVectors(termFile, flagConfig, termVectorIndexer.getSemanticTermVectors());

			// Create doc vectors and write them to disk.
			switch (flagConfig.docindexing()) {
				case INCREMENTAL:
					IncrementalDocVectors.createIncrementalDocVectors(
							termVectorIndexer.getSemanticTermVectors(), flagConfig, luceneUtils);
					IncrementalTermVectors itermVectors;

					for (int i = 1; i < flagConfig.trainingcycles(); ++i) {
						itermVectors = new IncrementalTermVectors(flagConfig, luceneUtils);

						VectorStoreWriter.writeVectors(
								VectorStoreUtils.getStoreFileName(
										flagConfig.termvectorsfile() + flagConfig.trainingcycles(), flagConfig),
								flagConfig, itermVectors);

						IncrementalDocVectors.createIncrementalDocVectors(itermVectors, flagConfig, luceneUtils);
					}
					if (flagConfig.trainingcycles() > 0) {
						VectorStoreUtils.renameTrainedVectorsFile(flagConfig.termvectorsfile(), flagConfig);
						VectorStoreUtils.renameEntityMapVectorsFile(flagConfig.termvectorsfile(), flagConfig);
					}
					break;
				case INMEMORY:
					DocVectors docVectors = new DocVectors(termVectorIndexer.getSemanticTermVectors(), flagConfig, luceneUtils);
					for (int i = 1; i < flagConfig.trainingcycles(); ++i) {
						VerbatimLogger.info("\nRetraining with learned document vectors ...");
						termVectorIndexer = TermVectorsFromLucene.createTermVectorsFromLucene(flagConfig, docVectors);
						docVectors = new DocVectors(termVectorIndexer.getSemanticTermVectors(), flagConfig, luceneUtils);
					}

					VectorStoreWriter.writeVectors(
							VectorStoreUtils.getStoreFileName(
									flagConfig.termvectorsfile() + flagConfig.trainingcycles(), flagConfig),
							flagConfig, termVectorIndexer.getSemanticTermVectors());

					if (flagConfig.trainingcycles() > 0) {
						VectorStoreUtils.renameTrainedVectorsFile(flagConfig.termvectorsfile(), flagConfig);
						VectorStoreUtils.renameEntityMapVectorsFile(flagConfig.termvectorsfile(), flagConfig);
					}

					// At end of training, convert document vectors from ID keys to pathname keys.
					VectorStore writeableDocVectors = docVectors.makeWriteableVectorStore();
					VerbatimLogger.info("Writing doc vectors to " + docFile + "\n");
					VectorStoreWriter.writeVectors(docFile, flagConfig, writeableDocVectors);
					break;
				case NONE:
					break;
				default:
					throw new IllegalStateException(
							"No procedure defined for -docindexing " + flagConfig.docindexing());
			}
		} catch (IOException e) {
			logger.warning(e.getMessage());
			throw e;
		}

		return true;
	}
}
