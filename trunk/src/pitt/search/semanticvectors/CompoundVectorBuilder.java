
/**
   Copyright (c) 2007, University of Pittsburgh
   Copyright (c) 2008 and ongoing, the SemanticVectors authors

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

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * This class contains methods for manipulating queries, e.g., taking
 * a list of queryterms and producing a (possibly weighted) aggregate
 * query vector. In the fullness of time this will hopefully include
 * parsing and building queries that include basic (quantum) logical operations.
 * So far these basic operations include negation of one or more terms.
 */
public class CompoundVectorBuilder {
  private VectorStore vecReader;
  private LuceneUtils lUtils;

  private static final Logger logger =
    Logger.getLogger(CompoundVectorBuilder.class.getCanonicalName());

  public CompoundVectorBuilder (VectorStore vecReader, LuceneUtils lUtils) {
    this.vecReader = vecReader;
    this.lUtils = lUtils;
  }

  /**
   * Constructor that defaults LuceneUtils to null.
   */
  public CompoundVectorBuilder (VectorStore vecReader) {
    this.vecReader = vecReader;
    this.lUtils = null;
  }

  /**
   * Returns a vector representation containing both content and positional information
   * @param queryTerms String array of query terms to look up. Expects a single "?" entry, which
   * denotes the query term position. E.g., "martin ? king" might pick out "luther".
   *
   */
  public static float[] getPermutedQueryVector(VectorStore vecReader,
                                               LuceneUtils lUtils,
                                               String[] queryTerms) throws IllegalArgumentException {

    // Check basic invariant that there must be one and only one "?" in input.
    int queryTermPosition = -1;
    for (int j = 0; j < queryTerms.length; ++j) {
      if (queryTerms[j].equals("?")) {
        if (queryTermPosition == -1) {
          queryTermPosition = j;
        } else {
          // If we get to here, there was more than one "?" argument.
          logger.severe("Illegal query argument: arguments to getPermutedQueryVector must " +
                             "have only one '?' string to denote target term position.");
          throw new IllegalArgumentException();
        }
      }
    }
    // If we get to here, there were no "?" arguments.
    if (queryTermPosition == -1) {
      logger.severe("Illegal query argument: arguments to getPermutedQueryVector must " +
                         "have exactly one '?' string to denote target term position.");
      throw new IllegalArgumentException();
    }

    // Initialize other arguments.
    float[] queryVec = new float[Flags.dimension];
    for (int i = 0; i < Flags.dimension; ++i) {
      queryVec[i] = 0;
    }

    ArrayList<float[]> permutedVecs = new ArrayList<float[]>();
    float[] tmpVec = new float[Flags.dimension];
    float weight = 1;

    for (int j = 0; j < queryTerms.length; ++j) {
      if (j != queryTermPosition)	{
        tmpVec = vecReader.getVector(queryTerms[j]);
        int permutation = j - queryTermPosition;

        if (lUtils != null) {
          weight = lUtils.getGlobalTermWeightFromString(queryTerms[j]);
          logger.log(Level.INFO, "Term {0} weight {1}", new Object[]{queryTerms[j], weight});
        } else {
	  weight = 1;
	}

        if (tmpVec != null) {
          tmpVec = VectorUtils.permuteVector(tmpVec.clone(), permutation);
          permutedVecs.add(VectorUtils.getNormalizedVector(tmpVec));
          for (int i = 0; i < Flags.dimension; ++i) {
            tmpVec[i] = tmpVec[i] * weight;
            queryVec[i] += tmpVec[i];
          }
        } else {
          logger.log(Level.WARNING, "No vector for {0}", queryTerms[j]);
        }
      }
    }
    queryVec = VectorUtils.getNormalizedVector(queryVec);

    return queryVec;
  }

  /**
   * Method gets a query vector from a query string, i.e., a
   * space-separated list of queryterms.
   */
  public static float[] getQueryVectorFromString(VectorStore vecReader,
                                                 LuceneUtils lUtils,
                                                 String queryString) {
    String[] queryTerms = queryString.split("\\s");
    return getQueryVector(vecReader, lUtils, queryTerms);
  }

