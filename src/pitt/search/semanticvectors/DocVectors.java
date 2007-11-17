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
import java.util.Hashtable;
import java.util.Random;
import java.util.Enumeration;
import java.io.IOException;
import java.lang.NullPointerException;

/** 
 * Implementation of vector store that collects doc vectors 
 * by iterating through all the terms in a term vector store 
 * and .
 *  
 * @param termVectorData Has all the information needed to create doc vectors
 */
public class DocVectors implements VectorStore {

    private Hashtable<String, ObjectVector> docVectors;
    private TermVectorsFromLucene termVectorData;
    private IndexReader indexReader;

    public DocVectors ( TermVectorsFromLucene termVectorData ) throws IOException {
	this.termVectorData = termVectorData;
	this.indexReader = termVectorData.getIndexReader();
	this.docVectors = new Hashtable();
	
	int numDocs = indexReader.numDocs();
	String[] fieldNames = {"contents"}; // you may want to change this
	int i, j;

	System.err.println("Initializing document matrix ...");
	float[][] docMatrix = new float[numDocs][ObjectVector.vecLength];
	for(i=0; i<numDocs; i++){
	    for(j=0; j<ObjectVector.vecLength; j++){
		docMatrix[i][j] = 0;
	    }
	}

	System.out.println("Building document vectors ...");
	Enumeration<ObjectVector> termEnum = termVectorData.getAllVectors();

	int tc = 0;

	try {
	    while(termEnum.hasMoreElements()){

		/* keep track of progress */
		tc++;
		if(tc % 100000 == 0){
		    System.out.print(tc + " ... ");
		}
		
		ObjectVector termVectorObject = termEnum.nextElement();
		float[] termVector = termVectorObject.getVector();
		String word = (String)termVectorObject.getObject();
		int docNum;

		// go through checking terms for each fieldName
		for(String fieldName: fieldNames){
		    Term term = new Term(fieldName, word);
		    // get any docs for this term
		    TermDocs td = indexReader.termDocs(term);
		    while(td.next()){
			docNum = td.doc();
			// add vector from this term, taking freq into account
			for(j=0; j<ObjectVector.vecLength; j++){
			    docMatrix[docNum][j] += td.freq() * termVector[j];
			}
		    }
		}
	    }
	}
	catch( IOException e ){ // catches from indexReader.
	    e.printStackTrace();
	}

	System.err.println("Created document matrix ... creating matrix store ...");

	for( i=0; i<numDocs; i++ ){
	    String docPath = indexReader.document(i).getField("path").stringValue();
	    float[] docVec = VectorUtils.getNormalizedVector(docMatrix[i]);
	    docVectors.put( docPath, new ObjectVector(docPath, docVec) );
	}
    }


    public float[] getVector(Object path){
	return docVectors.get(path).getVector();
    }

    public Enumeration getAllVectors(){
	return docVectors.elements();
    }
}    
