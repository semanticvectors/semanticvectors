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

package pitt.search.semanticvectors;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.InterruptedException;
import java.lang.Process;
import java.lang.Runtime;

import org.junit.runner.notification.Failure;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

/**
 * Class for running unit tests and regression tests.
 *
 * Should be run in the test/testdata/tmp working directory. Running
 * from the build.xml script with "ant run-tests" ensures this.
 */
public class RunTests {
  /**
   * Important: you need to add your test class to this list for it to
   * be run by runUnitTests().
   */
  public static Class[] unitTestClasses = { VectorUtilsTest.class,
                                            VectorStoreRAMTest.class,
                                            VectorStoreReaderTest.class,
                                            VectorStoreSparseRAMTest.class,
                                            VectorStoreWriterTest.class,
                                            CompoundVectorBuilderTest.class,
                                            FlagsTest.class };

  public static Class[] regressionTestClasses = { RegressionTests.class };

  public static String vectorTextFile = "testtermvectors.txt";
  public static String vectorBinFile = "testtermvectors.bin";

  public static String testVectors = "-dimensions|3\n"
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
   * @deprecated Use ant delete target instead, it's more reliable.
   * Recursively delete a directory. Do not attempt to delete dot files.
   * Return true on success; return false and quit at first failure.
   */
  public static boolean deleteDir(File dir) {
    if (dir.isDirectory()) {
      String[] files = dir.list();
      for (String file: files) {
        // I don't know why, but sometimes dot files get generated on
        // Linux and are hard to get rid of.
        if (file.charAt(0) == '.') continue;

        boolean success = deleteDir(new File(dir, file));
        if (!success) {
          System.err.println("Failed to delete file: " + file);
          return false;
        }
      }
    }
    // The directory is now empty so delete it.
    boolean success = true;
    if (dir.getName().charAt(0) != '.') {
      success = dir.delete();
      if (!success) System.err.println("Failed to delete directory: " + dir);
    }
    return success;
  }

  /**
   * Convenience method for running JUnit tests and displaying failure results.
   * @return int[2] {num_successes, num_failures}.
   */
  private static int[] runJUnitTests(Class[] classes) {
    Result results = org.junit.runner.JUnitCore.runClasses(classes);
    for (Failure failure: results.getFailures()) {
      System.out.println("FAILURE!!!");
      System.out.println("FAILURE!!!   " + failure.toString());
      System.out.println("FAILURE!!!");
    }
    int[] resultCounts = { results.getRunCount() - results.getFailureCount(),
                           results.getFailureCount() };
    return resultCounts;
  }

  private static boolean prepareUnitTestData() {
    try {
      BufferedWriter outBuf = new BufferedWriter(new FileWriter(vectorTextFile));
      outBuf.write(testVectors);
      outBuf.close();

      VectorStoreTranslater translater = new VectorStoreTranslater();
      String[] translaterArgs = {"-TEXTTOLUCENE", vectorTextFile, vectorBinFile};
      translater.main(translaterArgs);
    } catch (IOException e) {
      System.err.println("Failed to prepare test data ... abandoning tests.");
      e.printStackTrace();
      return false;
    }
    return true;
  }

  private static boolean cleanupUnitTestData() {
    return deleteDir(new File("."));
  }

  private static boolean prepareRegressionTestData() {
    // Explicitly trying to use Runtime constructs instead of (more reliable)
    // imported class APIs, in the hope that we fail faster with Runtime constructs.
    Runtime runtime = Runtime.getRuntime();
    String testDataPath = "../John";
    File testDataDir = new File(testDataPath);
    if (!testDataDir.isDirectory()) return false;
    try {
      Process luceneIndexer = runtime.exec("java org.apache.lucene.demo.IndexFiles " + testDataPath);
      luceneIndexer.waitFor();
      luceneIndexer.destroy();

      Process lucenePositionsIndexer =
          runtime.exec("java pitt.search.lucene.IndexFilePositions " + testDataPath);
      luceneIndexer.waitFor();
      luceneIndexer.destroy();
    } catch (Exception e) {
      System.err.println("Failed to prepare regression test data ... abandoning tests.");
      e.printStackTrace();
    }
    return true;
  }

  private static boolean cleanupRegressionTestData() {
    return deleteDir(new File("."));
  }

  public static void main(String args[]) throws IOException {
    if (!checkCurrentDirEmpty()) {
      throw new IOException("The test/testdata/tmp directory should be empty before running tests.");
    }

    int successes = 0;
    int failures = 0;
    int[] scores = {0, 0};

    // Prepare and run unit tests.
    System.err.println("Preparing and running unit tests ...");
    if (!prepareUnitTestData()) System.out.println("Failed.");
    scores = runJUnitTests(unitTestClasses);
    successes += scores[0];
    failures += scores[1];

    // Prepare and run regression tests.
    System.err.println("Preparing and running regression tests ...");
    if (!prepareRegressionTestData()) System.out.println("Failed.");
    scores = runJUnitTests(regressionTestClasses);
    successes += scores[0];
    failures += scores[1];

    System.err.println("Ran all tests. Successes: " + successes + "\tFailures: " + failures);
  }
}
