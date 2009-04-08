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

import org.junit.runner.JUnitCore;

public class RunTests {

	public static String tmpTestDataPath = "tmp/";  // Perhaps don't need this if set in build.xml file.
	public static String vectorTextFile = tmpTestDataPath + "termvectors.txt";
	public static String vectorBinFile = tmpTestDataPath + "termvectors.bin";

	public static String testVectors = "-dimensions|3\n"
		+ "abraham|1.0|0.0|0.0\n"
		+ "isaac|0.8|0.2|0.2\n";

	// Recursively delete a directory.
	// Return true on success; return false and quit at first failure.
	public static boolean deleteDir(File dir) {
		if (dir.isDirectory()) {
			String[] subDirs = dir.list();
			for (String subDir: subDirs) {
				boolean success = deleteDir(new File(dir, subDir));
				if (!success) {
					return false;
				}
			}
		}
		// The directory is now empty so delete it
		return dir.delete();
	}


	private static void prepareUnitTestData() {
		try {
			File tmpDir = new File(tmpTestDataPath);
			tmpDir.mkdir();
			BufferedWriter outBuf = new BufferedWriter(new FileWriter(vectorTextFile));
			outBuf.write(testVectors);
			outBuf.close();

			VectorStoreTranslater translater = new VectorStoreTranslater();
			String[] translaterArgs = {"-TEXTTOLUCENE", vectorTextFile, vectorBinFile};
			translater.main(translaterArgs);
		} catch (IOException e) {
			System.err.println("Failed to prepare test data ... abandoning tests.");
			e.printStackTrace();
		}
	}

	private static void cleanupUnitTestData() {
		deleteDir(new File(tmpTestDataPath));
	}

	private static void prepareRegressionTestData() {
		// Explicitly trying to use Runtime constructs instead of (more reliable)
		// imported class APIs, in the hope that we fail faster with Runtime constructs.
		Runtime runtime = Runtime.getRuntime();
		try {
			Process luceneIndexer = runtime.exec("java org.apache.lucene.demo.IndexFiles John");
			luceneIndexer.waitFor();
		} catch (Exception e) {
			System.err.println("Failed to prepare regression test data ... abandoning tests.");
			e.printStackTrace();
		}
	}

	private static void cleanupRegressionTestData() {
		deleteDir(new File(tmpTestDataPath));
		deleteDir(new File("index/"));
	}

	public static void main(String args[]) {
		// Prepare, run, and cleanup after unit tests.
		System.err.println("Preparing and running unit tests ...");
		prepareUnitTestData();

		JUnitCore runner = new JUnitCore();
		Class[] unitTestClasses = {VectorUtilsTest.class, 
															 VectorStoreRAMTest.class,
															 VectorStoreReaderTest.class,
															 VectorStoreSparseRAMTest.class,
															 VectorStoreWriterTest.class,
															 CompoundVectorBuilderTest.class,
															 FlagsTest.class};
		runner.run(unitTestClasses);
		cleanupUnitTestData();

		// Prepare, run, and cleanup after regression tests. 
		System.err.println("Preparing and running regression tests ...");
		prepareRegressionTestData();

		cleanupRegressionTestData();
	}
}