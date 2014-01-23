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

import java.lang.IllegalArgumentException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Logger;

import pitt.search.semanticvectors.LuceneUtils;
import pitt.search.semanticvectors.VectorStore;
import pitt.search.semanticvectors.vectors.BinaryVectorUtils;
import pitt.search.semanticvectors.vectors.IncompatibleVectorsException;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorType;
import pitt.search.semanticvectors.vectors.VectorUtils;
import pitt.search.semanticvectors.vectors.ZeroVectorException;

/**
 * Class for searching vector stores using different scoring functions.
 * Each VectorSearcher implements a particular scoring function which is
 * normally query dependent, so each query needs its own VectorSearcher.
 */
abstract public class VectorSearcher {
  private static final Logger logger = Logger.getLogger(VectorSearcher.class.getCanonicalName());

  private FlagConfig flagConfig;
  private VectorStore searchVecStore;
  private LuceneUtils luceneUtils;

  /**
   * Expand search space for dual-predicate searches
   */  
  public static VectorStore expandSearchSpace(VectorStore searchVecStore, FlagConfig flagConfig) {
    VectorStoreRAM nusearchspace = new VectorStoreRAM(flagConfig);
    Enumeration<ObjectVector> allVectors = searchVecStore.getAllVectors();
    ArrayList<ObjectVector> storeVectors = new ArrayList<ObjectVector>();

    while (allVectors.hasMoreElements()) {
      ObjectVector nextObjectVector = allVectors.nextElement();
      nusearchspace.putVector(nextObjectVector.getObject(), nextObjectVector.getVector());
      storeVectors.add(nextObjectVector);
    }

    for (int x=0; x < storeVectors.size()-1; x++) {
      for (int y=x; y < storeVectors.size(); y++) {
        Vector vec1 = storeVectors.get(x).getVector().copy();
        Vector vec2 = storeVectors.get(y).getVector().copy();
        String obj1 = storeVectors.get(x).getObject().toString();
        String obj2 = storeVectors.get(y).getObject().toString();

        vec1.release(vec2);
        nusearchspace.putVector(obj2+":"+obj1, vec1);

        if (flagConfig.vectortype().equals(VectorType.COMPLEX)) {
          vec2.release(storeVectors.get(x).getVector().copy());
          nusearchspace.putVector(obj1+":"+obj2, vec2);
        }
      }
    }
    System.err.println("Expanding search space from "+storeVectors.size()+" to "+nusearchspace.getNumVectors());
    return nusearchspace;
  }

  /**
   * This needs to be filled in for each subclass. It takes an individual
   * vector and assigns it a relevance score for this VectorSearcher.
   */
  public abstract double getScore(Vector testVector);

