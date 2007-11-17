package pitt.search.semanticvectors;

import org.apache.lucene.index.Term;

/**
 * This class contains methods for manipulating queries, e.g., taking
 * a list of queryterms and producing a (possibly weighted) aggregate
 * query vector. In the fullness of time this will hopefully include
 * several basic (quantum) logical operations.
 */

public class CompoundVectorBuilder {
    /**
     * Method gets a query vector from a query string, i.e., a space-separated list
     * of queryterms.
     */
    public static float[] getQueryVector(VectorStore vecReader,
					 LuceneUtils lUtils, 
					 String queryString) {
	String[] queryTerms = queryString.split(" ");
	float[] queryVec = new float[ObjectVector.vecLength];
	float[] tmpVec = new float[ObjectVector.vecLength];
	float weight = 1;

	for (int i = 0; i < ObjectVector.vecLength; ++i) {
	    queryVec[i] = 0;
	}
	
	for (int j = 0; j < queryTerms.length; ++j) {
	    tmpVec = vecReader.getVector(queryTerms[j]);
		
	    // try to get term weight; assume field is "contents"
	    if (lUtils != null) {
		weight = lUtils.getGlobalTermWeight(new Term("contents", queryTerms[j]));
	    }
	    else{ weight = 1; }

	    if (tmpVec != null) {
		System.err.println("Got vector for " + queryTerms[j] + 
				   ", using term weight " + weight);
		for (int i = 0; i < ObjectVector.vecLength; ++i) {
		    queryVec[i] += tmpVec[i];
		}
	    }
	    else{ System.err.println("No vector for " + queryTerms[j]); }
	}

	queryVec = VectorUtils.getNormalizedVector(queryVec);
	return queryVec;
    }    
}