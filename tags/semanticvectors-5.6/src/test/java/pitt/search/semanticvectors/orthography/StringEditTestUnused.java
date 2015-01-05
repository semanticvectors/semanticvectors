package pitt.search.semanticvectors.orthography;

import java.io.IOException;
import java.util.Enumeration;
import java.util.LinkedList;
import org.junit.Test;

import pitt.search.semanticvectors.*;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorType;
import pitt.search.semanticvectors.vectors.ZeroVectorException;

/**
 * Adapted from main() tests originally written by M Wahle.
 *
 * Created by dwiddows on 7/4/14.
 */
class StringEditTestUnused {

  // @Test - Not run until refactored.
  private void stringEditTest() throws IOException, ZeroVectorException {
    String[] args = new String[0];  // Deliberately left unfilled until this test is properly integrated.
    String[] originalArgs = args.clone();

    FlagConfig flagConfig = null;
    try {
      flagConfig = FlagConfig.getFlagConfig(args);
    } catch (IllegalArgumentException e) {
      System.err.println(e.getMessage());
      throw e;
    }

    int dimension = flagConfig.dimension();
    VectorType vType = flagConfig.vectortype();

    VectorStoreRAM theVSR = new VectorStoreRAM(flagConfig);
    VectorStoreRAM twoVSR = new VectorStoreRAM(flagConfig);

    try {
      // TODO: Replace with internal values.
      theVSR.initFromFile(flagConfig.queryvectorfile());
    } catch (IOException e) {
      e.printStackTrace();
    }

    try {
      flagConfig = FlagConfig.getFlagConfig(originalArgs);
      args = flagConfig.remainingArgs;
    } catch (IllegalArgumentException e) {
      System.err.println(e.getMessage());
      throw e;
    }

    //in case query vector file has different dimensionality or vector type
    flagConfig.setDimension(dimension);
    flagConfig.setVectortype(vType);

    VectorStoreRAM OOV = new VectorStoreRAM(flagConfig);

    System.out.println(flagConfig.minfrequency()+" "+flagConfig.maxfrequency());

    //for (int q =1; q < OV.size(); q++)
    //  for (int y= 1; y < OV.size(); y++)
    //    System.out.println(q+":"+y+"\t"+OV.get(q).getVector().measureOverlap(OV.get(y).getVector()));

    Enumeration<ObjectVector> theNum = theVSR.getAllVectors();
    //Hashtable<Integer, VectorStoreRAM> allNumberVectors = new Hashtable<Integer, VectorStoreRAM>();

    int cnt = 0;
    VectorStoreRAM theLetters = new VectorStoreRAM(flagConfig);
    NumberRepresentation NR = new NumberRepresentation(flagConfig);
    StringEdit stringEdit = new StringEdit(flagConfig, NR, theLetters);

    while (theNum.hasMoreElements()) {
      if (cnt++ % 1000 == 0) System.err.print(".."+cnt);
      ObjectVector theNext = theNum.nextElement();
      String theTerm = theNext.getObject().toString().trim();
      VectorStoreRAM OV = null;
      OV = NR.getNumberVectors(0, theTerm.length()+1);

      Vector toAdd = stringEdit.getStringVector(theTerm);

      if (flagConfig.hybridvectors())  //combine -queryvectorfile and orthographic vectors
      {
        toAdd.superpose(theVSR.getVector(theTerm), 1, null);
        toAdd.normalize();
      }

      twoVSR.putVector(theTerm,toAdd);


      Enumeration<ObjectVector> theNumbers = OV.getAllVectors();
      while (theNumbers.hasMoreElements()) {
        ObjectVector nextObjectVector = theNumbers.nextElement();
        if (OOV.getVector(nextObjectVector.getObject()) == null) {
          OOV.putVector(theTerm.length()+":"+nextObjectVector.getObject(),nextObjectVector.getVector());
        }
      }
    }

    System.out.println(flagConfig.dimension());
    System.out.println(flagConfig.vectortype());

    if (flagConfig.hybridvectors()) VectorStoreWriter.writeVectors("hybridvectors.bin", flagConfig, twoVSR);
    else VectorStoreWriter.writeVectors("editvectors.bin", flagConfig,twoVSR);
    VectorStoreWriter.writeVectorsInLuceneFormat("numbervectors.bin", flagConfig, OOV);
    VectorStoreWriter.writeVectorsInLuceneFormat("lettervectors.bin", flagConfig, theLetters);

    String[] terms = { "diabets", "dibetes",  "diabetic", "dominic", "abram", "sarai", "josh" };

    for (int a = 0; a < terms.length; a++) {
      VectorStoreRAM OV = NR.getNumberVectors(0, terms[a].length() + 1);
      VectorSearcher.VectorSearcherCosine theVSC = new VectorSearcher.VectorSearcherCosine(
          twoVSR, twoVSR, null, flagConfig, stringEdit.getStringVector(terms[a]));
      System.out.println(terms[a]);
      LinkedList<SearchResult> theResults = theVSC.getNearestNeighbors(10);

      for (int x =0; x < theResults.size(); x++) {
        System.out.println(theResults.get(x).getScore()+"\t"+theResults.get(x).getObjectVector().getObject());
      }
    }
  }
}