  /**
   * Performs basic initialization; subclasses should normally call super() to use this.
   * @param queryVecStore Vector store to use for query generation.
   * @param searchVecStore The vector store to search.
   * @param luceneUtils LuceneUtils object to use for query weighting. (May be null.)
   * @param flagConfig Flag configuration (cannot be null).
   */
  public VectorSearcher(VectorStore queryVecStore,  VectorStore searchVecStore,
      LuceneUtils luceneUtils, FlagConfig flagConfig) {
    this.flagConfig = flagConfig;
    this.searchVecStore = searchVecStore;
    this.luceneUtils = luceneUtils;
    if (flagConfig.expandsearchspace()) {
      this.searchVecStore = expandSearchSpace(searchVecStore, flagConfig);
    }
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
  public LinkedList<SearchResult> getNearestNeighbors(int numResults) {
    final double unsetScore = -Math.PI;
    final int bufferSize = 1000;
    final int indexSize = numResults + bufferSize;
    LinkedList<SearchResult> results = new LinkedList<SearchResult>();
    List<SearchResult> tmpResults = new ArrayList<SearchResult>(indexSize);
    double score = -1;
    double threshold = flagConfig.searchresultsminscore();
    if (flagConfig.stdev()) threshold = 0;
    //Counters for statistics to calculate standard deviation
    double sum=0, sumsquared=0;
    int count=0;
    int pos = 0;
    for(int i=0; i < indexSize; i++)
    {
      tmpResults.add(new SearchResult(unsetScore, null));
    }

    Enumeration<ObjectVector> vecEnum = searchVecStore.getAllVectors();
    while (vecEnum.hasMoreElements()) {
      // Test this element.
      ObjectVector testElement = vecEnum.nextElement();
      score = getScore(testElement.getVector());

      // This is a way of using the Lucene Index to get term and
      // document frequency information to reweight all results. It
      // seems to be good at moving excessively common terms further
      // down the results. Note that using this means that scores
      // returned are no longer just cosine similarities.
      if (this.luceneUtils != null && flagConfig.usetermweightsinsearch()) {
        score = score *
            luceneUtils.getGlobalTermWeightFromString((String) testElement.getObject());
      }

      if (flagConfig.stdev()) {
        count++;
        sum += score;
        sumsquared += Math.pow(score, 2);
      }

      if (score > threshold) {
        // set existing object in buffer space
        tmpResults.get(numResults+pos++).set(score, testElement);
      }

      if(pos == bufferSize)
      {
        pos = 0;
        Collections.sort(tmpResults);
        threshold = tmpResults.get(indexSize - 1).getScore();
      }
    }
    
    Collections.sort(tmpResults);
    
    for(int i = 0; i < numResults; i++)
    {
      SearchResult sr = tmpResults.get(i);
      if(sr.getScore() == unsetScore)
      {
        break;
      }
      results.add(sr);
    }
    
    if (flagConfig.stdev()) results = transformToStats(results, count, sum, sumsquared);

    
    return results;
  }

  /**
   * This search is implemented in the abstract
   * VectorSearcher class itself: this enables all subclasses to reuse
   * the search whatever scoring method they implement.  Since query
   * expressions are built into the VectorSearcher,
   * getAllAboveThreshold does not  takes a query vector as an
   * argument.
   *
   * This will retrieve all the results above the threshold score passed
   * as a parameter. It is more computationally convenient than getNearestNeighbor
   * when large numbers of results are anticipated
   *
   * @param threshold minimum score required to get into results list.
   */
  public LinkedList<SearchResult> getAllAboveThreshold(float threshold) {
    LinkedList<SearchResult> results = new LinkedList<SearchResult>();
    double score;

    Enumeration<ObjectVector> vecEnum = null;
    vecEnum = searchVecStore.getAllVectors();

    while (vecEnum.hasMoreElements()) {
      // Test this element.
      ObjectVector testElement = vecEnum.nextElement();
      if (testElement == null) score = Float.MIN_VALUE;
      else
      {

        Vector testVector = testElement.getVector();
        score = getScore(testVector);

      }

      if (score > threshold || threshold == Float.MIN_VALUE) {
        results.add(new SearchResult(score, testElement));}
    }

    Collections.sort(results);
    return results;
  }

  /**
   * Class that searches based on cosine similarity with given queryvector.
   */
  static public class VectorSearcherPlain extends VectorSearcher {
    Vector queryVector;

    /**
     * Plain constructor that just fills in the query vector and vector store to be searched.
     */
    public VectorSearcherPlain(VectorStore searchVecStore, Vector queryVector, FlagConfig flagConfig) {
      super(searchVecStore, searchVecStore, null, flagConfig);
      this.queryVector = queryVector;
    }

    @Override
    public double getScore(Vector testVector) {
      return queryVector.measureOverlap(testVector);
    }
  }

  /**
   * Class for searching a vector store using cosine similarity.
   * Takes a sum of positive query terms and optionally negates some terms.
   */
  static public class VectorSearcherCosine extends VectorSearcher {
    Vector queryVector;
    /**
     * @param queryVecStore Vector store to use for query generation.
     * @param searchVecStore The vector store to search.
     * @param luceneUtils LuceneUtils object to use for query weighting. (May be null.)
     * @param queryTerms Terms that will be parsed into a query
     * expression. If the string "NOT" appears, terms after this will be negated.
     */
    public VectorSearcherCosine(
        VectorStore queryVecStore, VectorStore searchVecStore,
        LuceneUtils luceneUtils, FlagConfig flagConfig, String[] queryTerms)
            throws ZeroVectorException {
      super(queryVecStore, searchVecStore, luceneUtils, flagConfig);
      this.queryVector = CompoundVectorBuilder.getQueryVector(
          queryVecStore, luceneUtils, flagConfig, queryTerms);
      if (this.queryVector.isZeroVector()) {
        throw new ZeroVectorException("Query vector is zero ... no results.");
      }
    }

    /**
     * @param queryVecStore Vector store to use for query generation.
     * @param searchVecStore The vector store to search.
     * @param luceneUtils LuceneUtils object to use for query weighting. (May be null.)
     * @param queryVector Vector representing query
     * expression. If the string "NOT" appears, terms after this will be negated.
     */
    public VectorSearcherCosine(
        VectorStore queryVecStore, VectorStore searchVecStore,
        LuceneUtils luceneUtils, FlagConfig flagConfig, Vector queryVector)
            throws ZeroVectorException {
      super(queryVecStore, searchVecStore, luceneUtils, flagConfig);
      this.queryVector = queryVector;
      Vector testVector = searchVecStore.getAllVectors().nextElement().getVector();
      IncompatibleVectorsException.checkVectorsCompatible(queryVector, testVector);
      if (this.queryVector.isZeroVector()) {
        throw new ZeroVectorException("Query vector is zero ... no results.");
      }
    }

    @Override
    public double getScore(Vector testVector) {
      return this.queryVector.measureOverlap(testVector);
    }
  }

  /**
   * Class for searching a vector store using the bound product of a series two vectors.
   */
  static public class VectorSearcherBoundProduct extends VectorSearcher {
    Vector queryVector;

    public VectorSearcherBoundProduct(VectorStore queryVecStore, VectorStore boundVecStore,
        VectorStore searchVecStore, LuceneUtils luceneUtils, FlagConfig flagConfig, String term1, String term2)
            throws ZeroVectorException {
      super(queryVecStore, searchVecStore, luceneUtils, flagConfig);

      this.queryVector = CompoundVectorBuilder.getQueryVectorFromString(queryVecStore, null, flagConfig, term1);

      queryVector.release(CompoundVectorBuilder.getBoundProductQueryVectorFromString(
          flagConfig, boundVecStore, term2));

      if (this.queryVector.isZeroVector()) {
        throw new ZeroVectorException("Query vector is zero ... no results.");
      }
    }

    public VectorSearcherBoundProduct(VectorStore semanticVecStore, VectorStore elementalVecStore, VectorStore predicateVecStore,
        VectorStore searchVecStore, LuceneUtils luceneUtils, FlagConfig flagConfig, String term1)
            throws ZeroVectorException {
      super(semanticVecStore, searchVecStore, luceneUtils, flagConfig);

      this.queryVector = CompoundVectorBuilder.getBoundProductQueryVectorFromString(
          flagConfig, elementalVecStore, semanticVecStore, predicateVecStore, term1);

      if (this.queryVector.isZeroVector()) {
        throw new ZeroVectorException("Query vector is zero ... no results.");
      }
    }

    public VectorSearcherBoundProduct(VectorStore queryVecStore, VectorStore boundVecStore,
        VectorStore searchVecStore, LuceneUtils luceneUtils, FlagConfig flagConfig, ArrayList<Vector> incomingVectors)
            throws ZeroVectorException {
      super(queryVecStore, searchVecStore, luceneUtils, flagConfig);

      Vector theSuperposition = VectorFactory.createZeroVector(
          flagConfig.vectortype(), flagConfig.dimension());

      for (int q = 0; q < incomingVectors.size(); q++)
        theSuperposition.superpose(incomingVectors.get(q), 1, null);

      theSuperposition.normalize();
      this.queryVector = theSuperposition;

      if (this.queryVector.isZeroVector()) {
        throw new ZeroVectorException("Query vector is zero ... no results.");
      }
    }


    /**
     * @param queryVecStore Vector store to use for query generation.
     * @param searchVecStore The vector store to search.
     * @param luceneUtils LuceneUtils object to use for query weighting. (May be null.)
     * @param queryVector Vector representing query
     * expression. If the string "NOT" appears, terms after this will be negated.
     */
    public VectorSearcherBoundProduct(VectorStore queryVecStore,
        VectorStore searchVecStore,
        LuceneUtils luceneUtils,
        FlagConfig flagConfig,
        Vector queryVector)
            throws ZeroVectorException {
      super(queryVecStore, searchVecStore, luceneUtils, flagConfig);
      this.queryVector = queryVector;
      Vector testVector = searchVecStore.getAllVectors().nextElement().getVector();
      IncompatibleVectorsException.checkVectorsCompatible(queryVector, testVector);
      if (this.queryVector.isZeroVector()) {
        throw new ZeroVectorException("Query vector is zero ... no results.");
      }
    }

    @Override
    public double getScore(Vector testVector) {
      return this.queryVector.measureOverlap(testVector);
    }
  }

  /**
   * Class for searching a vector store using the bound product of a series two vectors.
   */
  public static class VectorSearcherBoundProductSubSpace extends VectorSearcher {
    private ArrayList<Vector> disjunctSpace;

    public VectorSearcherBoundProductSubSpace(VectorStore queryVecStore, VectorStore boundVecStore,
        VectorStore searchVecStore, LuceneUtils luceneUtils, FlagConfig flagConfig, String term1, String term2)
            throws ZeroVectorException {
      super(queryVecStore, searchVecStore, luceneUtils, flagConfig);

      disjunctSpace = new ArrayList<Vector>();
      Vector queryVector = queryVecStore.getVector(term1).copy();

      if (queryVector.isZeroVector()) {
        throw new ZeroVectorException("Query vector is zero ... no results.");
      }

      this.disjunctSpace = CompoundVectorBuilder.getBoundProductQuerySubSpaceFromString(
          flagConfig, boundVecStore, queryVector, term2);
    }

    public VectorSearcherBoundProductSubSpace(VectorStore elementalVecStore, VectorStore semanticVecStore, VectorStore predicateVecStore,
        VectorStore searchVecStore, LuceneUtils luceneUtils, FlagConfig flagConfig, String term1)
            throws ZeroVectorException {
      super(semanticVecStore, searchVecStore, luceneUtils, flagConfig);

      disjunctSpace = new ArrayList<Vector>();
      this.disjunctSpace = CompoundVectorBuilder.getBoundProductQuerySubspaceFromString(
          flagConfig, elementalVecStore, semanticVecStore, predicateVecStore, term1);

    }

    public VectorSearcherBoundProductSubSpace(VectorStore queryVecStore, VectorStore boundVecStore,
        VectorStore searchVecStore, LuceneUtils luceneUtils, FlagConfig flagConfig, ArrayList<Vector> incomingDisjunctSpace)
            throws ZeroVectorException {
      super(queryVecStore, searchVecStore, luceneUtils, flagConfig);

      this.disjunctSpace = incomingDisjunctSpace;
    }

    @Override
    public double getScore(Vector testVector) {
      return VectorUtils.compareWithProjection(testVector, disjunctSpace);
    }
  }

  /**
   * Class for searching a vector store using the bound product of a series two vectors.
   */
  public static class VectorSearcherBoundMinimum extends VectorSearcher {
    private ArrayList<Vector> disjunctSpace;

    public VectorSearcherBoundMinimum(VectorStore queryVecStore, VectorStore boundVecStore,
        VectorStore searchVecStore, LuceneUtils luceneUtils, FlagConfig flagConfig, String term1, String term2)
            throws ZeroVectorException {
      super(queryVecStore, searchVecStore, luceneUtils, flagConfig);

      disjunctSpace = new ArrayList<Vector>();
      Vector queryVector = queryVecStore.getVector(term1).copy();

      if (queryVector.isZeroVector()) {
        throw new ZeroVectorException("Query vector is zero ... no results.");
      }

      this.disjunctSpace = CompoundVectorBuilder.getBoundProductQuerySubSpaceFromString(
          flagConfig, boundVecStore, queryVector, term2);
    }

    public VectorSearcherBoundMinimum(VectorStore elementalVecStore, VectorStore semanticVecStore, VectorStore predicateVecStore, 
        VectorStore searchVecStore, LuceneUtils luceneUtils, FlagConfig flagConfig, String term1)
            throws ZeroVectorException {
      super(semanticVecStore, searchVecStore, luceneUtils, flagConfig);

      disjunctSpace = new ArrayList<Vector>();
      this.disjunctSpace = CompoundVectorBuilder.getBoundProductQuerySubspaceFromString(
          flagConfig, elementalVecStore, semanticVecStore, predicateVecStore, term1);

    }

    public VectorSearcherBoundMinimum(VectorStore queryVecStore, VectorStore boundVecStore,
        VectorStore searchVecStore, LuceneUtils luceneUtils, FlagConfig flagConfig, ArrayList<Vector> incomingDisjunctSpace)
            throws ZeroVectorException {
      super(queryVecStore, searchVecStore, luceneUtils, flagConfig);

      this.disjunctSpace = incomingDisjunctSpace;
    }

    @Override
    public double getScore(Vector testVector) {
    	double score = Double.MAX_VALUE;
    	for (int q=0; q < disjunctSpace.size(); q ++)
    		score = Math.min(score, testVector.measureOverlap(disjunctSpace.get(q)));
    	return score;
    }
  }
  
  
  /**
   * Class for searching a vector store using quantum disjunction similarity.
   */
  static public class VectorSearcherSubspaceSim extends VectorSearcher {
    private ArrayList<Vector> disjunctSpace;
    private VectorType vectorType;

    /**
     * @param queryVecStore Vector store to use for query generation.
     * @param searchVecStore The vector store to search.
     * @param luceneUtils LuceneUtils object to use for query weighting. (May be null.)
     * @param queryTerms Terms that will be parsed and used to generate a query subspace.
     */
    public VectorSearcherSubspaceSim(VectorStore queryVecStore,
        VectorStore searchVecStore,
        LuceneUtils luceneUtils,
        FlagConfig flagConfig,
        String[] queryTerms)
            throws ZeroVectorException {
      super(queryVecStore, searchVecStore, luceneUtils, flagConfig);
      this.disjunctSpace = new ArrayList<Vector>();

      for (int i = 0; i < queryTerms.length; ++i) {
        System.out.println("\t" + queryTerms[i]);
        // There may be compound disjuncts, e.g., "A NOT B" as a single argument.
        String[] tmpTerms = queryTerms[i].split("\\s");
        Vector tmpVector = CompoundVectorBuilder.getQueryVector(
            queryVecStore, luceneUtils, flagConfig, tmpTerms);
        if (tmpVector != null) {
          this.disjunctSpace.add(tmpVector);
        }
      }
      if (this.disjunctSpace.size() == 0) {
        throw new ZeroVectorException("No nonzero input vectors ... no results.");
      }
      if (!vectorType.equals(VectorType.BINARY))
        VectorUtils.orthogonalizeVectors(this.disjunctSpace);
      else BinaryVectorUtils.orthogonalizeVectors(this.disjunctSpace);
    }

    /**
     * Scoring works by taking scalar product with disjunctSpace
     * (which must by now be represented using an orthogonal basis).
     * @param testVector Vector being tested.
     */
    @Override
    public double getScore(Vector testVector) {
      if (!vectorType.equals(VectorType.BINARY))
        return VectorUtils.compareWithProjection(testVector, disjunctSpace);
      else return BinaryVectorUtils.compareWithProjection(testVector, disjunctSpace);
    }
  }

  /**
   * Class for searching a vector store using minimum distance similarity.
   */
  static public class VectorSearcherMaxSim extends VectorSearcher {
    private ArrayList<Vector> disjunctVectors;
    /**
     * @param queryVecStore Vector store to use for query generation.
     * @param searchVecStore The vector store to search.
     * @param luceneUtils LuceneUtils object to use for query weighting. (May be null.)
     * @param queryTerms Terms that will be parsed and used to generate a query subspace.
     */
    public VectorSearcherMaxSim(VectorStore queryVecStore,
        VectorStore searchVecStore,
        LuceneUtils luceneUtils,
        FlagConfig flagConfig,
        String[] queryTerms)
            throws ZeroVectorException {
      super(queryVecStore, searchVecStore, luceneUtils, flagConfig);
      this.disjunctVectors = new ArrayList<Vector>();

      for (int i = 0; i < queryTerms.length; ++i) {
        // There may be compound disjuncts, e.g., "A NOT B" as a single argument.
        String[] tmpTerms = queryTerms[i].split("\\s");
        Vector tmpVector = CompoundVectorBuilder.getQueryVector(
            queryVecStore, luceneUtils, flagConfig, tmpTerms);

        if (tmpVector != null) {
          this.disjunctVectors.add(tmpVector);
        }
      }
      if (this.disjunctVectors.size() == 0) {
        throw new ZeroVectorException("No nonzero input vectors ... no results.");
      }
    }

    /**
     * @param queryVecStore Vector store to use for query generation.
     * @param searchVecStore The vector store to search.
     * @param luceneUtils LuceneUtils object to use for query weighting. (May be null.)
     * @param queryTerms Terms that will be parsed and used to generate a query subspace.
     */
    public VectorSearcherMaxSim(VectorStore queryVecStore,
        VectorStore searchVecStore,
        LuceneUtils luceneUtils,
        FlagConfig flagConfig,
        Vector[] queryTerms)
            throws ZeroVectorException {
      super(queryVecStore, searchVecStore, luceneUtils, flagConfig);
      this.disjunctVectors = new ArrayList<Vector>();

      for (int i = 0; i < queryTerms.length; ++i) {
        // There may be compound disjuncts, e.g., "A NOT B" as a single argument.
        Vector tmpVector = queryTerms[i];

        if (tmpVector != null) {
          this.disjunctVectors.add(tmpVector);
        }
      }
      if (this.disjunctVectors.size() == 0) {
        throw new ZeroVectorException("No nonzero input vectors ... no results.");
      }
    }


    /**
     * Scoring works by taking scalar product with disjunctSpace
     * (which must by now be represented using an orthogonal basis).
     * @param testVector Vector being tested.
     */
    @Override
    public double getScore(Vector testVector) {
      double score = -1;
      double max_score = -1;
      for (int i = 0; i < disjunctVectors.size(); ++i) {
        score = this.disjunctVectors.get(i).measureOverlap(testVector);
        if (score > max_score) {
          max_score = score;
        }
      }
      return max_score;
    }

  }

  /**
   * Class for searching a vector store using minimum distance similarity 
   * (i.e. the minimum across the vector cues, which may 
   * be of use for finding middle terms).
   */
  static public class VectorSearcherMinSim extends VectorSearcher {
    private ArrayList<Vector> disjunctVectors;
    /**
     * @param queryVecStore Vector store to use for query generation.
     * @param searchVecStore The vector store to search.
     * @param luceneUtils LuceneUtils object to use for query weighting. (May be null.)
     * @param queryTerms Terms that will be parsed and used to generate a query subspace.
     */
    public VectorSearcherMinSim(VectorStore queryVecStore,
        VectorStore searchVecStore,
        LuceneUtils luceneUtils,
        FlagConfig flagConfig,
        String[] queryTerms)
            throws ZeroVectorException {
      super(queryVecStore, searchVecStore, luceneUtils, flagConfig);
      this.disjunctVectors = new ArrayList<Vector>();

      for (int i = 0; i < queryTerms.length; ++i) {
        // There may be compound disjuncts, e.g., "A NOT B" as a single argument.
        String[] tmpTerms = queryTerms[i].split("\\s");
        Vector tmpVector = CompoundVectorBuilder.getQueryVector(
            queryVecStore, luceneUtils, flagConfig, tmpTerms);

        if (tmpVector != null) {
          this.disjunctVectors.add(tmpVector);
        }
      }
      if (this.disjunctVectors.size() == 0) {
        throw new ZeroVectorException("No nonzero input vectors ... no results.");
      }
    }

    /**
     * @param queryVecStore Vector store to use for query generation.
     * @param searchVecStore The vector store to search.
     * @param luceneUtils LuceneUtils object to use for query weighting. (May be null.)
     * @param queryTerms Terms that will be parsed and used to generate a query subspace.
     */
    public VectorSearcherMinSim(VectorStore queryVecStore,
        VectorStore searchVecStore,
        LuceneUtils luceneUtils,
        FlagConfig flagConfig,
        Vector[] queryTerms)
            throws ZeroVectorException {
      super(queryVecStore, searchVecStore, luceneUtils, flagConfig);
      this.disjunctVectors = new ArrayList<Vector>();

      for (int i = 0; i < queryTerms.length; ++i) {
        // There may be compound disjuncts, e.g., "A NOT B" as a single argument.
        Vector tmpVector = queryTerms[i];

        if (tmpVector != null) {
          this.disjunctVectors.add(tmpVector);
        }
      }
      if (this.disjunctVectors.size() == 0) {
        throw new ZeroVectorException("No nonzero input vectors ... no results.");
      }
    }


    /**
     * Scoring works by taking scalar product with disjunctSpace
     * (which must by now be represented using an orthogonal basis).
     * @param testVector Vector being tested.
     */
    @Override
    public double getScore(Vector testVector) {
      double score = -1;
      double min_score = Double.MAX_VALUE;
      for (int i = 0; i < disjunctVectors.size(); ++i) {
        score = this.disjunctVectors.get(i).measureOverlap(testVector);
        if (score < min_score) {
          min_score = score;
        }
      }
      return min_score;
    }

  }
  
  
  /**
   * Class for searching a permuted vector store using cosine similarity.
   * Uses implementation of rotation for permutation proposed by Sahlgren et al 2008
   * Should find the term that appears frequently in the position p relative to the
   * index term (i.e. sat +1 would find a term occurring frequently immediately after "sat"
   */
  public static class VectorSearcherPerm extends VectorSearcher {
    Vector theAvg;

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
        FlagConfig flagConfig,
        String[] queryTerms)
            throws IllegalArgumentException, ZeroVectorException {
      super(queryVecStore, searchVecStore, luceneUtils, flagConfig);

      try {
        theAvg = pitt.search.semanticvectors.CompoundVectorBuilder.
            getPermutedQueryVector(queryVecStore, luceneUtils, flagConfig, queryTerms);
      } catch (IllegalArgumentException e) {
        logger.info("Couldn't create permutation VectorSearcher ...");
        throw e;
      }

      if (theAvg.isZeroVector()) {
        throw new ZeroVectorException("Permutation query vector is zero ... no results.");
      }
    }

    @Override
    public double getScore(Vector testVector) {
      return theAvg.measureOverlap(testVector);
    }
  }

