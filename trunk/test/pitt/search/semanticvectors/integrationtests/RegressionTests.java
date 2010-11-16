/**
   Copyright 2009, The SemanticVectors AUTHORS.
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

package pitt.search.semanticvectors.integrationtests;

import java.io.File;
import java.util.logging.*;
import java.util.*;

import org.junit.*;

import pitt.search.semanticvectors.BuildIndex;
import pitt.search.semanticvectors.BuildPositionalIndex;
import pitt.search.semanticvectors.integrationtests.RunTests;
import static org.junit.Assert.*;

/**
 * A collection of regression tests, to make sure that new options and
 * features don't break existing usage patterns.
 *
 * Should be run using "ant run-tests", which will run unit tests and
 * regression using the RunTests class working in the
 * test/testdata/tmp directory. Depends on there being appropriate
 * Lucene indexes in this directory, which are prepared by the
 * RunTests class.
 */
public class RegressionTests {
  private static Logger logger = Logger.getLogger("RegressionTests");

  @Before
  public void setUp() {
    assert(RunTests.prepareTestData());
  }

  @Test
  public void testBuildAndSearchBasicIndex() {
    assert(!(new File("termvectors.bin")).isFile());
    assert(!(new File("docvectors.bin")).isFile());
    String[] args = {"-dimension", "200", "index"};
    BuildIndex.main(args);
    assert((new File("termvectors.bin")).isFile());
    assert((new File("docvectors.bin")).isFile());

    Scanner results = TestUtils.getCommandOutput("java pitt.search.semanticvectors.Search peter");
    // Iterate to second line and check result.
    results.next();
    String secondTerm = TestUtils.termFromResult(results.next());
    assertEquals("simon", secondTerm);
  }

  @Test
  public void testBuildAndSearchPositionalIndex() {
    assert(!(new File("termtermvectors.bin")).isFile());
    assert(!(new File("incremental_docvectors.bin")).isFile());

    String[] args2 = {"-dimension", "200", "positional_index"};

    logger.info("************ Building positional index ...");

    BuildPositionalIndex.main(args2);

    logger.info("*********** Finished building positional index.");

    assert((new File("termtermvectors.bin")).isFile());
    assert((new File("incremental_docvectors.bin")).isFile());

    logger.info("Built positional index.");

    Scanner results = TestUtils.getCommandOutput(
        "java pitt.search.semanticvectors.Search -queryvectorfile termtermvectors.bin simon");
    int i = 0;
    boolean foundPeter = false;
    while (i < 5) {
      String nextTerm = TestUtils.termFromResult(results.next());
      System.err.println("\tResult term is: '" + nextTerm);
      if (nextTerm.equals("peter")) {
        foundPeter = true;
        System.err.println("Found peter in line: " + i);
        break;
      }
      ++i;
    }
    assertTrue(foundPeter);
  }

  @Test
  public void testBuildAndSearchPermutationIndex() {
    String[] args3 = {"-dimension", "200", "-positionalmethod",
                      "permutation", "positional_index"};
    BuildPositionalIndex.main(args3);

    Scanner results = TestUtils.getCommandOutput(
        "java pitt.search.semanticvectors.Search " +
        "	-searchtype permutation -queryvectorfile randomvectors.bin " +
        "	-searchvectorfile permtermvectors.bin simon ?");
    // First result should be "peter".
    String firstLine = results.next();
    String firstTerm = TestUtils.termFromResult(firstLine);
    System.err.println("\tResult term is: '" + firstTerm);
    assertEquals("peter", firstTerm);

    Scanner results2 = TestUtils.getCommandOutput(
        "java pitt.search.semanticvectors.Search " +
        "	-searchtype balanced_permutation -queryvectorfile randomvectors.bin " +
        "	-searchvectorfile permtermvectors.bin simon ?");
    // First result should be "peter".
    String firstLine2 = results2.next();
    String firstTerm2 = TestUtils.termFromResult(firstLine2);
    System.err.println("\tResult term is: '" + firstTerm2 + "'");
    assertEquals("peter", firstTerm2);
  }
}
