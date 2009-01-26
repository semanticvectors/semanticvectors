/**
   Copyright 2009, SemanticVectors AUTHORS.
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

import org.junit.*;
import static org.junit.Assert.*;
import java.io.IOException;

public class VectorStoreReaderTest {

	@Test
		public void TestReadFromTestData() {
		System.err.println("Running tests for VectorStoreReader");
		// TODO(dwiddows): I don't like having a test inside a try/catch,
		// but don't know what to do about this. 
		//
		// The "tested" variable is meant to track whether the test have
		// really been run or not.
		boolean tested = false;
		try {
			VectorStoreReader reader = new VectorStoreReader(RunTests.vectorBinFile);
			assertEquals(2, reader.getNumVectors());
			float[] abraham = reader.getVector("Abraham");
			assertEquals(1.0f, abraham[0], 0.01);
			tested = true;
		} catch (IOException e) {
			e.printStackTrace();
		}
		assertTrue(tested);
	}

	@Test
		public void TestMultipleOpens() {
		boolean tested = false;
		try {
			VectorStoreReader reader = new VectorStoreReader(RunTests.vectorBinFile);
			VectorStoreReader reader2 = new VectorStoreReader(RunTests.vectorBinFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}