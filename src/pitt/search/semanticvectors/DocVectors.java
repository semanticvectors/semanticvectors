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
 * Implementation of vector store that collects doc vectors by
 * iterating through all the terms in a term vector store and
 * incrementing document vectors for each of the documents containing
 * that term. <br>
 *
 * TODO(widdows): This is a memory expensive implementation: it
 * creates both a document matrix (for quick population) and then a
 * document hashtable (to send to the VectorStoreWriter). This is a
 * poor design and should be refactored. <br>
 *
 * @param termVectorData Has all the information needed to create doc vectors.
 */
public class DocVectors implements VectorStore {

	private Hashtable<String, ObjectVector> docVectors;
	private TermVectorsFromLucene termVectorData;
	private IndexReader indexReader;

	/**
	 * Constructor that gets everything it needs from a
	 * TermVectorsFromLucene object.
	 */
	public DocVectors (TermVectorsFromLucene termVectorData) throws IOException {
		this.termVectorData = termVectorData;
		this.indexReader = termVectorData.getIndexReader();
		this.docVectors = new Hashtable();
	
		System.err.println("Initializing document matrix ...");
		float[][] docMatrix = new float[indexReader.numDocs()][ObjectVector.vecLength];
		for (int i=0; i < indexReader.numDocs(); ++i) {
			for (int j = 0; j < ObjectVector.vecLength; ++j) {
				docMatrix[i][j] = 0;
			}
		}

		System.out.println("Building document vectors ...");
		Enumeration<ObjectVector> termEnum = termVectorData.getAllVectors();

		try {
			int dc = 0;
			while (termEnum.hasMoreElements()) {
				/* output progress counter */
				if ((dc % 10000 == 0) || (dc < 10000 && dc % 1000 == 0)) {
					System.err.print(dc + " ... ");
				}
				dc++;

				ObjectVector termVectorObject = termEnum.nextElement();
				float[] termVector = termVectorObject.getVector();
				String word = (String)termVectorObject.getObject();
				int docNum;

				// go through checking terms for each fieldName
				for (String fieldName: termVectorData.getFieldsToIndex()) {
					Term term = new Term(fieldName, word);
					// get any docs for this term
					TermDocs td = indexReader.termDocs(term);
					while (td.next()) {
						docNum = td.doc();
						// add vector from this term, taking freq into account
						for (int j = 0; j < ObjectVector.vecLength; ++j) {
							docMatrix[docNum][j] += td.freq() * termVector[j];
						}
					}
				}
			}
		}
		catch (IOException e) { // catches from indexReader.
			e.printStackTrace();
		}

		System.err.println("\nCreated document matrix ... transforming to hashtable ...");

		int dc = 0;
		for (int i = 0; i < indexReader.numDocs(); ++i) {
			/* output progress counter */
			if ((dc % 10000 == 0) || (dc < 10000 && dc % 1000 == 0)) {
				System.err.print(dc + " ... ");
			}
			dc++;
			String docName;
			if (indexReader.document(i).getField("path") != null) {
				docName = indexReader.document(i).getField("path").stringValue();
			} else {
				// For bilingual docs, we index "filename" not "path",
				// since there are two system paths, one for each
				// language. So if there was no "path", get the "filename".
				docName = indexReader.document(i).getField("filename").stringValue();
			}
			float[] docVec = VectorUtils.getNormalizedVector(docMatrix[i]);
			docVectors.put(docName, new ObjectVector(docName, docVec));
		}
	}

	public float[] getVector(Object path){
		return docVectors.get(path).getVector();
	}

	public Enumeration getAllVectors(){
		return docVectors.elements();
	}
}    
