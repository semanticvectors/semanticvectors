/**
 Copyright (c) 2011, the SemanticVectors AUTHORS.

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
package pitt.search.semanticvectors.utils;

import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.SearchResult;
import pitt.search.semanticvectors.VectorSearcher;
import pitt.search.semanticvectors.VectorStoreReader;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.ZeroVectorException;

import java.io.IOException;
import java.util.List;

/**
 * Utility methods for working with predicates and other PSI-related constructs.
 *
 * @author dwiddows, tcohen
 */
public class PsiUtils {

  /**
   * Prints the nearest predicate for a particular flagConfig. (Please extend this comment!)
   *
   * @param flagConfig
   * @throws IOException
   */
  public static void printNearestPredicate(FlagConfig flagConfig) throws IOException {
    VerbatimLogger.info("Printing predicate results.");
    Vector queryVector = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());
    VectorSearcher.VectorSearcherBoundProduct predicateFinder;
    try {
      predicateFinder = new VectorSearcher.VectorSearcherBoundProduct(
          VectorStoreReader.openVectorStore(flagConfig.semanticvectorfile(), flagConfig),
          VectorStoreReader.openVectorStore(flagConfig.boundvectorfile(), flagConfig),
          null, flagConfig, queryVector);
      List<SearchResult> bestPredicate = predicateFinder.getNearestNeighbors(1);
      if (bestPredicate.size() > 0) {
        String pred = bestPredicate.get(0).getObjectVector().getObject().toString();
        System.out.println(pred);
      }
    } catch (ZeroVectorException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
}