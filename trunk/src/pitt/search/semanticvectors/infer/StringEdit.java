/**
   Copyright (c) 2013, the SemanticVectors AUTHORS.

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

package pitt.search.semanticvectors.infer;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Random;

import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.ObjectVector;
import pitt.search.semanticvectors.SearchResult;
import pitt.search.semanticvectors.VectorSearcher;
import pitt.search.semanticvectors.VectorStoreRAM;
import pitt.search.semanticvectors.VectorStoreWriter;
import pitt.search.semanticvectors.hashing.Bobcat;
import pitt.search.semanticvectors.vectors.ComplexVector;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorType;
import pitt.search.semanticvectors.vectors.ComplexVector.Mode;

public class StringEdit {
  
  public static void main(String[] args) throws Exception
  {
    
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
        
        theVSR.initFromFile(flagConfig.queryvectorfile());
        
  
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
  
  Hashtable<Integer, VectorStoreRAM> allNumberVectors = new Hashtable<Integer, VectorStoreRAM>();
  
  int cnt = 0;
  VectorStoreRAM theLetters = new VectorStoreRAM(flagConfig);
  NumberRepresentation NR = new NumberRepresentation( flagConfig);
  
  while (theNum.hasMoreElements())
  {
    if (cnt++ % 1000 == 0) System.err.print(".."+cnt);
    
    ObjectVector theNext = theNum.nextElement();
    String theTerm = theNext.getObject().toString().trim();
    VectorStoreRAM OV = null;
    
    OV = NR.getNumberVectors(1, theTerm.length()+1);
      
    twoVSR.putVector(theTerm,getStringVector(theTerm, OV, theLetters, flagConfig));
    
    Enumeration<ObjectVector> theNumbers = OV.getAllVectors();
    while (theNumbers.hasMoreElements())
    {
      ObjectVector nextObjectVector = theNumbers.nextElement();
      if (OOV.getVector(nextObjectVector.getObject()) == null)
      {
        OOV.putVector(theTerm.length()+":"+nextObjectVector.getObject(),nextObjectVector.getVector());
      }
    }
    
  }
  
   System.out.println(flagConfig.dimension());
   System.out.println(flagConfig.vectortype());
      
  
  
  VectorStoreWriter.writeVectors("editvectors.bin", flagConfig,twoVSR);
  VectorStoreWriter.writeVectorsInLuceneFormat("numbervectors.bin", flagConfig, OOV);
  VectorStoreWriter.writeVectorsInLuceneFormat("lettervectors.bin", flagConfig, theLetters);
  
  String[] terms = { "diabets", "dibetes",  "diabetic", "dominic", "abram", "sarai", "josh" };
     
  for (int a = 0; a < terms.length; a++)
  {
    VectorStoreRAM OV = NR.getNumberVectors(1, terms[a].length()+1);
  
    VectorSearcher.VectorSearcherCosine theVSC = new VectorSearcher.VectorSearcherCosine(twoVSR, twoVSR, null, flagConfig, getStringVector(terms[a], OV, theLetters, flagConfig));
    
    System.out.println(terms[a]);
    LinkedList<SearchResult> theResults = theVSC.getNearestNeighbors(10);
      
    for (int x =0; x < theResults.size(); x++)
    {
      System.out.println(theResults.get(x).getScore()+"\t"+theResults.get(x).getObjectVector().getObject());
    }
    
  }
  
    
  }
  
  public static Vector getStringVector(String theTerm, VectorStoreRAM theNumbers, VectorStoreRAM theLetters, FlagConfig flagConfig)
  {
    Vector theVector = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());
    Random random = new Random();
      if (flagConfig.vectortype().equals("complex"))
        ComplexVector.setDominantMode(Mode.CARTESIAN);
  
    //  System.out.println(theTerm);
    for (int q = 0; q < theTerm.length(); q++)
    {
      String letter = ""+theTerm.charAt(q);
      random.setSeed(Bobcat.asLong(letter));
      Vector incoming = VectorFactory.generateRandomVector(flagConfig.vectortype(), flagConfig.dimension(), flagConfig.seedlength, random);
      
      if (theLetters.getVector(letter) == null)
      {
        //System.out.println("adding "+letter);
        theLetters.putVector(letter, incoming.copy());
      }
      
      
      Vector posVector = theNumbers.getVector(q+1);
      if (posVector == null)
      { 
      System.out.println(theTerm);
      System.out.println(posVector);
      System.out.println(theTerm.length()+":"+(q+1)+"\n");
      Enumeration<ObjectVector> nation = theNumbers.getAllVectors();
      while (nation.hasMoreElements())
        System.out.println(nation.nextElement().getObject());
      }
      try {
      incoming.bind(posVector);
      } catch (Exception e) 
      {
        System.out.println(incoming);
        System.out.println(posVector);
        e.printStackTrace();
        System.exit(0);
      }
      //System.out.println(letter+" "+(q+1));
      theVector.superpose(incoming, 1, null);     
    }
    theVector.normalize();
    
    
    return theVector;
  }
  
  
}
