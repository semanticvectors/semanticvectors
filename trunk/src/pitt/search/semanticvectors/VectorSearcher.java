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

import java.io.IOException;
import java.util.LinkedList;
import java.util.Enumeration;

/**
 * Class for searching vector stores using different scoring functions.
 */
abstract class VectorSearcher{
    private VectorStoreReader vecReader;
    abstract float getScore(float[] queryVector, float[] testVector);

    public VectorSearcher(VectorStoreReader vecReader) {
	this.vecReader = vecReader;
    }

    /**
     * @param queryVector the query vector: method will search for vectors closest to this
     * @param numResults the number of results / length of the result list
     */
    public LinkedList getNearestNeighbors(float[] queryVector, int numResults){
	LinkedList<SearchResult> results = new LinkedList();
	float score, threshold = -1;

	Enumeration<ObjectVector> vecEnum = vecReader.getAllVectors(); 

	while (vecEnum.hasMoreElements()) {
	    // Initialize result list if just starting.
	    if (results.size() == 0) {
		ObjectVector firstElement = vecEnum.nextElement();
		score = getScore(queryVector, firstElement.getVector());
		results.add(new SearchResult(score, firstElement));
		continue;
	    }

	    // Test this element.
	    ObjectVector testElement = vecEnum.nextElement();
	    score = getScore(queryVector, testElement.getVector());
	    if (score > threshold) {
		boolean added = false;
		for (int i = 0; i < results.size(); i++) {
		    // Add to list if this is right place.
		    if (score > results.get(i).getScore() && added==false) {
			results.add(i, new SearchResult(score, testElement));
			added = true;
		    }
		}
		// Prune list if there are already numResults.
		if (results.size() > numResults) {
		    results.removeLast();
		    threshold = results.getLast().getScore();
		}
	    }
	}		
	return results;
    }

    /**
     * Class for searching a vector store using cosine similarity.
     */
    static public class VectorSearcherCosine extends VectorSearcher {
	public VectorSearcherCosine(VectorStoreReader vecReader) {
	    super(vecReader);
	}
	public float getScore(float[] queryVector, float[] testVector) {
	    return VectorUtils.scalarProduct(queryVector, testVector);
	}
    }
}
