/**
 * Copyright (c) 2011, The SemanticVectors AUTHORS
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

import com.ontotext.trree.sdk.PluginException;

import java.io.File;

public class VectorStoreUtils {

	public enum VectorStoreFormat {
		/** Optimized binary format created using Lucene I/O libraries. */
		LUCENE,

		/** Plan text format, used for interchange with external systems. */
		TEXT
	}

	/**
	 * Returns "$storeName.bin" if {@link FlagConfig#indexfileformat()} is {@link VectorStoreFormat#LUCENE}.
	 * Returns "$storeName.txt" if {@link FlagConfig#indexfileformat()} is {@link VectorStoreFormat#TEXT}.
	 *
	 * Method is idempotent: if file already ends with ".bin" or ".txt" as appropriate, input
	 * is returned unchanged.
	 */
	public static String getStoreFileName(String storeName, FlagConfig flagConfig) {
		switch (flagConfig.indexfileformat()) {
			case LUCENE:
				if (storeName.endsWith(".bin")) {
					return storeName;
				} else {
					return storeName + ".bin";
				}
			case TEXT:
				if (storeName.endsWith(".txt")) {
					return storeName;
				} else {
					return storeName + ".txt";
				}
			default:
				throw new IllegalStateException("Unknown -indexfileformat: " + flagConfig.indexfileformat());
		}
	}

	private static File getStoreFile(String fileName, FlagConfig flagConfig, boolean getEntityMapFile) {
		String extension = "";

		switch (flagConfig.indexfileformat()) {
			case LUCENE:
				extension = getEntityMapFile ? ".bin.map" : ".bin";
				break;
			case TEXT:
				extension = getEntityMapFile ? ".txt.map" : ".txt";
				break;
		}

		return new File(fileName + extension);
	}

	/**
	 *  Deletes initially created file and renames trained file on it.
	 *  Parameter "renameEntityMapFile" set to true handles entityMap file case.
	 * @param fileName
	 * @param flagConfig
	 */

	static void renameTrainedVectorsFile(String fileName, FlagConfig flagConfig, boolean renameEntityMapFile) {
		File initialFile = getStoreFile(fileName, flagConfig, renameEntityMapFile);
		if (initialFile.delete()) {
			getStoreFile(fileName + flagConfig.trainingcycles(), flagConfig, renameEntityMapFile)
					.renameTo(initialFile);
		} else {
			throw new PluginException("Could not delete initial file");
		}
	}
}
