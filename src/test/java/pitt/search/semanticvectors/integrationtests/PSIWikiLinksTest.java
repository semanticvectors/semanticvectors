/**
 * Copyright 2009, The SemanticVectors AUTHORS.
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
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 * <p>
 * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 * <p>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 **/

package pitt.search.semanticvectors.integrationtests;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import pitt.search.lucene.LuceneIndexFromTriples;
import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.PSI;
import pitt.search.semanticvectors.Search;
import pitt.search.semanticvectors.SearchResult;
import pitt.search.semanticvectors.utils.VerbatimLogger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Test for PSI on wikiLinks data workings.
 */
public class PSIWikiLinksTest {
	@Before
	public void setUp() {
		try {

			String triplesTestDataPath = "src/test/resources/testdata/wikiLinks/wikiLinksTab1M.csv";
			if (Arrays.asList(new File("tmp").list()).contains("predication_index")) {

				VerbatimLogger.warning(new File("tmp").getCanonicalPath() + " already contains predication_index. "
						+ "Please delete if you want to run from clean.\n");
			} else {
				LuceneIndexFromTriples.main(new String[]{"-luceneindexpath", "tmp/predication_index", triplesTestDataPath});
			}
		} catch (IOException e) {
			e.printStackTrace();
			fail();
		}
	}

	private int psiBuildSearchGetRank(String buildCmd, String searchCmd, String targetTerm) throws IOException {
		String[] filesToBuild = new String[]{"elementalvectors.bin", "predicatevectors.bin", "semanticvectors.bin"};
		String[] buildArgs = buildCmd.split("\\s+");
		for (String fn : filesToBuild) {
			if (new File(fn).isFile()) {
				new File(fn).delete();
			}
			assertFalse((new File(fn)).isFile());
		}
		PSI.main(buildArgs);
		for (String fn : filesToBuild) assertTrue((new File(fn)).isFile());

		String[] searchArgs = searchCmd.split("\\s+");
		List<SearchResult> results = Search.runSearch(FlagConfig.getFlagConfig(searchArgs));
		int rank = 1;
		if (results.isEmpty()) {
			throw new RuntimeException("Results were empty!");
		} else {
			for (SearchResult result : results) {
				String term = (String) result.getObjectVector().getObject();
				System.out.println("Result: " + term);
				//if (term.contains(targetTerm)) break;
				++rank;
				if (rank > 20)
					break;
			}
			rank = 1;
		}

		for (String fn : filesToBuild) {
			System.err.println("Deleting file: " + fn);
			assertTrue("Failed to delete file: " + fn, (new File(fn)).delete());
		}
		return rank;
	}

	@Ignore
	@Test
	public void testBuildAndSearchRealPSIIndex() throws IOException, IllegalArgumentException {
		String buildCmd = "-minfrequency 5 -dimension 1000 -vectortype real -seedlength 500 -luceneindexpath tmp/predication_index -minfrequency 5";
		String searchCmd = "-queryvectorfile semanticvectors.bin http://dbpedia.org/resource/BMW";
		int rank = psiBuildSearchGetRank(buildCmd, searchCmd, "http://dbpedia.org/resource/BMW");
		assertTrue(rank <= 3);

	}
}

