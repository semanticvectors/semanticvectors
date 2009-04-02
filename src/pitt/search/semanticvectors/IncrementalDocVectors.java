/**
	 Copyright (c) 2008, Arizona State University.

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

import java.io.IOException;
import java.lang.Integer;
import org.apache.lucene.index.*;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.MMapDirectory;

/**
 * generates document vectors incrementally
 * requires a
 * @param termVectorData Has all the information needed to create doc vectors.
 * @param vectorFile Filename for the document vectors
 * @author Trevor Cohen, Dominic Widdows
 */
public class IncrementalDocVectors {

	private VectorStore termVectorData;
	private IndexReader indexReader;
	private String[] fieldsToIndex;
	
	/**
	 * Constructor that gets everything it needs from a
	 * TermVectorsFromLucene object and writes to a named file.
	 * @param termVectorData Has all the information needed to create doc vectors.
	 * @param indexDir Directory of the Lucene Index used to generate termVectorData
	 * @param fieldsToIndex String[] containing fields indexed when generating termVectorData
	 * @param vectorFile Filename for the document vectors
	 */
	public IncrementalDocVectors(VectorStore termVectorData, String indexDir,
															 String[] fieldsToIndex, String vectorFile)
		throws IOException {
		this.termVectorData = termVectorData;
		this.indexReader = IndexReader.open(indexDir);
		this.fieldsToIndex = fieldsToIndex;
	
		int numdocs = indexReader.numDocs();

		// Open file and write headers.
		MMapDirectory dir = new MMapDirectory();
		IndexOutput outputStream = dir.createOutput(vectorFile);
		float[] tmpVector = new float[ObjectVector.vecLength];

		int counter = 0;
		System.err.println("Write vectors incrementally to file " + vectorFile);

		// Write header giving number of dimensions for all vectors.
		outputStream.writeString("-dimensions");
		outputStream.writeInt(ObjectVector.vecLength);


		// Iterate through documents.
		for (int dc=0; dc < numdocs; dc++) {
			/* output progress counter */
			if (( dc % 10000 == 0 ) || ( dc < 10000 && dc % 1000 == 0 )) {
				System.err.print(dc + " ... ");
			}


			String docID = Integer.toString(dc);

			// Use filename and path rather than Lucene index number for document vector.
			if (this.indexReader.document(dc).getField("path") != null) {
				docID = this.indexReader.document(dc).getField("path").stringValue();
			} else {
				// For bilingual docs, we index "filename" not "path",
				// since there are two system paths, one for each
				// language. So if there was no "path", get the "filename".
				docID = this.indexReader.document(dc).getField("filename").stringValue();
			}

			float[] docVector = new float[ObjectVector.vecLength];

			for (String fieldName: fieldsToIndex) {
				TermFreqVector vex =
					 indexReader.getTermFreqVector(dc, fieldName);

				if (vex !=null) {
					// Get terms in document and term frequencies.
					String[] terms = vex.getTerms();
					int[] freqs = vex.getTermFrequencies();
					
					for (int b = 0; b < freqs.length; ++b) {
						String term = terms[b];
						int freq = freqs[b];
						// Add contribution from this term, excluding terms that
						// are not represented in termVectorData.
						try{
							float[] termVector = termVectorData.getVector(term);
							if (termVector != null && termVector.length > 0) {
								for (int j = 0; j < ObjectVector.vecLength; ++j) {
									docVector[j] += freq * termVector[j];
								}
							}
						} catch (NullPointerException npe) {
							// Don't normally print anything - too much data!
							// TODO(dwiddows): Replace with a configurable logging system.
							// System.err.println("term "+term+ " not represented");
						}
					}
				}
				// All fields in document have been processed.
				// Write out documentID and normalized vector.
				outputStream.writeString(docID);
				docVector = VectorUtils.getNormalizedVector(docVector);

				for (int i = 0; i < ObjectVector.vecLength; ++i) {
					outputStream.writeInt(Float.floatToIntBits(docVector[i]));
				}
			}
		} // Finish iterating through documents.

		System.err.println("Finished writing vectors.");
		outputStream.flush();
		outputStream.close();
	}
	
	
	
	public static void main(String[] args) throws Exception
	{//vector store (terms)
     //index
	 String[] fieldsToIndex = {"contents"};
	 String vectorFile = args[0].replaceAll("\\.bin","")+"_docvectors.bin";	
	 VectorStoreRAM vsr = new VectorStoreRAM();
	 vsr.InitFromFile(args[0]);
	 
		new IncrementalDocVectors(vsr, args[1],
				 fieldsToIndex, vectorFile);
	}
	
}
