/**
   Copyright (c) 2008, Google Inc.

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

package pitt.search.semanticvectors.integrationtests;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

import pitt.search.lucene.IndexFilePositions;
import pitt.search.lucene.LuceneIndexFromTriples;
import pitt.search.semanticvectors.VectorStoreTranslater;

/**
 * Class for running unit tests and regression tests.
 *
 * Should be run in the test/testdata/tmp working directory. Running
 * from the build.xml script with "ant run-integration-tests" ensures this.
 */
public class RunTests {
  /**
   * Important: for integration tests you need to add your test class to this list for it to
   * be run by runUnitTests().
   */
  public static Class<?>[] integrationTestClasses = {
    ThreadSafetyTest.class,
    LSATest.class,
    RegressionTests.class,
    PSITest.class,
  };

  public static boolean testDataPrepared = false;

  public static String vectorStoreName = "testtermvectors";
  public static String lucenePositionalIndexDir = "positional_index";

  public static String testVectors = "-dimension 3 -vectortype real\n"
    + "abraham|1.0|0.0|0.0\n"
    + "isaac|0.8|0.2|0.2\n";

  public static boolean checkCurrentDirEmpty() {
    File cwd = new File(".");
    if (!cwd.isDirectory()) return false;
    String[] files = cwd.list();
    if (files.length > 0) return false;
    return true;
  }

  /**
   * Convenience method for running JUnit tests and displaying failure results.
   * @return int[2] {numSuccesses, numFailures}.
   */
  private static Result runJUnitTests(Class<?> testClass) {
    Result results = org.junit.runner.JUnitCore.runClasses(testClass);
    for (Failure failure: results.getFailures()) {
      System.err.println("FAILURE!!!");
      System.err.println("FAILURE!!! Test: " + failure.toString());
      System.err.println("FAILURE!!! Message: " + failure.getMessage());
      System.err.println("FAILURE!!! Exception: ");
      failure.getException().printStackTrace();
      System.err.println("FAILURE!!!");
    }
    return results;
  }

  public static boolean prepareTestData() {
    if (testDataPrepared) return true;

    // Create basic vector store files. No Lucene / corpus dependencies here.
    try {
      BufferedWriter outBuf = new BufferedWriter(new FileWriter(vectorStoreName + ".txt"));
      outBuf.write(testVectors);
      outBuf.close();

      String[] translaterArgs = {"-TEXTTOLUCENE", vectorStoreName + ".txt", vectorStoreName + ".bin"};
      VectorStoreTranslater.main(translaterArgs);
    } catch (IOException e) {
      System.err.println("Failed to prepare test data ... abandoning tests.");
      e.printStackTrace();
      return false;
    }

    // Create Lucene indexes from test corpus, to use in index building and searching tests.
    String testDataPath = "../John";
    File testDataDir = new File(testDataPath);
    if (!testDataDir.isDirectory()) return false;
    IndexFilePositions.main(new String[] {testDataPath});
    
    testDataPath = "../nationalfacts/nationalfacts.txt";
    testDataDir = new File(testDataPath);
    LuceneIndexFromTriples.main(new String[] {testDataPath});

    testDataPrepared = true;
    return true;
  }

  public static void main(String args[]) {
    if (!checkCurrentDirEmpty()) {
      System.err.println("The test/testdata/tmp directory should be empty before running tests.\n"
          + "This may skew your results: consider cleaning up this directory first.");
    }

    int successes = 0;
    int failures = 0;

    System.err.println("Preparing test data ...");
    if (!prepareTestData()) {
      System.err.println("Failed.");
      System.exit(-1);
    }

    // Run regression tests.
    System.err.println("Running regression tests ...");
    List<Result> allResults = new ArrayList<Result>(); 
        
    for (Class<?> testClass : integrationTestClasses) {
      Result result = runJUnitTests(testClass);
      successes += result.getRunCount() - result.getFailureCount();
      failures += result.getFailureCount();
      allResults.add(result);
    }

    System.err.println("Ran all tests. Successes: " + successes + "\tFailures: " + failures);
    System.err.println("Failing tests were:");
    for (Result result : allResults) {
      for (Failure failure : result.getFailures()) {
        System.err.println("\t" + failure.toString());
      }
    }
    System.exit(0);
  }
}
