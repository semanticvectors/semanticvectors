/**
	 Copyright (c) 2007, University of Pittsburgh

	 All rights reserved.

	 Redistribution and use in sourcegg and binary forms, with or without
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
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Enumeration;

/**
 * Class for searching vector stores using different scoring functions.
 `* Each VectorSearcher implements a particular scoring function which is 
 * normally query dependent, so each query needs its own VectorSearcher.
 */
abstract public class VectorSearcher{
	private VectorStore queryVecStore;
	private VectorStore searchVecStore;
	private LuceneUtils luceneUtils;

	/**
	 * This needs to be filled in for each subclass. It takes an individual 
	 * vector and assigns it a relevance score for this VectorSearcher.
	 */
	public abstract float getScore(float[] testVector);

	public VectorSearcher(VectorStore queryVecStore,
												VectorStore searchVecStore,
												LuceneUtils lucenUtils) {
		this.queryVecStore = queryVecStore;
		this.searchVecStore = searchVecStore;
		this.luceneUtils = luceneUtils;
	}

	/**
	 * This nearest neighbor search is implemented in the abstract
	 * VectorSearcher class itself: this enables all subclasses to reuse
	 * the search whatever scoring method they implement.  Since query
	 * expressions are built into the VectorSearcher,
	 * getNearestNeighbors no longer takes a query vector as an
	 * argument.
	 * @param numResults the number of results / length of the result list.
	 */
	public LinkedList getNearestNeighbors(int numResults){
		LinkedList<SearchResult> results = new LinkedList();
		float score, threshold = -1;

		Enumeration<ObjectVector> vecEnum = searchVecStore.getAllVectors();

		while (vecEnum.hasMoreElements()) {
	    // Initialize result list if just starting.
	    if (results.size() == 0) {
				ObjectVector firstElement = vecEnum.nextElement();
				score = getScore(firstElement.getVector());
				results.add(new SearchResult(score, firstElement));
				continue;
	    }

	    // Test this element.
	    ObjectVector testElement = vecEnum.nextElement();
	    score = getScore(testElement.getVector());

	    if (score > threshold) {
				boolean added = false;
				for (int i = 0; i < results.size(); ++i) {
					// Add to list if this is right place.
					if (score > results.get(i).getScore() && added == false) {
						results.add(i, new SearchResult(score, testElement));
						added = true;
					}
				}
				// Prune list if there are already numResults.
				if (results.size() > numResults) {
					results.removeLast();
					threshold = results.getLast().getScore();
				} else {
					if (added == false) {
						results.add(new SearchResult(score, testElement));
					}
				}
	    }
		}
		return results;
	}

	/**
	 * Class for searching a vector store using cosine similarity.
	 */
	static public class VectorSearcherCosine extends VectorSearcher {
		float[] queryVector;

		public VectorSearcherCosine(VectorStore queryVecStore,
																VectorStore searchVecStore,
																LuceneUtils luceneUtils,
																String[] queryTerms) {
	    super(queryVecStore, searchVecStore, luceneUtils);
			this.queryVector = CompoundVectorBuilder.getQueryVector(queryVecStore,
																															luceneUtils,
																															queryTerms);
			if (VectorUtils.isZeroVector(this.queryVector)) {
				System.err.println("Query vector is zero ... no results.");
				System.exit(-1);
			}
		}
		public float getScore(float[] testVector) {
	    return VectorUtils.scalarProduct(this.queryVector, testVector);
		}
	}

	/**
	 * Class for searching a vector store using tensor product
	 * similarity.  The class takes a seed tensor as a training
	 * example. This tensor should be entangled (a superposition of
	 * several individual products A * B) for non-trivial reslults.
	 */
	static public class VectorSearcherTensorSim extends VectorSearcher {
		private float[][] trainingTensor;
		private float[] partnerVector;
		/**
		 * @param searchVecStore The vector store to search.
		 * test vector to form a tensor product for comparison.
		 */
		public VectorSearcherTensorSim(VectorStore queryVecStore,
																	 VectorStore searchVecStore,
																	 LuceneUtils luceneUtils,
																	 String[] queryTerms) {
			super(queryVecStore, searchVecStore, luceneUtils);
			this.trainingTensor = VectorUtils.createZeroTensor(ObjectVector.vecLength);

			// Collect tensor training relations.
			int i = 0;
			while (queryTerms[i].indexOf("~") > 0) {
				System.err.println("Training pair: " + queryTerms[i]);
				String[] trainingTerms = queryTerms[i].split("~");
				if (trainingTerms.length != 2) {
					System.err.println("Tensor training terms must be pairs split by individual"
														 + " '~' character. Error with: '" + queryTerms[i] + "'");
				}
				float[] trainingVec1 = queryVecStore.getVector(trainingTerms[0]);
				float[] trainingVec2 = queryVecStore.getVector(trainingTerms[1]);
				if (trainingVec1 != null && trainingVec2 != null) {
					float[][] trainingPair =
						VectorUtils.getOuterProduct(trainingVec1, trainingVec2);
					this.trainingTensor =
						VectorUtils.getTensorSum(trainingTensor, trainingPair);
				}
				++i;
			}

			// Check to see that we got a non-zero training tensor before moving on.
			if (VectorUtils.isZeroTensor(trainingTensor)) {
				System.err.println("Tensor training relation is zero ... no results.");
				System.exit(-1);
			}
			this.trainingTensor = VectorUtils.getNormalizedTensor(trainingTensor);

			String[] partnerTerms = new String[queryTerms.length - i];
			for (int j = 0; j < queryTerms.length - i; ++j) {
				partnerTerms[j] = queryTerms[i + j];
			}
			this.partnerVector = CompoundVectorBuilder.getQueryVector(queryVecStore,
																																luceneUtils,
																																partnerTerms);
			if (VectorUtils.isZeroVector(this.partnerVector)) {
				System.err.println("Query vector is zero ... no results.");
				System.exit(-1);
			}
		}
		