  /**
   * Test searcher for finding a is to b as c is to ?
   *
   * Doesn't do well yet!
   *
   * @author dwiddows
   */
  static public class AnalogySearcher extends VectorSearcher {
    Vector queryVector;

    public AnalogySearcher(
        VectorStore queryVecStore, VectorStore searchVecStore,
        LuceneUtils luceneUtils, FlagConfig flagConfig, String[] queryTriple) {
      super(queryVecStore, searchVecStore, luceneUtils, flagConfig);
      Vector term0 = CompoundVectorBuilder.getQueryVectorFromString(queryVecStore, luceneUtils, flagConfig, queryTriple[0]);
      Vector term1 = CompoundVectorBuilder.getQueryVectorFromString(queryVecStore, luceneUtils, flagConfig, queryTriple[1]);
      Vector term2 = CompoundVectorBuilder.getQueryVectorFromString(queryVecStore, luceneUtils, flagConfig, queryTriple[2]);
      Vector relationVec = term0.copy();
      relationVec.bind(term1);
      this.queryVector = term2.copy();
      this.queryVector.release(relationVec);
    }

    @Override
    public double getScore(Vector testVector) {
      return queryVector.measureOverlap(testVector);
    }
  }

  /**
   * Class for searching a permuted vector store using cosine similarity.
   * Uses implementation of rotation for permutation proposed by Sahlgren et al 2008
   * Should find the term that appears frequently in the position p relative to the
   * index term (i.e. sat +1 would find a term occurring frequently immediately after "sat"
   * This is a variant that takes into account different results obtained when using either
   * permuted or random index vectors as the cue terms, by taking the mean of the results
   * obtained with each of these options.
   */
  static public class BalancedVectorSearcherPerm extends VectorSearcher {
    Vector oneDirection;
    Vector otherDirection;
    VectorStore searchVecStore, queryVecStore;
    // These "special" fields are here to enable non-static construction of these
    // static inherited classes. It suggests that the inheritance pattern for VectorSearcher
    // needs to be reconsidered.
    LuceneUtils specialLuceneUtils;
    FlagConfig specialFlagConfig;
    String[] queryTerms;

