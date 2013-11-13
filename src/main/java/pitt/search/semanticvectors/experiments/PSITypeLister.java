package pitt.search.semanticvectors.experiments;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.ObjectVector;
import pitt.search.semanticvectors.VectorStore;
import pitt.search.semanticvectors.VectorStoreRAM;
import pitt.search.semanticvectors.vectors.Vector;

/**
 * Experiment for trying to recover the "type" of a semantic vector in a PSI model.
 * 
 * @author dwiddows
 */
public class PSITypeLister {
    FlagConfig flagConfig;
  private VectorStore elementalVectors;
  private VectorStore semanticVectors;
  private VectorStore predicateVectors;
  
  /**
   * Initializes a {@link FlagConfig} from args and intializes appropriate elemental,
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
  
  static private HashMap<String, List<String>> typesToAttributes = new HashMap<>();
  static {
    typesToAttributes.put("COUNTRY",
      Arrays.asList(new String[] {"CAPITAL_OF-INV", "HAS_NATIONAL_ANIMAL", "HAS_CURRENCY"})); 
    typesToAttributes.put("CITY", Arrays.asList(new String[] {"CAPITAL_OF"}));
    typesToAttributes.put("CURRENCY", Arrays.asList(new String[] {"HAS_CURRENCY-INV"}));
    typesToAttributes.put("ANIMAL", Arrays.asList(new String[] {"HAS_NATIONAL_ANIMAL-INV"}));
  }
  
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
    
  public static void main(String[] args) throws IOException {
    PSITypeLister typeLister = new PSITypeLister(args);
    typeLister.printBestRelations();
  }
}
