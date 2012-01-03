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

import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;

import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorType;

import java.io.IOException;
import java.util.Enumeration;
import java.util.logging.Logger;

/**
 * Implementation of vector store that collects doc vectors by
 * iterating through all the terms in a term vector store and
 * incrementing document vectors for each of the documents containing
 * that term.
 */
public class DocVectors implements VectorStore {
  private static final Logger logger = Logger.getLogger(DocVectors.class.getCanonicalName());
  private VectorType vectorType;
  private int dimension;  
  private VectorStoreRAM docVectors;
  private TermVectorsFromLucene termVectorData;
  private IndexReader indexReader;
  private LuceneUtils lUtils;

  @Override
  public VectorType getVectorType() { return vectorType; }
  
  @Override
  public int getDimension() { return dimension; }
  
  /**
   * Constructor that gets everything it needs from a
   * TermVectorsFromLucene object.
   */
  public DocVectors (TermVectorsFromLucene termVectorData) throws IOException {
    this.termVectorData = termVectorData;
    this.vectorType = termVectorData.getVectorType();
    this.dimension = termVectorData.getDimension();
    this.indexReader = termVectorData.getIndexReader();
    this.docVectors = new VectorStoreRAM(vectorType, dimension);
    
    if (this.lUtils == null) {
      String indexReaderDir = termVectorData.getIndexReader().directory().toString();
      indexReaderDir = indexReaderDir.replaceAll("^[^@]+@","");
      indexReaderDir = indexReaderDir.replaceAll(" lockFactory=.+$","");
      this.lUtils = new LuceneUtils(indexReaderDir);
    }

    initializeDocVectors();
    trainDocVectors();
  }

  /**
   * Creates doc vectors, iterating over terms.
   */
  private void trainDocVectors() {
    VerbatimLogger.info("Building document vectors ... ");
    Enumeration<ObjectVector> termEnum = termVectorData.getAllVectors();
    try {
      int tc = 0;
      while (termEnum.hasMoreElements()) {
        // Output progress counter.
        if ((tc % 10000 == 0) || (tc < 10000 && tc % 1000 == 0)) {
          VerbatimLogger.info("Processed " + tc + " terms ... ");
        }
        tc++;

        ObjectVector termVectorObject = termEnum.nextElement();
        Vector termVector = termVectorObject.getVector();
        String word = (String) termVectorObject.getObject();


        // Go through checking terms for each fieldName.
        for (String fieldName: termVectorData.getFieldsToIndex()) {
          Term term = new Term(fieldName, word);
          float globalweight = 1;
          float fieldweight = 1;
          
          
       
          
          if (Flags.termweight.equals("logentropy")) { 
            //global entropy weighting
            globalweight = globalweight * lUtils.getEntropy(term);
          }
          else if (Flags.termweight.equals("idf")) {
        	  
        	  int docFreq = indexReader.docFreq(term);
        	  if (docFreq > 0)
              globalweight =  globalweight * (float) Math.log10(indexReader.numDocs()/docFreq);
        	  }	
        
          // Get any docs for this term.
          TermDocs td = this.indexReader.termDocs(term);
          
          while (td.next()) {
            String docID = Integer.toString(td.doc());
            // Add vector from this term, taking freq into account.
            Vector docVector = this.docVectors.getVector(docID);
            float localweight = td.freq();

            if (Flags.fieldweight) {
            	//field weight: 1/sqrt(number of terms in field)
            	  String[] terms = indexReader.getTermFreqVector(td.doc(), fieldName).getTerms();
                  fieldweight = (float) (1/Math.sqrt(terms.length));
              }
            
            if (Flags.termweight.equals("logentropy"))
            {
              //local weighting: 1+ log (local frequency)
              localweight = new Double(1 + Math.log(localweight)).floatValue();    	
            }
          

            docVector.superpose(termVector, localweight * globalweight * fieldweight, null);
          }
        }
      }
    }
    catch (IOException e) { // catches from indexReader.
      e.printStackTrace();
    }

    VerbatimLogger.info("\nNormalizing doc vectors ...\n");
    for (int i = 0; i < indexReader.numDocs(); ++i) {
      docVectors.getVector(Integer.toString(i)).normalize();
    }
  }
  
  /**
   * Allocate doc vectors to zero vectors.
   */
  private void initializeDocVectors() {
    VerbatimLogger.info("Initializing document vector store ... \n");
    for (int i = 0; i < indexReader.numDocs(); ++i) {
      Vector docVector = VectorFactory.createZeroVector(vectorType, dimension);
      this.docVectors.putVector(Integer.toString(i), docVector);
    }
  }

  /**
   * Create a version of the vector store indexes by path / filename rather than Lucene ID.
   */
  public VectorStore makeWriteableVectorStore() {
    VectorStoreRAM outputVectors = new VectorStoreRAM(vectorType, dimension);

    for (int i = 0; i < this.indexReader.numDocs(); ++i) {
      String docName = "";
      try {
        // Default field value for docid is "path". But can be
        // reconfigured.  For bilingual docs, we index "filename" not
        // "path", since there are two system paths, one for each
        // language.
        if (this.indexReader.document(i).getField(Flags.docidfield) != null) {
          docName = this.indexReader.document(i).getField(Flags.docidfield).stringValue();
          if (docName.length() == 0) {
            logger.warning("Empty document name!!! This will cause problems ...");
            logger.warning("Please set -docidfield to a nonempty field in your Lucene index.");
          }
        }
        Vector docVector = this.docVectors.getVector(Integer.toString(i));
        outputVectors.putVector(docName, docVector);
      } catch (CorruptIndexException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    return outputVectors;
  }

  public Vector getVector(Object id) {
    return this.docVectors.getVector(id);
  }

  public Enumeration<ObjectVector> getAllVectors() {
    return this.docVectors.getAllVectors();
  }

  public int getNumVectors() {
    return this.docVectors.getNumVectors();
  }
}
