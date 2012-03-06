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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.logging.Logger;

/**
 * This class is used for performing kMeans clustering on an entire
 * vector store.  It presumes that the vector store to be clustered is
 * represented in a file in text format (since it's unlikely that
 * you'd want to try this on large files anyway.
 * @see ClusterResults 
 */
public class ClusterVectorStore {
  private static final Logger logger = Logger.getLogger(
      ClusterVectorStore.class.getCanonicalName());

  /**
   * Prints the following usage message:
   * <code>
   * ClusterVectorStore class for clustering an entire (text) vector store. <br>
   * Usage: java.pitt.search.semanticvectors.ClusterVectorStore VECTORFILE <br>
   * Use --numclusters to change the number of clusters.
   * Do not try this for large vector stores, it will not scale well! <br>
   */
  public static void usage() {
    String message = "ClusterVectorStore class for clustering an entire (text) vector store.";
    message += "\nUsage: java.pitt.search.semanticvectors.ClusterVectorStore VECTORFILE";
    message += "\nUse --numclusters to change the number of clusters.";
    message += "\nDo not try this for large vector stores, it will not scale well!";
    System.out.println(message);
    return;
  }

  /**
   * Small utility for work with the Bible.
   * Assumes input like "bible_chapters/Matthew/Chapter_9".
   */
  private static String getBookFromPath(String path) {
    // Change this delimiter on other operating systems.
    return (new File(path)).getParent();
  }

  private static int getMaxValue(int[] values) {
    int max = values[0];
    for(int value: values) {
      if (value > max) {
        max = value;
      }
    }
    return max;
  }

  public static String[] getCluster(int ID, int[] clusterIDs, String[] names) {
    ArrayList<String> results = new ArrayList<String>();
    for (int i = 0; i < clusterIDs.length; ++i) {
      if (clusterIDs[i] == ID) {
        results.add(names[i]);
      }
    }
    String[] finalResults = new String[results.size()];
    for (int i = 0; i < results.size(); ++i) {
      finalResults[i] = results.get(i);
    }
    return finalResults;
  }

  /**
   * Measures the overlap between clusters; configured for the KJB corpus and not very general.
   */
  public static void clusterOverlapMeasure(int[] clusterIDs, ObjectVector[] vectors) {
    String[] names = new String[vectors.length];
    Hashtable<String, int[]> internalResults = new Hashtable<String, int[]>();
    for (int i = 0; i < vectors.length; ++i) {
      names[i] = getBookFromPath(vectors[i].getObject().toString());
      int[] matchAndTotal = {0, 0};
      internalResults.put(names[i], matchAndTotal);
    }
    int numClusters = getMaxValue(clusterIDs);
    for (int i = 0; i < numClusters; ++i) {
      String[] cluster = getCluster(i, clusterIDs, names);
      if (cluster.length < 2) {
        continue;
      }
      for (int j = 0; j < cluster.length; ++j) {
        for (int k = j+1; k < cluster.length; ++k) {
          int[] matchAndTotalJ = internalResults.get(cluster[j]);
          int[] matchAndTotalK = internalResults.get(cluster[k]);
          matchAndTotalJ[1]++;
          matchAndTotalK[1]++;
          if (cluster[k].equals(cluster[j])) {
            matchAndTotalJ[0]++;
            matchAndTotalK[0]++;
          }
        }
      }
    }
    for (Enumeration<String> keys = internalResults.keys(); keys.hasMoreElements();) {
      String key = keys.nextElement(); 
      int[] matchAndTotal = internalResults.get(key);
      System.out.println(key + "\t" + (float) matchAndTotal[0] / (float) matchAndTotal[1]);
    }
  }

  /**
   * Takes a number of clusters and a vector store (presumed to be
   * text format) as arguments and prints out clusters.
   */
  public static void main(String[] args) throws IllegalArgumentException {
    args = Flags.parseCommandLineFlags(args);
    if (args.length != 1) {
      System.out.println("Wrong number of arguments.");
      usage();
      return;
    }

    CloseableVectorStore vecReader;
    try {
      vecReader = VectorStoreReader.openVectorStore(args[0]);
    } catch (IOException e) {
      System.out.println("Failed to open vector store from file: '" + args[0] + "'");
      logger.info(e.getMessage());
      throw new IllegalArgumentException("Failed to parse arguments for ClusterVectorStore");
    }

    // Figure out how many vectors we need.
    logger.info("Counting vectors in store ...");
    int numVectors = vecReader.getNumVectors();

    // Allocate vector memory and read vectors from store.
    logger.info("Reading vectors into memory ...");
    ObjectVector[] resultsVectors = new ObjectVector[numVectors];
    Enumeration<ObjectVector> vecEnum = vecReader.getAllVectors();
    int offset = 0;
    while (vecEnum.hasMoreElements()) {
      resultsVectors[offset] = vecEnum.nextElement();
      // VectorUtils.printVector(resultsVectors[offset].getVector());
      ++offset;
    }
    vecReader.close();

    // Perform clustering and print out results.
    logger.info("Clustering vectors ...");
    ClusterResults.Clusters clusters = ClusterResults.kMeansCluster(resultsVectors, Flags.numclusters);
    for (int i = 0; i < Flags.numclusters; ++i) {
      System.out.println("Cluster " + i);
      for (int j = 0; j < clusters.clusterMappings.length; ++j) {
        if (clusters.clusterMappings[j] == i) {
          System.out.println(resultsVectors[j].getObject());
        }
      }
      System.out.println("\n*********\n");
    }

    //ClusterResults.writeCentroidsToFile(clusters);

    clusterOverlapMeasure(clusters.clusterMappings, resultsVectors);
  }
}
