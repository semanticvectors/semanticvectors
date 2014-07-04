package pitt.search.semanticvectors.orthography;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import pitt.search.semanticvectors.*;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Class for representing characters in terms of their neighbors, and then building string representations
 * from these characters.
 */
public class CharRepresentation {
  public static final String ENCODING = "utf8";

  private FlagConfig flagConfig;
  private VectorStoreDeterministic elementalCharVectors;
  private VectorStoreRAM semanticCharVectors;
  private VectorStoreOrthographical semanticTermVectors;

  public CharRepresentation(FlagConfig config) {
    this.flagConfig = config;
    this.elementalCharVectors = new VectorStoreDeterministic(this.flagConfig);
    this.semanticCharVectors = new VectorStoreRAM(this.flagConfig);
  }

  private static String letterAt(String source, int i) {
    String ret = "" + source.charAt(i);
    return ret;
  }

  private void addStringToCharRep(String inputString) {
    for (int i = 0; i < inputString.length(); ++i) {
      if (semanticCharVectors.getVector(letterAt(inputString, i)) == null) {
        semanticCharVectors.putVector(
            letterAt(inputString, i), VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension()));
      }
      if (i != 0) {
        Vector summand = elementalCharVectors.getVector(letterAt(inputString, i - 1));
        semanticCharVectors.getVector(letterAt(inputString, i)).superpose(summand, 1, null);
      }
      if (i != inputString.length() - 1) {
        Vector summand = elementalCharVectors.getVector(letterAt(inputString, i + 1));
        semanticCharVectors.getVector(letterAt(inputString, i)).superpose(summand, 1, null);
      }
    }
  }

  private void addHtmlFileToCharRep(File inputFile) throws IOException {
    Document doc = Jsoup.parse(inputFile, ENCODING);
    String innerText = doc.text();
    java.util.StringTokenizer tokenizer = new java.util.StringTokenizer(innerText, " ");
    while (tokenizer.hasMoreTokens()) {
      String token = tokenizer.nextToken();
      addStringToCharRep(token);
    }
  }

  private void addFileToCharRep(File inputFile) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile)));
    String line;
    while ((line = reader.readLine()) != null)  {
      addStringToCharRep(line);
    }
    reader.close();
  }

  private void addStringToTermRep(String text) {
    java.util.StringTokenizer tokenizer = new java.util.StringTokenizer(text, " ");
    while (tokenizer.hasMoreTokens()) {
      String token = tokenizer.nextToken();
      // This public getter has the side-effect of putting the token vector in the cache.
      if (!this.semanticTermVectors.containsVector(token)) {
        this.semanticTermVectors.getVector(token);
      }
    }
  }

  private void addHtmlFileToTermRep(File inputFile) throws IOException {
    Document doc = Jsoup.parse(inputFile, ENCODING);
    String innerText = doc.text();
    addStringToTermRep(innerText);
  }

  private void addFileToTermRep(File inputFile) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), ENCODING));
    String line;
    while ((line = reader.readLine()) != null) {
      addStringToTermRep(line);
    }
    reader.close();
  }

  public static void listDirRecursive(File rootDir, List<File> listToAppend) {
    File[] contents = rootDir.listFiles();

    for (File file : rootDir.listFiles()) {
      if (file.isFile()) {
        listToAppend.add(file);
      } else if (file.isDirectory()) {
        listDirRecursive(file, listToAppend);
      }
    }
  }

  /**
   * Creates
   * @param args Command line arguments parsed by {@link FlagConfig}, followed by a single argument
   *             which must be the path to the root of files to be indexed.
   * @throws Exception If arguments are ill-formed or path is not found.
   */
  public static void main(String[] args) throws IOException {
    FlagConfig flagConfig = FlagConfig.getFlagConfig(args);
    List<File> files = new ArrayList<>();
    File rootDir = new File(flagConfig.remainingArgs[0]);
    if (!rootDir.exists()) {
      throw new IllegalArgumentException("Not a file or directory: '" + args[0] + "'.");
    }
    listDirRecursive(rootDir, files);

    CharRepresentation charRepresentation = new CharRepresentation(flagConfig);
    for (File file : files) {
      System.out.println("Indexing chars from: " + file.getAbsolutePath());
      charRepresentation.addHtmlFileToCharRep(file);
    }
    VectorStoreWriter.writeVectors("charvectors", flagConfig, charRepresentation.semanticCharVectors);

    charRepresentation.semanticTermVectors =
        new VectorStoreOrthographical(flagConfig, charRepresentation.semanticCharVectors);
    for (File file : files) {
      System.out.println("Indexing words from: " + file.getAbsolutePath());
      charRepresentation.addHtmlFileToTermRep(file);
    }
    VectorStoreWriter.writeVectors("termvectors_semchar", flagConfig, charRepresentation.semanticTermVectors);

    charRepresentation.semanticTermVectors =
        new VectorStoreOrthographical(flagConfig, charRepresentation.elementalCharVectors);
    for (File file : files) {
      System.out.println("Indexing words from: " + file.getAbsolutePath());
      charRepresentation.addHtmlFileToTermRep(file);
    }
    VectorStoreWriter.writeVectors("termvectors_elemchar", flagConfig, charRepresentation.semanticTermVectors);
  }
}
