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

import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.IOException;

public class RunTests {

	public static String testDataPath = "test/testdata/";
	public static String vectorTextFile = testDataPath + "termvectors.txt";
	public static String vectorBinFile = testDataPath + "termvectors.bin";
	public static String testVectors = "-dimensions|3\n"
		+ "Abraham|1.0|0.0|0.0\n"
		+ "Isaac|0.8|0.2|0.2\n";

	private static void PrepareTestData() {
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
		}
	}

	// For each class with tests in it, add its main function below.
	public static void main(String args[]) {
		PrepareTestData();

		String[] tests = {
			"pitt.search.semanticvectors.VectorUtilsTest",
			"pitt.search.semanticvectors.VectorStoreRAMTest",
			"pitt.search.semanticvectors.VectorStoreReaderTest",
			"pitt.search.semanticvectors.VectorStoreSparseRAMTest",
			"pitt.search.semanticvectors.VectorStoreWriterTest",
			"pitt.search.semanticvectors.CompoundVectorBuilderTest",
		};
		org.junit.runner.JUnitCore.main(tests);	

		// TODO(widdows): Write cleanup method to clean testdata directory.
	}
}