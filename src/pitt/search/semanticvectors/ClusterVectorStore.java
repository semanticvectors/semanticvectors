/**
   Copyright (c) 2008, University of Pittsburgh.

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

import java.lang.Integer;
import java.util.Enumeration;

public class ClusterVectorStore {
	public static void usage() {
		String message = "ClusterVectorStore class for clustering an entire (text) vector store.";
		message += "\nUsage: java.pitt.search.semanticvectors.ClusterVectorStore NUMCLUSTERS VECTORFILE";
		message += "\nDo not try this for large vectors, it will not scale well!";
	}

	public static void main(String[] args) {
		int numClusters = 0;
		VectorStoreReaderText vecReader = null;

		// Parse query args. Make sure you put the two clustering
		// arguments before any of the search arguments.
		if (args.length != 2) {
			usage();
		}
		numClusters = Integer.parseInt(args[0]);
		try {
			vecReader = new VectorStoreReaderText(args[1]);
		} catch (Exception e) {
			System.out.println("Failed to open vector store from file: '" + args[1] + "'");
			e.printStackTrace();
		}

		// Figure out how many vectors we need.
		System.err.println("Counting vectors in store ...");
		Enumeration<ObjectVector> vecEnum = vecReader.getAllVectors();
		int numVectors = 0;
		while (vecEnum.hasMoreElements()) {
			vecEnum.nextElement();
			++numVectors;
		}

		// Allocate vector memory and read vectors from store.
		System.err.println("Reading vectors into memory ...");
		ObjectVector[] resultsVectors = new ObjectVector[numVectors];
		vecEnum = vecReader.getAllVectors();
		int offset = 0;
		while (vecEnum.hasMoreElements()) {
			resultsVectors[offset] = vecEnum.nextElement();
			// VectorUtils.printVector(resultsVectors[offset].getVector());
			++offset;
		}

		// Peform clustering and print out results.
		System.err.println("Clustering vectors ...");
		int[] clusterMappings = ClusterResults.kMeansCluster(resultsVectors, numClusters);
		for (int i = 0; i < numClusters; ++i) {
	    System.out.println("Cluster " + i);
	    for (int j = 0; j < clusterMappings.length; ++j) {
				if (clusterMappings[j] == i) {
					System.out.println(resultsVectors[j].getObject());
				}
	    }
	    System.out.println("\n*********\n");
		}
	}
}