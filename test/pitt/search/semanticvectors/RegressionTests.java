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

package pitt.search.semanticvectors;

import java.io.File;
import java.io.IOException;
import java.lang.Process;
import java.lang.Runtime;
import java.util.*;

import org.junit.*;
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

  /**
   * Utility for taking a command, executing it as a process, and
   * returning a scanner of that processes stdout.
   */
  public static Scanner getCommandOutput(String command) {
    try {
      Runtime runtime = Runtime.getRuntime();
      Process process = runtime.exec(command);
      Scanner output = new Scanner(process.getInputStream()).useDelimiter("\\n");
      process.waitFor();
      return output;
    }
    catch (IOException e) { e.printStackTrace(); }
    catch (InterruptedException e) { e.printStackTrace(); }
    return null;
  }

  /**
   * Get a term from a search results line.
   */
  public String termFromResult(String result) {
    String[] parts = result.split(":");
    if (parts.length != 2) return null;
    return parts[1];
  }

  @Test
    public void testBuildAndSearchBasicIndex() {
    assert(!(new File("termvectors.bin")).isFile());
    assert(!(new File("docvectors.bin")).isFile());
    String[] args = {"-d", "200", "index"};
    BuildIndex.main(args);
    assert((new File("termvectors.bin")).isFile());
    assert((new File("docvectors.bin")).isFile());

    Scanner results = getCommandOutput("java pitt.search.semanticvectors.Search peter");
    // Iterate to second line and check result.
    results.next();
    String secondTerm = termFromResult(results.next());
    assert(secondTerm.equals("simon"));
  }
}