    /**
     * @param queryVecStore Vector store to use for query generation (this is also reversed).
     * @param searchVecStore The vector store to search (this is also reversed).
     * @param luceneUtils LuceneUtils object to use for query weighting. (May be null.)
     * @param queryTerms Terms that will be parsed into a query
     * expression. If the string "?" appears, terms best fitting into this position will be returned
     */
    public BalancedVectorSearcherPerm(
        VectorStore queryVecStore, VectorStore searchVecStore, LuceneUtils luceneUtils,
        FlagConfig flagConfig, String[] queryTerms)
            throws IllegalArgumentException, ZeroVectorException {
      super(queryVecStore, searchVecStore, luceneUtils, flagConfig);
      specialFlagConfig = flagConfig;
      specialLuceneUtils = luceneUtils;
      try {
        oneDirection = pitt.search.semanticvectors.CompoundVectorBuilder.
            getPermutedQueryVector(queryVecStore, luceneUtils, flagConfig, queryTerms);
        otherDirection = pitt.search.semanticvectors.CompoundVectorBuilder.
            getPermutedQueryVector(searchVecStore, luceneUtils, flagConfig, queryTerms);
      } catch (IllegalArgumentException e) {
        logger.info("Couldn't create balanced permutation VectorSearcher ...");
        throw e;
      }

      if (oneDirection.isZeroVector()) {
        throw new ZeroVectorException("Permutation query vector is zero ... no results.");
      }
    }

