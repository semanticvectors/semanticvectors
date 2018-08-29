/**
  Copyright 2008, Google Inc.
   All rights reserved.

   Redistribution and use in source and binary forms, with or without
   modification, are permitted provided that the following conditions are
   met:

   * Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.

   * Redistributions in binary form must reproduce the above
   copyright notice, this list of conditions and the following disclaimer
   in the documentation and/or other materials provided with the
   distribution.

   * Neither the name of Google Inc. nor the names of its
   contributors may be used to endorse or promote products derived from
   this software without specific prior written permission.

   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
   A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
   OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
   SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
   LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
   DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
   THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
   (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
   THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
**/

package pitt.search.semanticvectors;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.IllegalArgumentException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Random;
import java.util.Vector;

import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;

import pitt.search.semanticvectors.utils.Bobcat;
import pitt.search.semanticvectors.utils.VerbatimLogger;
import pitt.search.semanticvectors.vectors.RealVector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorType;
import pitt.search.semanticvectors.vectors.ZeroVectorException;

/**
 * Class providing command-line interface for transforming vector
 * store between the optimized Lucene format and plain text.
 */
public class VectorStoreTranslater {
  public static String usageMessage = "VectorStoreTranslater class in pitt.search.semanticvectors"
      + "\nUsage: java pitt.search.semanticvector.VectorStoreTranslater -option INFILE OUTFILE"
      + "\n -option can be: -lucenetotext or -texttolucene";

  private enum Options { LUCENE_TO_TEXT, TEXT_TO_LUCENE, LUCENE_TO_WORD2VEC}

  /**
   * Command line method for performing index translation.
   * @throws IOException if any of the vector stores on disk cannot be opened.
   * @see #usageMessage
   */
  public static void main(String[] args) throws IOException {
	  
	 final long ONE_GB = (long) Math.pow(1024,3);
	  
    // Parse command line args.
    if (args.length < 3) {
      VerbatimLogger.info("You gave " + args.length + " arguments aside from the command line flags, there must be at least 3.\n");
      System.err.println(usageMessage);
      throw new IllegalArgumentException();
    }
    Options option = null;
    if (args[0].equalsIgnoreCase("-lucenetotext")) { option = Options.LUCENE_TO_TEXT; }
    else if (args[0].equalsIgnoreCase("-texttolucene")) { option = Options.TEXT_TO_LUCENE; }
    else if (args[0].equalsIgnoreCase("-lucenetoword2vec")) { option = Options.LUCENE_TO_WORD2VEC; }
   
    else {
      System.err.println(usageMessage);
      throw new IllegalArgumentException();
    }

    String infile = args[1];
    String outfile = args[2];

    // Empty flag config is needed to satisfy vector store interfaces.
    FlagConfig flagConfig = FlagConfig.getFlagConfig(null);

    // Convert Lucene-style index to plain text.
    if (option == Options.LUCENE_TO_TEXT) {
      VectorStoreReaderLucene vecReader;
      try {
        vecReader = new VectorStoreReaderLucene(infile, flagConfig);
      } catch (IOException e) {
        throw e;
      }
      VerbatimLogger.info("Writing term vectors to " + outfile + "\n");
      VectorStoreWriter.writeVectorsInTextFormat(outfile, flagConfig, vecReader);
      vecReader.close();
    } 
    else if (option == Options.LUCENE_TO_WORD2VEC) 
    {
    	 VectorStoreReaderLucene vecReader;
         try {
           vecReader = new VectorStoreReaderLucene(infile, flagConfig);
         } catch (IOException e) {
           throw e;
         }
         VerbatimLogger.info("Writing term vectors to " + outfile + "\n");
         OutputStream theOutput = new FileOutputStream(new File(outfile));
    		
    	 //java code to output word2vec format, procedure derived from code in repository
    	 //https://github.com/medallia/Word2VecJava.git
    	 Charset cs = Charset.forName("UTF-8");
    	 String header = String.format("%d %d\n", vecReader.getNumVectors(), flagConfig.dimension());
    	 ByteBuffer buffer = ByteBuffer.allocate(4 * flagConfig.dimension());
    	 buffer.order(ByteOrder.LITTLE_ENDIAN);	

    	 theOutput.write(header.getBytes(cs));
    	 Enumeration<ObjectVector> allVecs = vecReader.getAllVectors();
    	
    		while (allVecs.hasMoreElements())
    		{
    			ObjectVector nextObjectVector = allVecs.nextElement();
    			String nextTerm = nextObjectVector.getObject().toString();
    			float[] nextVec = ((RealVector) nextObjectVector.getVector()).getCoordinates();
    			theOutput.write(String.format("%s ", nextTerm).getBytes(cs));
    			buffer.clear();
    			
    			for(int j = 0; j < flagConfig.dimension(); ++j)
    				buffer.putFloat(nextVec[j]);
    			
    			theOutput.write(buffer.array());
    			theOutput.write('\n');
    			
    		}
    		
    		theOutput.flush();
    		theOutput.close();
          
    }
     // Convert plain text index to Lucene-style.
    else if (option == Options.TEXT_TO_LUCENE) {
      VectorStoreReaderText vecReader;
      try {
        vecReader = new VectorStoreReaderText(infile, flagConfig);
      } catch (IOException e) {
        throw e;
      }
      VerbatimLogger.info("Writing term vectors to " + outfile + "\n");
      VectorStoreWriter.writeVectorsInLuceneFormat(outfile, flagConfig, vecReader);
      vecReader.close();
    }
  }
}
