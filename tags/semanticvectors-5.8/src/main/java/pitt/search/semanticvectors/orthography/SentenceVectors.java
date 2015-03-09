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

package pitt.search.semanticvectors.orthography;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Random;
import java.util.StringTokenizer;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocsAndPositionsEnum;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.LuceneUtils;
import pitt.search.semanticvectors.ObjectVector;
import pitt.search.semanticvectors.VectorStore;
import pitt.search.semanticvectors.VectorStoreDeterministic;
import pitt.search.semanticvectors.VectorStoreRAM;
import pitt.search.semanticvectors.VectorStoreWriter;
import pitt.search.semanticvectors.utils.Bobcat;
import pitt.search.semanticvectors.utils.VerbatimLogger;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;

public class SentenceVectors {

  static LuceneUtils lUtils;

  public static Vector getPhraseVector(String theSentence, VectorStoreRAM theNumbers, VectorStore theWords, VectorStoreRAM semanticWords, Random random, FlagConfig flagConfig)
  {
    Vector theVector = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());
    
    StringTokenizer theTokenizer = new StringTokenizer(theSentence, " ");
    int allTokens = theTokenizer.countTokens();
    
    random.setSeed(Bobcat.asLong(theSentence));
    Vector elementalDocVector = VectorFactory.generateRandomVector(flagConfig.vectortype(), flagConfig.dimension(), flagConfig.seedlength, random);
    	    

    for (int q = 0; q < allTokens; q++)
    {
    	
      Vector docVector = elementalDocVector.copy();
      
      String word = theTokenizer.nextToken();
      //System.out.println(word);
      float theweight = 1;
      if (lUtils != null)
        theweight = lUtils.getGlobalTermWeight(new Term(flagConfig.contentsfields()[0],word));

      if (! theWords.containsVector(word))
    	  continue;
      
      Vector incoming = theWords.getVector(word).copy();
      
      if (! semanticWords.containsVector(word))
      {
    	  semanticWords.putVector(word, VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension()));
      }

    	  
      Vector outgoing = semanticWords.getVector(word);	  
      Vector posVector = theNumbers.getVector((q+1));

      
      if (posVector == null)
      {   
      System.out.println(allTokens+":"+(q+1));
      System.out.println(posVector);
      System.exit(0);
      }


      try {
        incoming.bind(posVector);
        docVector.bind(posVector);
      } catch (Exception e) 
      {
        System.out.println(incoming);
        System.out.println(posVector);
        e.printStackTrace();
        System.exit(0);
      }
      //System.out.println(letter+" "+(q+1));

      theVector.superpose(incoming, theweight, null); 
      outgoing.superpose(docVector, 1, null);
    
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

    IndexReader indexReader = DirectoryReader.open(FSDirectory.open(
        FileSystems.getDefault().getPath(flagConfig.luceneindexpath())));

    int numdocs = indexReader.numDocs();
    VectorStoreRAM sentenceVectors = new VectorStoreRAM(flagConfig);
    VectorStoreRAM theNumbers = new VectorStoreRAM(flagConfig);
    VectorStore	   theWords = new VectorStoreRAM(flagConfig);
    VectorStoreRAM semanticWords = new VectorStoreRAM(flagConfig);
    
    if (!flagConfig.initialtermvectors().equals("random")) ((VectorStoreRAM) theWords).initFromFile(flagConfig.initialtermvectors());
    else theWords = new VectorStoreDeterministic(flagConfig);
    
    VectorStoreRAM OOV = new VectorStoreRAM(flagConfig);


    Hashtable<Integer, VectorStoreRAM> allNumbers = new Hashtable<Integer, VectorStoreRAM>();
    NumberRepresentation NR = new NumberRepresentation(flagConfig, "*STARTSENTENCE*", "*ENDSENTENCE*");
    theNumbers = NR.getNumberVectors(0, 11); 
    allNumbers.put(new Integer(10), theNumbers);
    
    System.err.println("Numdocs "+numdocs);
    for (int x =0; x < numdocs; x++)
    {

      if (x % 10000 == 0)
        System.err.print(x+"...");

      Terms terms = lUtils.getTermVector(x, flagConfig.contentsfields()[0]);
          
      ArrayList<String> localTerms = new ArrayList<String>();
      ArrayList<Integer> freqs = new ArrayList<Integer>();
      Hashtable<Integer, Integer> localTermPositions = new Hashtable<Integer, Integer>();

      TermsEnum termsEnum=null;
  	try {
  		termsEnum = terms.iterator(null);
  	} catch (IOException e1) {
  		// TODO Auto-generated catch block
  		e1.printStackTrace();
  	}
      BytesRef text;
      int termcount = 0;

      try {
  		while((text = termsEnum.next()) != null) {
  		  String theTerm = text.utf8ToString();
  		  
  		  DocsAndPositionsEnum docsAndPositions = termsEnum.docsAndPositions(null, null);
  		  if (docsAndPositions == null) continue;
  		  
  		  docsAndPositions.nextDoc();
  		  freqs.add(docsAndPositions.freq());
  		  localTerms.add(theTerm); 

  		  for (int y = 0; y < docsAndPositions.freq(); y++) {
  		    localTermPositions.put(new Integer(docsAndPositions.nextPosition()), termcount);
  		  }

  		  termcount++;
  		}
  	} catch (IOException e1) {
  		// TODO Auto-generated catch block
  		e1.printStackTrace();
  	}
      
      int allTokens = localTermPositions.size();
      
      String theSentence = "";
      
      for (int q=0; q < allTokens; q++)
    	  if (localTermPositions.get(q) != null && localTerms.get(localTermPositions.get(q)) != null)
    	  theSentence += localTerms.get(localTermPositions.get(q))+" ";
    	 
      
      //String theSentence = theDoc.get(flagConfig.contentsfields()[0]).replaceAll("[^A-Za-z]"," ").toLowerCase();

      StringTokenizer theTokenizer = new StringTokenizer(theSentence," ");
      int numTokens = theTokenizer.countTokens();

      if (numTokens < 2)
        continue;

      theNumbers = allNumbers.get(new Integer(numTokens));

      
      if (theNumbers == null)
      {

        //System.out.println("Generating number vectors for sentence of length "+numTokens);

        theNumbers = NR.getNumberVectors(0, numTokens+1); 
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

      Random random = new Random();
      
      Vector sentenceVector = getPhraseVector(theSentence, theNumbers, theWords, semanticWords, random, flagConfig);
      sentenceVectors.putVector(theSentence, sentenceVector);



    }
    
    

    VectorStoreWriter.writeVectorsInLuceneFormat("sentencevectors.bin", flagConfig, sentenceVectors);
    VectorStoreWriter.writeVectorsInLuceneFormat("sentencenumbervectors.bin", flagConfig, OOV);
    VectorStoreWriter.writeVectorsInLuceneFormat("sentencetermvectors.bin", flagConfig, theWords);
    
    //experimental - proximity based word vectors
    VerbatimLogger.info("\nNormalizing semantic term vectors ...\n");
    Enumeration<ObjectVector> docEnum = semanticWords.getAllVectors();
    while (docEnum.hasMoreElements())
    	docEnum.nextElement().getVector().normalize();
    
    
    VectorStoreWriter.writeVectorsInLuceneFormat("positionalritermvectors.bin", flagConfig, semanticWords);
  }


}