    /**
     * This overrides the nearest neighbor class implemented in the abstract
     * {@code VectorSearcher} class.
     *
     * WARNING: This implementation fails to respect flags used by the
     * {@code VectorSearcher.getNearestNeighbors} method.
     *
     * @param numResults the number of results / length of the result list.
     */
    @Override
    public LinkedList<SearchResult> getNearestNeighbors(int numResults) {
      LinkedList<SearchResult> results = new LinkedList<SearchResult>();
      double score, score1, score2 = -1;
      double threshold = specialFlagConfig.searchresultsminscore();
      if (specialFlagConfig.stdev())
        threshold = 0;

      // Counters for statistics to calculate standard deviation
      double sum=0, sumsquared=0;
      int count=0;

      Enumeration<ObjectVector> vecEnum = searchVecStore.getAllVectors();
      Enumeration<ObjectVector> vecEnum2 = queryVecStore.getAllVectors();
      while (vecEnum.hasMoreElements()) {
        // Test this element.
        ObjectVector testElement = vecEnum.nextElement();
        ObjectVector testElement2 = vecEnum2.nextElement();
        score1 = getScore(testElement.getVector());
        score2 = getScore2(testElement2.getVector());
        score = Math.max(score1,score2);

        // This is a way of using the Lucene Index to get term and
        // document frequency information to reweight all results. It
        // seems to be good at moving excessively common terms further
        // down the results. Note that using this means that scores
        // returned are no longer just cosine similarities.
        if ((specialLuceneUtils != null) && specialFlagConfig.usetermweightsinsearch()) {
          score = score * specialLuceneUtils.getGlobalTermWeightFromString((String) testElement.getObject());
        }

        if (specialFlagConfig.stdev()) {
        System.out.println("STDEV");
          count++;
          sum += score;
          sumsquared += Math.pow(score, 2);
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
      if (specialFlagConfig.stdev()) results = transformToStats(results, count, sum, sumsquared);
      return results;
    }

    @Override
    public double getScore(Vector testVector) {
      testVector.normalize();
      return oneDirection.measureOverlap(testVector);
    }
    public double getScore2(Vector testVector) {
      testVector.normalize();
      return (otherDirection.measureOverlap(testVector));
    }
  }

  /**
   * calculates approximation of standard deviation (using a somewhat imprecise single-pass algorithm)
   * and recasts top scores as number of standard deviations from the mean (for a single search)
   *
   * @return list of results with scores as number of standard deviations from mean
   */
  public LinkedList<SearchResult> transformToStats(
      LinkedList<SearchResult> rawResults,int count, double sum, double sumsq) {
    LinkedList<SearchResult> transformedResults = new LinkedList<SearchResult>();
    double variancesquared = sumsq - (Math.pow(sum,2)/count);
    double stdev = Math.sqrt(variancesquared/(count));
    double mean = sum/count;

    Iterator<SearchResult> iterator = rawResults.iterator();
    while (iterator.hasNext()) {
      SearchResult temp = iterator.next();
      double score = temp.getScore();
      score = new Double((score-mean)/stdev).floatValue();
      if (score > flagConfig.searchresultsminscore())
        transformedResults.add(new SearchResult(score, temp.getObjectVector()));
    }
    return transformedResults;
  }
}