		/**
		 * @param testVector Vector being tested.
		 * Scores are hopefully high when the relationship between queryVector
		 * and testVector is analogoues to the relationship between rel1 and rel2.
		 */
		public float getScore(float[] testVector) {
			float[][] testTensor =
				VectorUtils.getOuterProduct(this.partnerVector, testVector);
			return VectorUtils.getInnerProduct(this.trainingTensor, testTensor);
		}
	}
}
	/**
	 * Like VectorSearcherTensorSim, but uses convolution product.
	 *
	 static public class VectorSearcherConvolutionSim extends VectorSearcher {
	 private float[] relConvolution;
	 private float[] partnerVector;
	 /**
	 * @param searchVecStore The vector store to search.
	 * @param relTensor The tensor being used for
	 * comparison. This is turned into a convolution product
	 * internally, but passed in as a tensor to keep the same
	 * interface as VectorSearcherTensorSim.
	 * @param partnerVector The vector that will be paired with the
	 * test vector to form a convolution product for comparison.
	 *
	 public VectorSearcherConvolutionSim(VectorStore searchVecStore,
	 float[][] relTensor,
	 float[] partnerVector) {
	 super(searchVecStore);
	 this.relConvolution = VectorUtils.getConvolutionFromTensor(relTensor);
	 this.partnerVector = partnerVector;
	 }

	 /**
	 * @param testVector Vector being tested.
	 *
	 public float getScore(float[] testVector) {
	 float[] testConvolution =
	 VectorUtils.getConvolutionFromVectors(this.partnerVector, testVector);
	 return VectorUtils.scalarProduct(this.relConvolution, testConvolution);
	 }

	 /**
	 * @param rel1 Part of training relation.
	 * @param rel2 Part of training relation.
	 * Creates a relation convolution from rel1 and rel2, which query / test vectors
	 * will be compared with.
	 *
	 public VectorSearcherConvolutionSim(VectorStore searchVecStore,
	 float[] rel1, float[] rel2,
	 float[] partnerVector) {
	 super(searchVecStore);
	 // TODO (widdows): This isn't space efficient but permits entanglement.
	 // Should implement entangled sum for this to work, though.
	 float[][] relTensor = VectorUtils.getOuterProduct(rel1, rel2);
	 this.relConvolution = VectorUtils.getConvolutionFromTensor(relTensor);
	 this.partnerVector = partnerVector;
	 }
	 }


	 /**
	 * Class for searching a vector store using quantum disjunction similarity.
	 *
	 static public class VectorSearcherPlane extends VectorSearcher {
	 private ArrayList<float[]> disjunctSpace;
	 /**
	 * @param searchVecStore The vector store to search.
	 * @param queryString The string to parse into a subspace.
	 *
	 public VectorSearcherPlane(VectorStore searchVecStore,
	 String queryString) {
	 super(searchVecStore);
	 this.disjunctSpace = new ArrayList();

	 System.out.println(queryString);
	 String[] queryTerms = queryString.split(" ");
	 for (int i = 0; i < queryTerms.length; ++i) {
	 System.out.println("\t" + queryTerms[i]);
	 float[] tmpVector = vecStore.getVector(queryTerms[i]);
	 if (tmpVector != null) {
	 this.disjunctSpace.add(tmpVector);
	 }
	 }
	 VectorUtils.orthogonalizeVectors(this.disjunctSpace);
	 }

	 /**
	 * @param testVector Vector being tested.
	 *
	 * Scoring works by taking scalar product with disjunctSpace
	 * (which must by now be represented using an orthogonal basis).
	 *
	 public float getScore(float[] testVector) {
	 float score = 0;
	 for (int i = 0; i < disjunctSpace.size(); ++i) {
	 score += VectorUtils.scalarProduct(this.disjunctSpace.get(i), testVector);
	 }
	 return score;
	 }
	 }
	 }
	*/