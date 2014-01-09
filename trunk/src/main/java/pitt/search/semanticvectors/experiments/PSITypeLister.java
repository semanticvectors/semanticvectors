package pitt.search.semanticvectors.experiments;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.LuceneUtils;
import pitt.search.semanticvectors.ObjectVector;
import pitt.search.semanticvectors.VectorSearcher;
import pitt.search.semanticvectors.VectorSearcher.VectorSearcherCosine;
import pitt.search.semanticvectors.VectorStore;
import pitt.search.semanticvectors.VectorStoreRAM;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorUtils;

/**
 * Experiment for trying to recover the "type" of a semantic vector in a PSI model.
 *
 * Also contains some experiments on negation in PSI.
 *
 * @author dwiddows
 */
public class PSITypeLister {
    FlagConfig flagConfig;
  private VectorStore elementalVectors;
  private VectorStore semanticVectors;
  private VectorStore predicateVectors;
  
  /**
   * Initializes a {@link FlagConfig} from args and initializes appropriate elemental,
   * semantic, and predicate vector stores.
   */
  public PSITypeLister(String[] args) throws IOException {
    flagConfig = FlagConfig.getFlagConfig(args);
    elementalVectors = VectorStoreRAM.readFromFile(flagConfig, flagConfig.elementalvectorfile());
    semanticVectors = VectorStoreRAM.readFromFile(flagConfig, flagConfig.semanticvectorfile());
    predicateVectors = VectorStoreRAM.readFromFile(flagConfig, flagConfig.boundvectorfile());
  }
  
  /**
   * Prints all relations between combinations of S(subject) * E(predicate) that give
   * a match with an E(object) greater than {@link FlagConfig#searchresultsminscore()}. 
   */
  public void printBestRelations() {    
    for (ObjectVector semanticVector : Collections.list(semanticVectors.getAllVectors())) {
      List<String> attributes = new ArrayList<>();
      for (ObjectVector predicateVector : Collections.list(predicateVectors.getAllVectors())) {
        Vector probeVector = semanticVector.getVector().copy();
        probeVector.release(predicateVector.getVector());
        for (ObjectVector elementalVector : Collections.list(elementalVectors.getAllVectors())) {
          double relationScore = probeVector.measureOverlap(elementalVector.getVector());
          if (relationScore > flagConfig.searchresultsminscore()) {
            System.out.println(String.format("\t%4.3f\t%s : %s : %s", relationScore, 
                semanticVector.getObject(), predicateVector.getObject(), elementalVector.getObject()));
            attributes.add((String) predicateVector.getObject());
          }
        }
      }
      String type = getProposedType(attributes);
      if (attributes.size() != 0) {
        System.out.println(String.format("'%s' is therefore a '%s'\n",
          semanticVector.getObject(), type));
      }
    }
  }

  /** Data structure that encodes mapping of which attributes are expected for each type. */
  static private HashMap<String, List<String>> typesToAttributes = new HashMap<>();
  static {
    typesToAttributes.put("COUNTRY",
      Arrays.asList(new String[] {"CAPITAL_OF-INV", "HAS_NATIONAL_ANIMAL", "HAS_CURRENCY"})); 
    typesToAttributes.put("CITY", Arrays.asList(new String[] {"CAPITAL_OF"}));
    typesToAttributes.put("CURRENCY", Arrays.asList(new String[] {"HAS_CURRENCY-INV"}));
    typesToAttributes.put("ANIMAL", Arrays.asList(new String[] {"HAS_NATIONAL_ANIMAL-INV"}));
  }

  /**
   * Returns a string representing the type of an item given a list of attributes it has.
   **/
  private String getProposedType(List<String> matchedAttributes) {
    String result = null;
    for (Map.Entry<String, List<String>> entry : typesToAttributes.entrySet()) {
      for (String attribute : matchedAttributes) {
        if (entry.getValue().contains(attribute)) {
          if (result == null || result == entry.getKey()) {
            result = entry.getKey();
          }
        }
      }
    }
    return result;
  }

  /**
   * Separate method for hard-coded experiments on negation, included here for ease.
   */
  public static void notUsDollar(PSITypeLister typeLister, FlagConfig flagConfig) {
    Vector dollar = typeLister.semanticVectors.getVector("united_states_dollar");
    Vector usesCurrency = typeLister.predicateVectors.getVector("HAS_CURRENCY-INV");
    Vector countryUsesDollar = dollar.copy();
    countryUsesDollar.release(usesCurrency);

    System.out.println("Results without negation ...");
    for (ObjectVector testVector : Collections.list(
        typeLister.elementalVectors.getAllVectors())) {
      double score = testVector.getVector().measureOverlap(countryUsesDollar);
      if (score > flagConfig.searchresultsminscore()) {
        System.out.println(score + " : " + testVector.getObject());
      }
    }

    ArrayList<Vector> setToNegate = new ArrayList<>();
    Vector usa = typeLister.elementalVectors.getVector("united_states");
    setToNegate.add(usa);
    setToNegate.add(countryUsesDollar);
    VectorUtils.orthogonalizeVectors(setToNegate);
    countryUsesDollar.normalize();
    System.out.println("Results with negation ...");
    for (ObjectVector testVector : Collections.list(
        typeLister.elementalVectors.getAllVectors())) {
      double score = testVector.getVector().measureOverlap(countryUsesDollar);
      if (score > flagConfig.searchresultsminscore()) {
        System.out.println(score + " : " + testVector.getObject());
      }
    }
  }
  
  public static void main(String[] args) throws IOException {
    PSITypeLister typeLister = new PSITypeLister(args);
    typeLister.printBestRelations();
    //notUsDollar(typeLister, FlagConfig.getFlagConfig(args));
  }
}
