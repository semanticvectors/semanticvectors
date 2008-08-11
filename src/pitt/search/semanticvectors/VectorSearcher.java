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
import java.lang.IllegalArgumentException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Enumeration;

import pitt.search.semanticvectors.LuceneUtils;
import pitt.search.semanticvectors.VectorSearcher;
import pitt.search.semanticvectors.VectorStore;
import pitt.search.semanticvectors.VectorUtils;

/**
 * Class for searching vector stores using different scoring functions.
 * Each VectorSearcher implements a particular scoring function which is 
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


	/**
	 * Performs basic initialization; subclasses should normally call super() to use this.
	 * @param queryVecStore Vector store to use for query generation.
	 * @param searchVecStore The vector store to search.
	 * @param luceneUtils LuceneUtils object to use for query weighting. (May be null.)
	 */
	public VectorSearcher(VectorStore queryVecStore,
												VectorStore searchVecStore,
												LuceneUtils luceneUtils) {
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
	public LinkedList getNearestNeighbors(int numResults) {
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

			// This is a way of using the Lucene Index to get term and
			// document frequency information to reweight all results. It
			// seems to be good at moving excessively common terms further
			// down the results. Note that using this means that scores
			// returned are no longer just cosine similarities.
			if (this.luceneUtils != null) {
				score = score *
					luceneUtils.getGlobalTermWeightFromString((String) testElement.getObject());
			}

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
	 * Takes a sum of positive query terms and optionally negates some terms.
	 */
	static public class VectorSearcherCosine extends VectorSearcher {
		float[] queryVector;
		/**
		 * @param queryVecStore Vector store to use for query generation.
		 * @param searchVecStore The vector store to search.
		 * @param luceneUtils LuceneUtils object to use for query weighting. (May be null.)
		 * @param queryTerms Terms that will be parsed into a query
		 * expression. If the string "NOT" appears, terms after this will be negated.
		 */
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
			//testVector = VectorUtils.getNormalizedVector(testVector);
	    return VectorUtils.scalarProduct(this.queryVector, testVector);
		}
	}

	/**
	 * Class for searching a vector store using sparse cosine similarity.
	 * Takes a sum of positive query terms and optionally negates some terms.
	 */
	static public class VectorSearcherCosineSparse extends VectorSearcher {
		float[] queryVector;
		/**
		 * @param queryVecStore Vector store to use for query generation.
		 * @param searchVecStore The vector store to search.
		 * @param luceneUtils LuceneUtils object to use for query weighting. (May be null.)
		 * @param queryTerms Terms that will be parsed into a query
		 * expression. If the string "NOT" appears, terms after this will be negated.
		 */
		public VectorSearcherCosineSparse(VectorStore queryVecStore,
																			VectorStore searchVecStore,
																			LuceneUtils luceneUtils,
																			String[] queryTerms) {
	    super(queryVecStore, searchVecStore, luceneUtils);
			float[] fullQueryVector = CompoundVectorBuilder.getQueryVector(queryVecStore,
																																		 luceneUtils,
																																		 queryTerms);

			if (VectorUtils.isZeroVector(fullQueryVector)) {
				System.err.println("Query vector is zero ... no results.");
				System.exit(-1);
			}

			short[] sparseQueryVector =
				VectorUtils.floatVectorToSparseVector(fullQueryVector, 20);
			this.queryVector = 
				VectorUtils.sparseVectorToFloatVector(sparseQueryVector, ObjectVector.vecLength);
		}

		public float getScore(float[] testVector) {
			//testVector = VectorUtils.getNormalizedVector(testVector);
			short[] sparseTestVector =
				VectorUtils.floatVectorToSparseVector(testVector, 40);
			testVector = 
				VectorUtils.sparseVectorToFloatVector(sparseTestVector, ObjectVector.vecLength);
	    return VectorUtils.scalarProduct(this.queryVector, testVector);
		}
	}


	/**
	 * Class for searching a vector store using tensor product
	 * similarity.  The class takes a seed tensor as a training
	 * example. This tensor should be entangled (a superposition of
	 * several individual products A * B) for non-trivial results.
	 */
	static public class VectorSearcherTensorSim extends VectorSearcher {
		private float[][] trainingTensor;
		private float[] partnerVector;
		/**
		 * @param queryVecStore Vector store to use for query generation.
		 * @param searchVecStore The vector store to search.
		 * @param luceneUtils LuceneUtils object to use for query weighting. (May be null.)
		 * @param queryTerms Terms that will be parsed into a query
		 * expression. This should be a list of one or more
		 * tilde-separated training pairs, e.g., <code>paris~france
		 * berlin~germany</code> followed by a list of one or more search
		 * terms, e.g., <code>london birmingham</code>.
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

			// This is an explicit way of taking a slice of the last i
			// terms. There may be a quicker way of doing this.
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
		 * and testVector is analogous to the relationship between rel1 and rel2.
		 */
		public float getScore(float[] testVector) {
			float[][] testTensor =
				VectorUtils.getOuterProduct(this.partnerVector, testVector);
			return VectorUtils.getInnerProduct(this.trainingTensor, testTensor);
		}
	}

	/**
	 * Class for searching a vector store using convolution similarity.
	 * Interface is similar to that for VectorSearcherTensorSim.
	 */
	static public class VectorSearcherConvolutionSim extends VectorSearcher {
		private float[] trainingConvolution;
		private float[] partnerVector;
		/**
		 * @param queryVecStore Vector store to use for query generation.
		 * @param searchVecStore The vector store to search.
		 * @param luceneUtils LuceneUtils object to use for query weighting. (May be null.)
		 * @param queryTerms Terms that will be parsed into a query
		 * expression. This should be a list of one or more
		 * tilde-separated training pairs, e.g., <code>paris~france
		 * berlin~germany</code> followed by a list of one or more search
		 * terms, e.g., <code>london birmingham</code>.
		 */
		public VectorSearcherConvolutionSim(VectorStore queryVecStore,
																				VectorStore searchVecStore,
																				LuceneUtils luceneUtils,
																				String[] queryTerms) {
			super(queryVecStore, searchVecStore, luceneUtils);
			this.trainingConvolution = new float[2 * ObjectVector.vecLength - 1];
			for (int i = 0; i < 2 * ObjectVector.vecLength - 1; ++i) {
				this.trainingConvolution[i] = 0;
			}

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
					float[] trainingPair =
						VectorUtils.getConvolutionFromVectors(trainingVec1, trainingVec2);
					for (int j = 0; j < 2 * ObjectVector.vecLength - 1; ++j) {
						this.trainingConvolution[j] += trainingPair[j];
					}
				}
				++i;
			}

			// Check to see that we got a non-zero training tensor before moving on.
			if (VectorUtils.isZeroVector(trainingConvolution)) {
				System.err.println("Convolution training relation is zero ... no results.");
				System.exit(-1);
			}
			this.trainingConvolution = VectorUtils.getNormalizedVector(trainingConvolution);

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
			float[] testConvolution =
				VectorUtils.getConvolutionFromVectors(this.partnerVector, testVector);
			testConvolution = VectorUtils.getNormalizedVector(testConvolution);
			return VectorUtils.scalarProduct(this.trainingConvolution, testConvolution);
		}
	}

	/**
	 * Class for searching a vector store using quantum disjunction similarity.
	 */	 
	static public class VectorSearcherSubspaceSim extends VectorSearcher {
		private ArrayList<float[]> disjunctSpace;
		/**
		 * @param queryVecStore Vector store to use for query generation.
		 * @param searchVecStore The vector store to search.
		 * @param luceneUtils LuceneUtils object to use for query weighting. (May be null.)
		 * @param queryTerms Terms that will be parsed and used to generate a query subspace.
		 */
		public VectorSearcherSubspaceSim(VectorStore queryVecStore,
																		 VectorStore searchVecStore,
																		 LuceneUtils luceneUtils,
																		 String[] queryTerms) {
			super(queryVecStore, searchVecStore, luceneUtils);
			this.disjunctSpace = new ArrayList();

			for (int i = 0; i < queryTerms.length; ++i) {
				System.out.println("\t" + queryTerms[i]);
				// There may be compound disjuncts, e.g., "A NOT B" as a single argument.
				String[] tmpTerms = queryTerms[i].split("\\s");
				float[] tmpVector = CompoundVectorBuilder.getQueryVector(queryVecStore,
																																 luceneUtils,
																																 tmpTerms);
				if (tmpVector != null) {
					this.disjunctSpace.add(tmpVector);
				}
			}
			VectorUtils.orthogonalizeVectors(this.disjunctSpace);
		}

		/**
		 * Scoring works by taking scalar product with disjunctSpace
		 * (which must by now be represented using an orthogonal basis).
		 * @param testVector Vector being tested.
		 */
		public float getScore(float[] testVector) {
			return VectorUtils.getSumScalarProduct(testVector, disjunctSpace);
		}
	}

	/**
	 * Class for searching a vector store using minimum distance similarity.
	 */	 
	static public class VectorSearcherMaxSim extends VectorSearcher {
		private ArrayList<float[]> disjunctVectors;
		/**
		 * @param queryVecStore Vector store to use for query generation.
		 * @param searchVecStore The vector store to search.
		 * @param luceneUtils LuceneUtils object to use for query weighting. (May be null.)
		 * @param queryTerms Terms that will be parsed and used to generate a query subspace.
		 */
		public VectorSearcherMaxSim(VectorStore queryVecStore,
																VectorStore searchVecStore,
																LuceneUtils luceneUtils,
																String[] queryTerms) {
			super(queryVecStore, searchVecStore, luceneUtils);
			this.disjunctVectors = new ArrayList();

			for (int i = 0; i < queryTerms.length; ++i) {
				// There may be compound disjuncts, e.g., "A NOT B" as a single argument.
				String[] tmpTerms = queryTerms[i].split("\\s");
				float[] tmpVector = CompoundVectorBuilder.getQueryVector(queryVecStore,
																																 luceneUtils,
																																 tmpTerms);
				if (tmpVector != null) {
					this.disjunctVectors.add(tmpVector);
				}
			}
		}

		/**
		 * Scoring works by taking scalar product with disjunctSpace
		 * (which must by now be represented using an orthogonal basis).
		 * @param testVector Vector being tested.
		 */
		public float getScore(float[] testVector) {
			float score = -1;
			float max_score = -1;
			for (int i = 0; i < disjunctVectors.size(); ++i) {
				score = VectorUtils.scalarProduct(this.disjunctVectors.get(i), testVector);
				if (score > max_score) {
					max_score = score;
				}
			}
			return max_score;
		}
	
	}
	/**
	 * Class for searching a permuted vector store using cosine similarity.
	 * Uses implementation of rotation for permutation proposed by Sahlgren et al 2008
	 * Should find the term that appears frequently in the position p relative to the
	 * index term (i.e. sat +1 would find a term occurring frequently immediately after "sat"
	 */
	static public class VectorSearcherPerm extends VectorSearcher {
		float[] theAvg;
		
		/**
		 * @param queryVecStore Vector store to use for query generation.
		 * @param searchVecStore The vector store to search.
		 * @param luceneUtils LuceneUtils object to use for query weighting. (May be null.)
		 * @param queryTerms Terms that will be parsed into a query
		 * expression. If the string "?" appears, terms best fitting into this position will be returned
		 */
		public VectorSearcherPerm(VectorStore queryVecStore,
																VectorStore searchVecStore,
																LuceneUtils luceneUtils,
																String[] queryTerms)
			throws IllegalArgumentException {
	    super(queryVecStore, searchVecStore, luceneUtils);
			
			try {
				theAvg = pitt.search.semanticvectors.CompoundVectorBuilder.getPermutedQueryVector(
																						           queryVecStore,luceneUtils,queryTerms);
			} catch (IllegalArgumentException e) {
				System.err.println("Couldn't create permutation VectorSearcher ...");
				throw e;
			}
		}

		public float getScore(float[] testVector) {
	   return VectorUtils.scalarProduct(theAvg, testVector);
		}
	}
	
}
