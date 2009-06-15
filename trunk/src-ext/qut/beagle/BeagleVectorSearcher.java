/**
	 Copyright (c) 2009, Queensland University of Technology

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

package qut.beagle;

import pitt.search.semanticvectors.CompoundVectorBuilder;
import pitt.search.semanticvectors.LuceneUtils;
import pitt.search.semanticvectors.VectorSearcher;
import pitt.search.semanticvectors.VectorStore;
import pitt.search.semanticvectors.VectorUtils;
import pitt.search.semanticvectors.ZeroVectorException;

/**
 * This class extends VectorSeracher and is used to return a query vector
 * for searching and also to score vector comparisons.
 * 
 * @author Lance De Vine
 */

/*
* This class extends VectorSearcher and is used to construct
* query vectors for searching.
*
* @author Lance De Vine
*/

public class BeagleVectorSearcher extends VectorSearcher
{
	float[] queryVector;
	/**
	 * @param queryVecStore Vector store to use for query generation.
	 * @param searchVecStore The vector store to search.
	 * @param luceneUtils LuceneUtils object to use for query weighting. (May be null.)
	 * @param queryTerms Terms that will be parsed into a query expression. 
	 */
	public BeagleVectorSearcher(VectorStore queryVecStore, VectorStore searchVecStore,
															LuceneUtils luceneUtils,
															String[] queryTerms)
		throws ZeroVectorException 
	{
		super(queryVecStore, searchVecStore, luceneUtils);
				
		BeagleCompoundVecBuilder bcvb = new BeagleCompoundVecBuilder();
		
		queryVector = bcvb.getNGramQueryVector(queryVecStore, queryTerms);
				
		if (VectorUtils.isZeroVector(this.queryVector)) {
			throw new ZeroVectorException("Query vector is zero ... no results.");
		}
	}

	public float getScore(float[] testVector) 
	{
		return VectorUtils.scalarProduct(this.queryVector, testVector);	
	}	
	
}



