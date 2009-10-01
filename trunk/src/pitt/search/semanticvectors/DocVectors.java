/**
   Copyright (c) 2007, University of Pittsburgh

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

import org.apache.lucene.index.*;
import org.apache.lucene.document.Field;
import java.io.IOException;
import java.lang.Integer;
import java.lang.NullPointerException;
import java.util.Hashtable;
import java.util.Random;
import java.util.Enumeration;

/**
 * Implementation of vector store that collects doc vectors by
 * iterating through all the terms in a term vector store and
 * incrementing document vectors for each of the documents containing
 * that term. <br>
 *
 * @param termVectorData Has all the information needed to create doc vectors.
 *
 * TODO (dwiddows): Refactor so that interface is more flexible, like IncrementalDocVectors.
 */
public class DocVectors implements VectorStore {

  private VectorStoreRAM docVectors;
  private TermVectorsFromLucene termVectorData;
  private IndexReader indexReader;
  static private LuceneUtils lUtils;
  
  /**
   * Constructor that gets everything it needs from a
   * TermVectorsFromLucene object.
   */
  public DocVectors (TermVectorsFromLucene termVectorData) throws IOException {
    this.termVectorData = termVectorData;
    this.indexReader = termVectorData.getIndexReader();
    this.docVectors = new VectorStoreRAM();
    if (this.lUtils == null)
    this.lUtils = new LuceneUtils(termVectorData.getIndexReader().directory().toString().replaceAll(".*@",""));
    
    // Intialize doc vector store.
    System.err.println("Initializing document vector store ...");
    for (int i = 0; i < indexReader.numDocs(); ++i) {
      float[] docVector = new float[Flags.dimension];
      for (int j = 0; j < Flags.dimension; ++j) {
        docVector[j] = 0;
      }
      this.docVectors.putVector(Integer.toString(i), docVector);
    }

    // Create doc vectors, iterating over terms.
    System.out.println("Building document vectors ...");
    Enumeration<ObjectVector> termEnum = termVectorData.getAllVectors();

    try {
      int dc = 0;
      while (termEnum.hasMoreElements()) {
        // Output progress counter.
        if ((dc % 10000 == 0) || (dc < 10000 && dc % 1000 == 0)) {
          System.err.print(dc + " ... ");
        }
        dc++;

        ObjectVector termVectorObject = termEnum.nextElement();
        float[] termVector = termVectorObject.getVector();
        String word = (String) termVectorObject.getObject();

        
        // Go through checking terms for each fieldName.
        for (String fieldName: termVectorData.getFieldsToIndex()) {
          Term term = new Term(fieldName, word);
          float globalweight = 1;
          if (Flags.termweight.equals("logentropy")) { 
	      //global entropy weighting
	      globalweight = globalweight * lUtils.getEntropy(term);
	  }
          
          // Get any docs for this term.
          TermDocs td = this.indexReader.termDocs(term);
          while (td.next()) {
            String docID = Integer.toString(td.doc());
            // Add vector from this term, taking freq into account.
            float[] docVector = this.docVectors.getVector(docID);
            float localweight = td.freq();
            
            if (Flags.termweight.equals("logentropy"))
            {
            	//local weighting: 1+ log (local frequency)
            	localweight = new Double(1 + Math.log(localweight)).floatValue();    	
            }
            
            for (int j = 0; j < Flags.dimension; ++j) {
              docVector[j] += localweight * globalweight * termVector[j];
              
            }
          }
        }
      }
    }
    catch (IOException e) { // catches from indexReader.
      e.printStackTrace();
    }

    System.err.println("\nNormalizing doc vectors ...");
    int dc = 0;
    for (int i = 0; i < indexReader.numDocs(); ++i) {
      float[] docVector = this.docVectors.getVector(Integer.toString(i));
      docVector = VectorUtils.getNormalizedVector(docVector);
      this.docVectors.putVector(Integer.toString(i), docVector);
    }
  }

  /**
   * Create a version of the vector store indexes by path / filename rather than Lucene ID.
   */
  public VectorStore makeWriteableVectorStore() {
    VectorStoreRAM outputVectors = new VectorStoreRAM();

    for (int i = 0; i < this.indexReader.numDocs(); ++i) {
      String docName = "";
      try {
	// Default field value for docid is "path". But can be
	// reconfigured.  For bilingual docs, we index "filename" not
	// "path", since there are two system paths, one for each
	// language.
        if (this.indexReader.document(i).getField("path") != null) {
          docName = this.indexReader.document(i).getField(Flags.docidfield).stringValue();
	  if (docName.length() == 0) {
	    System.err.println("Empty document name!!! This will cause problems ...");
	    System.err.println("Please set -docidfield to a nonempty field in your Lucene index.");
	  }
        }
        float[] docVector = this.docVectors.getVector(Integer.toString(i));
        outputVectors.putVector(docName, docVector);
      } catch (CorruptIndexException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    return outputVectors;
  }

  public float[] getVector(Object id) {
    return this.docVectors.getVector(id);
  }

  public Enumeration getAllVectors() {
    return this.docVectors.getAllVectors();
  }

  public int getNumVectors() {
    return this.docVectors.getNumVectors();
  }
}
