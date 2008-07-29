/**
   Copyright (c) 2007, University of Pittsburgh.

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

import java.lang.Math;
import java.lang.Integer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Random;

public class ClusterResults {

	/** 
	 * Simple k-means clustering algorithm. 
	 * @param objectVectors Array of object vectors to be clustered.
	 * @return Integer array parallel to objectVectors saying which
	 * cluster each vector belongs to.
	 */
	public static int[] kMeansCluster (ObjectVector[] objectVectors, int numClusters) {
		int[] clusterMappings = new int[objectVectors.length];
		Random rand = new Random();
		int dim = objectVectors[0].getVector().length;
		float[][] centroids = new float[numClusters][dim];

		System.out.println("Initializing clusters ...");

		// Initialize cluster mappings randomly.
		for (int i = 0; i < objectVectors.length; ++i) {
			clusterMappings[i] = Math.abs(rand.nextInt()) % numClusters;
		}

		System.out.println("Iterating k-means assignment ...");

		// Loop that computes centroids and reassigns members.
		while (true) {
			// Clear centroid register.
	    for (int i = 0; i < centroids.length; ++i) {
				for (int j = 0; j < dim; ++j) {
					centroids[i][j] = 0;
				}
	    }
	    // Generate new cluster centroids.
	    for (int i = 0; i < objectVectors.length; ++i) {
				for (int j = 0; j < dim; ++j) {
					centroids[clusterMappings[i]][j] += objectVectors[i].getVector()[j];
				}
	    }
	    for (int i = 0; i < numClusters; ++i) {
				centroids[i] = VectorUtils.getNormalizedVector(centroids[i]);
	    }

	    boolean changeFlag = false;
	    // Map items to clusters.
	    for (int i = 0; i < objectVectors.length; i++) {
				int j = VectorUtils.getNearestVector(objectVectors[i].getVector(), centroids);
				if (j != clusterMappings[i]) {
					changeFlag = true;
					clusterMappings[i] = j;
				}
	    }
	    if (changeFlag == false) {
				break;
	    }
		}

		System.err.println("Got to stable clusters ...");
		return clusterMappings;
	}

	/**
	 * Prints the following usage message: <br>
	 * <code>
	 * ClusterResults class in package pitt.search.semanticvectors
	 * <br>Usage: java pitt.search.semanticvectors.BuildIndex -results [number of search results]
	 *                                                        -clusters [number of clusters]
	 *                                                        &lt;SEARCH ARGS&gt;
	 * where SEARCH ARGS is an expression passed to Search class.
	 * </code>
	 * @see Search#usage
	 */
	public static void usage(){
		String usageMessage = "\nClusterResults class in package pitt.search.semanticvectors"
			+ "\nUsage: java pitt.search.semanticvectors.ClusterResults -results [number of search results]"
			+ "\n                                                       -clusters [number of clusters]"
			+ "\n                                                       <SEARCH ARGS>"
			+ "\nwhere SEARCH ARGS is an expression passed to Search class.";
		System.out.println(usageMessage);
		System.exit(-1);
	}

	/**
	 * Main function gathers search results for a particular query,
	 * performs clustering on the results, and prints out results.
	 * @param args
	 * @see ClusterResults#usage
	 */
	public static void main(String[] args) {
		int numClusters = 0, numResults = 0;

		// Parse query args. Make sure you put the two clustering
		// arguments before any of the search arguments.
    int argc = 0;
		if (args.length < 5) { usage(); }
    while (args[argc].substring(0, 1).equals("-")) {
      if (args[argc].equals("-clusters")) {
        numClusters = Integer.parseInt(args[argc + 1]);
        argc += 2;
      }
      else if (args[argc].equals("-results")) {
        numResults = Integer.parseInt(args[argc + 1]);
        argc += 2;
      }
			else {
				if (argc == 4 && numClusters != 0 && numResults != 0) {
					break;
				}
				else { usage(); }
			}
		}

		String[] searchArgs = new String[args.length - argc];
		for (int i = 0; i < args.length - argc;  ++i) {
			searchArgs[i] = args[argc + i];
		}

		// Get search results, perform clustering, and print out results.
		ObjectVector[] resultsVectors = Search.getSearchResultVectors(searchArgs, numResults);
		int[] clusterMappings = kMeansCluster(resultsVectors, numClusters);
		for (int i = 0; i < numClusters; ++i) {
	    System.out.println("Cluster " + i);
	    for (int j = 0; j < clusterMappings.length; ++j) {
				if (clusterMappings[j] == i) {
					System.out.print(resultsVectors[j].getObject() + " ");
				}
	    }
	    System.out.println();
		}
	}
}