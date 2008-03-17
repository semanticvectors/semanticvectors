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
abstract public class VectorSearcher{
    private VectorStore vecStore;
    abstract float getScore(float[] queryVector, float[] testVector);

    public VectorSearcher(VectorStore vecStore) {
	this.vecStore = vecStore;
    }

    /**
     * This nearest neighbor search is implemented in the main abstract VectorSearcher
     * class: this enables all subclasses to reuse the search whatever scoring method
     * they implement.
     * @param queryVector the query vector: method will search for vectors closest to this
     * @param numResults the number of results / length of the result list
     */
    public LinkedList getNearestNeighbors(float[] queryVector, int numResults){
	LinkedList<SearchResult> results = new LinkedList();
	float score, threshold = -1;

	Enumeration<ObjectVector> vecEnum = vecStore.getAllVectors();

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
	public VectorSearcherCosine(VectorStore vecStore) {
	    super(vecStore);
	}
	public float getScore(float[] queryVector, float[] testVector) {
	    return VectorUtils.scalarProduct(queryVector, testVector);
	}
    }

    /**
     * Class for searching a vector store using tensor product similarity.
     * The class takes a seed relation (r1, r2), and a fixed test term s1.
     * The similarity for a search term s2 is then ten(r1, r2) * ten(s1, s2).
     */
    static public class VectorSearcherTensorSim extends VectorSearcher {
	private float[][] relTensor;
	/**
	 * @param rel1 Part of training relation.
	 * @param rel2 Part of training relation.
	 * Creates a relation tensor from rel1 and rel2, which query / test vectors
	 * will be compared with.
	 */
	public VectorSearcherTensorSim(VectorStore vecStore,
				    float[] rel1, float[] rel2) {
	    super(vecStore);
	    relTensor = VectorUtils.getOuterProduct(rel1, rel2);
	}

	/**
	 * @param queryVector Target vector for searching.
	 * @param testVector Vector being tested.
	 * Scores are hopefully high when the relationship between queryVector
	 * and testVector is analogoues to the relationship between rel1 and rel2.
	 */
	public float getScore(float[] queryVector, float[] testVector) {
	    float[][] testTensor = VectorUtils.getOuterProduct(queryVector, testVector);
	    return VectorUtils.getInnerProduct(relTensor, testTensor);
	}
    }

    /**
     * Like VectorSearcherTensorSim, but uses convolution product.
     */
    static public class VectorSearcherConvolutionSim extends VectorSearcher {
	private float[] relConvolution;
	/**
	 * @param rel1 Part of training relation.
	 * @param rel2 Part of training relation.
	 * Creates a relation convolution from rel1 and rel2, which query / test vectors
	 * will be compared with.
	 */
	public VectorSearcherConvolutionSim(VectorStore vecStore,
					    float[] rel1, float[] rel2) {
	    super(vecStore);
	    relConvolution = VectorUtils.getConvolution(rel1, rel2);
	}
	/**
	 * @param queryVector Target vector for searching.
	 * @param testVector Vector being tested.
	 * Scores are hopefully high when the relationship between queryVector
	 * and testVector is analogoues to the relationship between rel1 and rel2.
	 */
	public float getScore(float[] queryVector, float[] testVector) {
	    float[] testConvolution = VectorUtils.getConvolution(queryVector, testVector);
	    return VectorUtils.scalarProduct(relConvolution, testConvolution);
	}
    }
}
