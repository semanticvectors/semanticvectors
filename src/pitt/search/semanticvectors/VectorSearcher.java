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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Enumeration;
import java.util.logging.Logger;

import pitt.search.semanticvectors.LuceneUtils;
import pitt.search.semanticvectors.VectorSearcher;
import pitt.search.semanticvectors.VectorStore;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorUtils;

/**
 * Class for searching vector stores using different scoring functions.
 * Each VectorSearcher implements a particular scoring function which is
 * normally query dependent, so each query needs its own VectorSearcher.
 */
abstract public class VectorSearcher {
  private static final Logger logger = Logger.getLogger(VectorSearcher.class.getCanonicalName());

  private VectorStore searchVecStore;
  private LuceneUtils luceneUtils;

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
   */
  public VectorSearcher(VectorStore queryVecStore,
      VectorStore searchVecStore,
      LuceneUtils luceneUtils) {
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
  public LinkedList<SearchResult> getNearestNeighbors(int numResults) {
    LinkedList<SearchResult> results = new LinkedList<SearchResult>();
    double score = -1;
    double threshold = Flags.searchresultsminscore;
    if (Flags.stdev) threshold = 0;
    //Counters for statistics to calculate standard deviation
    double sum=0, sumsquared=0;
    int count=0;
    
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
      if (this.luceneUtils != null && Flags.usetermweightsinsearch) {
        score = score *
        luceneUtils.getGlobalTermWeightFromString((String) testElement.getObject());
      }

      if (Flags.stdev) {
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
    if (Flags.stdev) results = transformToStats(results, count, sum, sumsquared);
    return results;
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
    public VectorSearcherCosine(VectorStore queryVecStore,
        VectorStore searchVecStore,
        LuceneUtils luceneUtils,
        String[] queryTerms)
    throws ZeroVectorException {
      super(queryVecStore, searchVecStore, luceneUtils);
      this.queryVector = CompoundVectorBuilder.getQueryVector(queryVecStore,
          luceneUtils,
          queryTerms);
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
   * Class for searching a vector store using quantum disjunction similarity.
   */
  static public class VectorSearcherSubspaceSim extends VectorSearcher {
    private ArrayList<Vector> disjunctSpace;
    /**
     * @param queryVecStore Vector store to use for query generation.
     * @param searchVecStore The vector store to search.
     * @param luceneUtils LuceneUtils object to use for query weighting. (May be null.)
     * @param queryTerms Terms that will be parsed and used to generate a query subspace.
     */
    public VectorSearcherSubspaceSim(VectorStore queryVecStore,
        VectorStore searchVecStore,
        LuceneUtils luceneUtils,
        String[] queryTerms)
    throws ZeroVectorException {
      super(queryVecStore, searchVecStore, luceneUtils);
      this.disjunctSpace = new ArrayList<Vector>();

      for (int i = 0; i < queryTerms.length; ++i) {
        System.out.println("\t" + queryTerms[i]);
        // There may be compound disjuncts, e.g., "A NOT B" as a single argument.
        String[] tmpTerms = queryTerms[i].split("\\s");
        Vector tmpVector = CompoundVectorBuilder.getQueryVector(
            queryVecStore, luceneUtils, tmpTerms);
        if (tmpVector != null) {
          this.disjunctSpace.add(tmpVector);
        }
      }
      if (this.disjunctSpace.size() == 0) {
        throw new ZeroVectorException("No nonzero input vectors ... no results.");
      }

      VectorUtils.orthogonalizeVectors(this.disjunctSpace);
    }

    /**
     * Scoring works by taking scalar product with disjunctSpace
     * (which must by now be represented using an orthogonal basis).
     * @param testVector Vector being tested.
     */
    @Override
    public double getScore(Vector testVector) {
      return VectorUtils.compareWithProjection(testVector, disjunctSpace);
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
        String[] queryTerms)
    throws ZeroVectorException {
      super(queryVecStore, searchVecStore, luceneUtils);
      this.disjunctVectors = new ArrayList<Vector>();

      for (int i = 0; i < queryTerms.length; ++i) {
        // There may be compound disjuncts, e.g., "A NOT B" as a single argument.
        String[] tmpTerms = queryTerms[i].split("\\s");
        Vector tmpVector = CompoundVectorBuilder.getQueryVector(
            queryVecStore, luceneUtils, tmpTerms);

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
   * Class for searching a permuted vector store using cosine similarity.
   * Uses implementation of rotation for permutation proposed by Sahlgren et al 2008
   * Should find the term that appears frequently in the position p relative to the
   * index term (i.e. sat +1 would find a term occurring frequently immediately after "sat"
   */
  static public class VectorSearcherPerm extends VectorSearcher {
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
        String[] queryTerms)
    throws IllegalArgumentException, ZeroVectorException {
      super(queryVecStore, searchVecStore, luceneUtils);

      try {
        theAvg = pitt.search.semanticvectors.CompoundVectorBuilder.
        getPermutedQueryVector(queryVecStore, luceneUtils, queryTerms);
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
   * Class for searching a permuted vector store using cosine similarity.
   * Uses implementation of rotation for permutation proposed by Sahlgren et al 2008
   * Should find the term that appears frequently in the position p relative to the
   * index term (i.e. sat +1 would find a term occurring frequently immediately after "sat"
   * This is a variant that takes into account differt results obtained when using either
   * permuted or random index vectors as the cue terms, by taking the mean of the results
   * obtained with each of these options
   */
  static public class BalancedVectorSearcherPerm extends VectorSearcher {
    Vector oneDirection;
    Vector otherDirection;
    VectorStore searchVecStore, queryVecStore;
    LuceneUtils luceneUtils;
    String[] queryTerms;


    /**
     * @param queryVecStore Vector store to use for query generation (this is also reversed).
     * @param searchVecStore The vector store to search (this is also reversed).
     * @param luceneUtils LuceneUtils object to use for query weighting. (May be null.)
     * @param queryTerms Terms that will be parsed into a query
     * expression. If the string "?" appears, terms best fitting into this position will be returned
     */

    public BalancedVectorSearcherPerm(VectorStore queryVecStore,  VectorStore searchVecStore,  LuceneUtils luceneUtils,   String[] queryTerms)
    throws IllegalArgumentException, ZeroVectorException {
      super(queryVecStore, searchVecStore, luceneUtils);
      this.queryVecStore = queryVecStore;
      this.searchVecStore = searchVecStore;
      this.luceneUtils = luceneUtils;

      try {
        oneDirection = pitt.search.semanticvectors.CompoundVectorBuilder.
        getPermutedQueryVector(queryVecStore,luceneUtils,queryTerms);
        otherDirection = pitt.search.semanticvectors.CompoundVectorBuilder.
        getPermutedQueryVector(searchVecStore,luceneUtils,queryTerms);
      } catch (IllegalArgumentException e) {
        logger.info("Couldn't create balanced permutation VectorSearcher ...");
        throw e;
      }

      if (oneDirection.isZeroVector()) {
        throw new ZeroVectorException("Permutation query vector is zero ... no results.");
      }
    }

    /**
     * This overides the nearest neighbor class implemented in the abstract
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
      double threshold = Flags.searchresultsminscore;
      if (Flags.stdev) threshold = 0;

      //Counters for statistics to calculate standard deviation
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
        if ((this.luceneUtils != null) && Flags.usetermweightsinsearch) {
          score = score * luceneUtils.getGlobalTermWeightFromString((String) testElement.getObject());
        }

        if (Flags.stdev) {
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
      if (Flags.stdev) results = transformToStats(results, count, sum, sumsquared);
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
  public static LinkedList<SearchResult> transformToStats(
      LinkedList<SearchResult> rawResults,int count, double sum, double sumsq) {
    LinkedList<SearchResult> transformedResults = new LinkedList<SearchResult>();
    double variancesquared = sumsq - (Math.pow(sum,2)/count);
    double stdev =  Math.sqrt(variancesquared/(count)); 
    double mean = sum/count;

    Iterator<SearchResult> iterator = rawResults.iterator();
    while (iterator.hasNext()) {
      SearchResult temp = iterator.next();
      double score = temp.getScore();
      score = new Double((score-mean)/stdev).floatValue();
      if (score > Flags.searchresultsminscore)
        transformedResults.add(new SearchResult(score, temp.getObjectVector()));
    }
    return transformedResults;
  }
}
