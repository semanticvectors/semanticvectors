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

import java.io.IOException;
import java.util.Random;
import java.util.logging.Logger;

import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorUtils;

/**
 * Clusters search results using a simple k-means algorithm.
 * 
 * @author Dominic Widdows
 */
public class ClusterResults {
  private static final Logger logger = Logger.getLogger(ClusterResults.class.getCanonicalName());

  public static String usageMessage = "ClusterResults class in package pitt.search.semanticvectors"
      + "\nUsage: java pitt.search.semanticvectors.ClusterResults"
      + "\n                        -numsearchresults [number of search results]" 
      + "\n                        -numclusters [number of clusters]"
      + "\n                        <SEARCH ARGS>"
      + "\nwhere SEARCH ARGS is an expression passed to Search class.";

  /**
   * Thin struct for storing cluster information.
   * 
   * Initialized to null with public members: use at your own risk!
   */
  public static class Clusters {
    /** Array of ints mapping each of a list of object vectors to a cluster. */
    public int[] clusterMappings;
    /** Centroids of the clusters in question. */
    public Vector[] centroids;

    public Clusters() {
      clusterMappings = null;
      centroids = null;
    }
  }

  /** 
   * Simple k-means clustering algorithm.
   * 
   * @param objectVectors Array of object vectors to be clustered.
   * @return Integer array parallel to objectVectors saying which
   * cluster each vector belongs to.
   */
  public static Clusters kMeansCluster (ObjectVector[] objectVectors, FlagConfig flagConfig) {
    Clusters clusters = new Clusters();
    clusters.clusterMappings = new int[objectVectors.length];
    clusters.centroids = new Vector[flagConfig.getNumclusters()];
    Random rand = new Random();

    logger.info("Initializing clusters ...");

    // Initialize cluster mappings randomly.
    for (int i = 0; i < objectVectors.length; ++i) {
      int randInt = rand.nextInt();
      while (randInt == Integer.MIN_VALUE) {
        //fix strange result where abs(MIN_VALUE) returns a negative number
        randInt = rand.nextInt();
      }
      clusters.clusterMappings[i] = Math.abs(randInt) % flagConfig.getNumclusters();
    }

    logger.info("Iterating k-means assignment ...");

    // Loop that computes centroids and reassigns members.
    while (true) {
      // Clear centroid register.
      for (int i = 0; i < clusters.centroids.length; ++i) {
        clusters.centroids[i] = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension()); 
      }
      // Generate new cluster centroids.
      for (int i = 0; i < objectVectors.length; ++i) {
        clusters.centroids[clusters.clusterMappings[i]].superpose(objectVectors[i].getVector(), 1, null);
      }
      for (int i = 0; i < flagConfig.getNumclusters(); ++i) {
        clusters.centroids[i].normalize();
      }

      boolean changeFlag = false;
      // Map items to clusters.
      for (int i = 0; i < objectVectors.length; i++) {
        int j = VectorUtils.getNearestVector(objectVectors[i].getVector(), clusters.centroids);
        if (j != clusters.clusterMappings[i]) {
          changeFlag = true;
          clusters.clusterMappings[i] = j;
        }
      }
      if (changeFlag == false) {
        break;
      }
    }

    logger.info("Got to stable clusters ...");
    return clusters;
  }

  /**
   * Prints out {@link #usageMessage}
   * 
   * See also {@link Search#usageMessage}
   */
  public static void usage() {
    System.err.println(usageMessage);
  }

  /**
   * Utility method that writes cluster centroids to a file called "cluster_centroids.bin".
   */
  public static void writeCentroidsToFile(Clusters clusters, FlagConfig flagConfig) {
    VectorStoreRAM centroidsOutput = new VectorStoreRAM(flagConfig);
    for (int i = 0; i < clusters.centroids.length; ++i) {
      centroidsOutput.putVector(Integer.toString(i), clusters.centroids[i]);
    }
    try {
      VectorStoreWriter.writeVectors("cluster_centroids.bin", flagConfig, centroidsOutput);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Main function gathers search results for a particular query,
   * performs clustering on the results, and prints out results.
   * @param args
   * @see ClusterResults#usage
   */
  public static void main (String[] args) throws IllegalArgumentException {
    FlagConfig flagConfig = FlagConfig.getFlagConfig(args);
    args = flagConfig.remainingArgs;

    // Get search results, perform clustering, and print out results.		
    ObjectVector[] resultsVectors = Search.getSearchResultVectors(flagConfig, args, flagConfig.numsearchresults());
    Clusters clusters = kMeansCluster(resultsVectors, flagConfig);
    for (int i = 0; i < flagConfig.getNumclusters(); ++i) {
      System.out.println("Cluster " + i);
      for (int j = 0; j < clusters.clusterMappings.length; ++j) {
        if (clusters.clusterMappings[j] == i) {
          System.out.print(resultsVectors[j].getObject() + " ");
        }
      }
      System.out.println();
    }

    writeCentroidsToFile(clusters, flagConfig);
  }
}