  /**
   * Method gets a query vector from an array of query terms. The
   * method is static and creates its own CompoundVectorBuilder.  This
   * enables client code just to call "getQueryVector" without
   * creating an object first, though this may be slightly less
   * efficient for multiple calls.
   * @param vecReader The vector store reader to use.
   * @param lUtils Lucene utilities for getting term weights.
   * @param queryTerms Query expression, e.g., from command line.  If
   *        the term NOT appears in queryTerms, terms after that will
   *        be negated.
   * @return queryVector, an array of floats representing the user's query.
   */
  public static float[] getQueryVector(VectorStore vecReader,
                                       LuceneUtils lUtils,
                                       String[] queryTerms) {
    CompoundVectorBuilder builder = new CompoundVectorBuilder(vecReader, lUtils);
    float[] returnVector = new float[Flags.dimension];
    // Check through args to see if we need to do negation.
    if (!Flags.suppressnegatedqueries) {
      for (int i = 0; i < queryTerms.length; ++i) {
	if (queryTerms[i].equalsIgnoreCase("NOT")) {
	  // If, so build negated query and return.
	  return builder.getNegatedQueryVector(queryTerms, i);
	}
      }
    }
    if (Flags.vectorlookupsyntax.equals("regex")) {
      returnVector = builder.getAdditiveQueryVectorRegex(queryTerms);
    } else {
      returnVector = builder.getAdditiveQueryVector(queryTerms);
    }
    return returnVector;
  }

  /**
   * Returns a (possibly weighted) normalized query vector created
   * by adding together vectors retrieved from vector store.
   * @param queryTerms String array of query terms to look up.
   */
  protected float[] getAdditiveQueryVector (String[] queryTerms) {
    float[] queryVec = new float[Flags.dimension];
    float[] tmpVec = new float[Flags.dimension];
    float weight = 1;

    for (int i = 0; i < Flags.dimension; ++i) {
      queryVec[i] = 0;
    }

    for (int j = 0; j < queryTerms.length; ++j) {
      tmpVec = vecReader.getVector(queryTerms[j]);

      if (lUtils != null) {
        weight = lUtils.getGlobalTermWeightFromString(queryTerms[j]);
      } else {
	weight = 1;
      }

      if (tmpVec != null) {
        for (int i = 0; i < Flags.dimension; ++i) {
          queryVec[i] += tmpVec[i] * weight;
        }
      } else {
	logger.log(Level.WARNING, "No vector for {0}", queryTerms[j]);
      }
    }

    queryVec = VectorUtils.getNormalizedVector(queryVec);
    return queryVec;
  }

  /**
   * Returns a (possibly weighted) normalized query vector created by
   * adding together all vectors retrieved from vector store whose
   * objects match a particular regular expression.
   * @param queryTerms String array of query terms to look up.
   */
  protected float[] getAdditiveQueryVectorRegex (String[] queryTerms) {
    float[] queryVec = new float[Flags.dimension];
    float weight = 1;

    for (int i = 0; i < Flags.dimension; ++i) {
      queryVec[i] = 0;
    }

    for (int j = 0; j < queryTerms.length; ++j) {
      // Compile a regular expression for matching anything containing this term.
      Pattern pattern = Pattern.compile(queryTerms[j]);
      logger.log(Level.FINER,"Query term pattern: {0}",pattern.pattern());
      Enumeration<ObjectVector> vecEnum = vecReader.getAllVectors();
      while (vecEnum.hasMoreElements()) {
        // Test this element.
        ObjectVector testElement = vecEnum.nextElement();
        Matcher matcher = pattern.matcher(testElement.getObject().toString());
        if (matcher.find()) {
          float[] tmpVec = testElement.getVector();

          if (lUtils != null) {
            weight = lUtils.getGlobalTermWeightFromString(testElement.getObject().toString());
          }
          else { weight = 1; }

          for (int i = 0; i < Flags.dimension; ++i) {
            queryVec[i] += tmpVec[i] * weight;
          }
        }
      }
    }
    queryVec = VectorUtils.getNormalizedVector(queryVec);
    return queryVec;
  }

  /**
   * Creates a vector including orthogonalizing negated terms.
   * @param queryTerms List of positive and negative terms.
   * @param split Position in this list of the NOT mark: terms
   * before this are positive, those after this are negative.
   * @return Single query vector, the sum of the positive terms,
   * projected to be orthogonal to all negative terms.
   * @see VectorUtils#orthogonalizeVectors
   */
  protected float[] getNegatedQueryVector(String[] queryTerms, int split) {
    int numNegativeTerms = queryTerms.length - split - 1;
    int numPositiveTerms = split;
    logger.log(Level.FINER, "Number of negative terms: {0}", numNegativeTerms);
    logger.log(Level.FINER, "Number of positive terms: {0}", numPositiveTerms);
    ArrayList<float[]> vectorList = new ArrayList<float[]>();
    for (int i = 1; i <= numNegativeTerms; ++i) {
      float[] tmpVector = vecReader.getVector(queryTerms[split + i]);
      if (tmpVector != null) {
        vectorList.add(tmpVector);
      }
    }
    String[] positiveTerms = new String[numPositiveTerms];
    for (int i = 0; i < numPositiveTerms; ++i) {
      positiveTerms[i] = queryTerms[i];
    }
    vectorList.add(getAdditiveQueryVector(positiveTerms));
    VectorUtils.orthogonalizeVectors(vectorList);
    return vectorList.get(vectorList.size() - 1);
  }
}
