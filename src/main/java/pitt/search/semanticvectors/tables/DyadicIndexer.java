package pitt.search.semanticvectors.tables;

import pitt.search.semanticvectors.*;
import pitt.search.semanticvectors.utils.VerbatimLogger;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Enumeration;

/**
 * Experiment for reading in a 2-column dataset and creating semantic vectors for each column
 * as superpositions of elemental vectors for the other column.
 */
public class DyadicIndexer {

  // Control-flow constants introduced for experiments. Edit these to get more behavior at higher memory cost.
  private static boolean trainLeftFromRight = false;
  private static boolean trainRightFromLeft = true;

  private FlagConfig flagConfig;
  private ElementalVectorStore leftElementalVectors;
  private ElementalVectorStore rightElementalVectors;
  private VectorStoreRAM leftSemanticVectors;
  private VectorStoreRAM rightSemanticVectors;

  public static String usageMessage = "Usage: java pitt.search.examples.DyadicIndexer $TSV_FILENAME";

  public DyadicIndexer(FlagConfig flagConfig) {
    this.flagConfig = flagConfig;
    this.leftElementalVectors = new ElementalVectorStore(flagConfig);
    this.rightElementalVectors = new ElementalVectorStore(flagConfig);
    this.leftSemanticVectors = new VectorStoreRAM(flagConfig);
    this.rightSemanticVectors = new VectorStoreRAM(flagConfig);
  }

  public void addDataPair(String left, String right) {
    if (trainRightFromLeft) {
      Vector leftElementalVector = this.leftElementalVectors.getVector(left);
      if (this.rightSemanticVectors.getVector(right) == null) {
        this.rightSemanticVectors.putVector(
            right, VectorFactory.createZeroVector(this.flagConfig.vectortype(), this.flagConfig.dimension()));
      }

      Vector rightSemanticVector = this.rightSemanticVectors.getVector(right);
      rightSemanticVector.superpose(leftElementalVector, 1, null);
    }

    if (trainLeftFromRight) {
      Vector rightElementalVector = this.rightElementalVectors.getVector(right);
      if (this.leftSemanticVectors.getVector(left) == null) {
        this.leftSemanticVectors.putVector(
            left, VectorFactory.createZeroVector(this.flagConfig.vectortype(), this.flagConfig.dimension()));
      }

      Vector leftSemanticVector = this.leftSemanticVectors.getVector(left);
      leftSemanticVector.superpose(rightElementalVector, 1, null);
    }
  }

  public void normalizeSemanticVectors() {
    for (Enumeration<ObjectVector> e = this.rightSemanticVectors.getAllVectors(); e.hasMoreElements();) {
      e.nextElement().getVector().normalize();
    }

    for (Enumeration<ObjectVector> e = this.leftSemanticVectors.getAllVectors(); e.hasMoreElements();) {
      e.nextElement().getVector().normalize();
    }
  }

  public void writeVectors() throws IOException {
    if (trainLeftFromRight) {
      VectorStoreWriter.writeVectors("leftSemanticVectors", this.flagConfig, this.leftSemanticVectors);
    }

    if (trainRightFromLeft) {
      VectorStoreWriter.writeVectors("rightSemanticVectors", this.flagConfig, this.rightSemanticVectors);
    }
  }

  public static void main(String[] args) throws IOException {
    FlagConfig flagConfig = null;
    try {
      flagConfig = FlagConfig.getFlagConfig(args);
      args = flagConfig.remainingArgs;
    } catch (IllegalArgumentException e) {
      System.err.println(usageMessage);
      throw e;
    }

    if (flagConfig.remainingArgs.length != 1) {
      throw new IllegalArgumentException(
          "Wrong number of arguments after parsing command line flags.\n" + usageMessage);
    }

    DyadicIndexer dyadicIndexer = new DyadicIndexer(flagConfig);

    int lineCount = 0;
    VerbatimLogger.info("Building vector index of rows in file: " + args[0] + "\n");
    BufferedReader fileReader = new BufferedReader(new FileReader(args[0]));
    String dataLine;
    while ((dataLine = fileReader.readLine()) != null) {
      // dataLine = dataLine.toLowerCase();
      String[] entries = dataLine.split("\t");
      dyadicIndexer.addDataPair(entries[0], entries[1]);

      lineCount++;
      if (VerbatimLogger.isCounterOutput(lineCount)) {
        VerbatimLogger.info("Processed " + lineCount + " lines ... ");
      }
    }

    dyadicIndexer.normalizeSemanticVectors();
    dyadicIndexer.writeVectors();
  }
}
