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

import java.io.File;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Random;
import java.util.StringTokenizer;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.FSDirectory;

import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.LuceneUtils;
import pitt.search.semanticvectors.ObjectVector;
import pitt.search.semanticvectors.VectorStoreRAM;
import pitt.search.semanticvectors.VectorStoreWriter;
import pitt.search.semanticvectors.hashing.Bobcat;
import pitt.search.semanticvectors.vectors.ComplexVector;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.ComplexVector.Mode;

public class SentenceVectors {

  static LuceneUtils lUtils;
  
  public static Vector getPhraseVector(String theSentence, VectorStoreRAM theNumbers, VectorStoreRAM termVectors, FlagConfig flagConfig)
  {
    Vector theVector = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());
    Random random = new Random();
      if (flagConfig.vectortype().equals("complex"))
        ComplexVector.setDominantMode(Mode.CARTESIAN);
  
      StringTokenizer theTokenizer = new StringTokenizer(theSentence, " ");
      int allTokens = theTokenizer.countTokens();
    //  System.out.println(theTerm);
    for (int q = 0; q < allTokens; q++)
    {
      String word = theTokenizer.nextToken();
      float theweight = 1;
      if (lUtils != null)
        theweight = lUtils.getGlobalTermWeight(new Term("contents",word));
      
      Vector incoming = termVectors.getVector(word);
      
      if (incoming == null)
      {
        random.setSeed(Bobcat.asLong(word));
        incoming = VectorFactory.generateRandomVector(flagConfig.vectortype(), flagConfig.dimension(), flagConfig.seedlength, random);
        //System.out.println("adding "+word);
        termVectors.putVector(word, incoming.copy());
      }
      
      
      Vector posVector = theNumbers.getVector((allTokens+1)+":"+(q+1));
      
      if (posVector == null)
      {   System.out.println(allTokens+":"+(q+1));
        System.out.println(posVector);
        System.exit(0);
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
      
      theVector.superpose(incoming, theweight, null);     
    }
    theVector.normalize();
    
    
    return theVector;
  }
  
  
  public static void main(String[] args) throws Exception
  { 
    
     FlagConfig flagConfig = null;
        try {
          flagConfig = FlagConfig.getFlagConfig(args);
          args = flagConfig.remainingArgs;
        } catch (IllegalArgumentException e) {
          throw e;
        }

    lUtils = null;
    if (!flagConfig.luceneindexpath().isEmpty())
      lUtils = new LuceneUtils(flagConfig);
    
    IndexReader indexReader = IndexReader.open(FSDirectory.open(new File("/home/tcohen/OHSUMED_SOURCES/predication_index")));

    int numdocs = indexReader.numDocs();
    VectorStoreRAM sentenceVectors = new VectorStoreRAM(flagConfig);
    VectorStoreRAM theNumbers = new VectorStoreRAM(flagConfig);
    VectorStoreRAM theWords = new VectorStoreRAM(flagConfig);
    //theWords.initFromFile("/home/tcohen/SVNspace/SemanticVectors/termvectors.bin");
    
    VectorStoreRAM OOV = new VectorStoreRAM(flagConfig);
    
    
    Hashtable<Integer, VectorStoreRAM> allNumbers = new Hashtable<Integer, VectorStoreRAM>();
    NumberRepresentation NR = new NumberRepresentation(flagConfig);
    
    System.err.println("Numdocs "+numdocs);
    for (int x =0; x < numdocs; x++)
    {
      
      if (x % 10000 == 0)
        System.err.print(x+"...");
      
      Document theDoc = indexReader.document(x);
      String theSentence = theDoc.get("source").replaceAll("[^A-Za-z]"," ").toLowerCase();
      
      StringTokenizer theTokenizer = new StringTokenizer(theSentence," ");
      int numTokens = theTokenizer.countTokens();
      
      if (numTokens < 2)
        continue;
      
      theNumbers = allNumbers.get(new Integer(numTokens));
      
      
      if (theNumbers == null)
      {
    
      //System.out.println("Generating number vectors for sentence of length "+numTokens);
        
      theNumbers = NR.getNumberVectors(1, numTokens+1); 
      allNumbers.put(new Integer(numTokens), theNumbers);
      
      Enumeration<ObjectVector> newNumbers = theNumbers.getAllVectors();
      
      while (newNumbers.hasMoreElements())
      {
        ObjectVector nextObjectVector = newNumbers.nextElement();
        if (OOV.getVector(numTokens+":"+nextObjectVector.getObject()) == null)
        {
          OOV.putVector(numTokens+":"+nextObjectVector.getObject(),nextObjectVector.getVector());
        }
      }
      
      
      }
      
      Vector sentenceVector = getPhraseVector(theSentence, theNumbers, theWords, flagConfig);
      sentenceVectors.putVector(theSentence, sentenceVector);
      
      //System.out.println(theSentence);
      
    
      
    }
    
    VectorStoreWriter.writeVectorsInLuceneFormat("sentencevectors.bin", flagConfig, sentenceVectors);
    VectorStoreWriter.writeVectorsInLuceneFormat("sentencenumbervectors.bin", flagConfig, OOV);
    VectorStoreWriter.writeVectorsInLuceneFormat("sentencetermvectors.bin", flagConfig, theWords);
  }
  
  
}

