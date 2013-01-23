/**
   Copyright (c) 2007 and ongoing, University of Pittsburgh
   and the SemanticVectors AUTHORS.

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

package pitt.util.vectors;

import pitt.search.semanticvectors.*;
import pitt.search.semanticvectors.vectors.IncompatibleVectorsException;
import pitt.search.semanticvectors.vectors.RealVector;
import ch.akuhn.edu.mit.tedlab.*;

/**
   Class for creating 2d plots of search results.

   Basic usage is something like:
   In the main semantic vectors source directory:
      ant compile-ext
   In the directory with your vector indexes:
      java pitt.util.vectors.PrincipalComponents $ARGS

   $ARGS includes first regular semantic vectors flags, e.g.,
   -queryvectorfile and -numsearchresults, followed by query terms.
 */
public class PrincipalComponents {
  ObjectVector[] vectorInput;
  DMat matrix;
  Svdlib svd;
  SVDRec svdR;
  int dimension;

  public PrincipalComponents (ObjectVector[] vectorInput) {
    this.vectorInput = vectorInput;
    this.dimension = vectorInput[0].getVector().getDimension();
    double[][] vectorArray = new double[vectorInput.length][dimension];

    for (int i = 0; i < vectorInput.length; ++i) {
      if (vectorInput[i].getVector().getClass() != RealVector.class) {
        throw new IncompatibleVectorsException(
            "Principal components class only works with Real Vectors so far!");
      }
      if (vectorInput[i].getVector().getDimension() != dimension) {
        throw new IncompatibleVectorsException("Dimensions must all be equal!");
      }
      RealVector realVector = (RealVector) vectorInput[i].getVector();
      float[] tempVec = realVector.getCoordinates().clone();
      for (int j = 0; j < dimension; ++j) {
        vectorArray[i][j] = (double) tempVec[j];
      }
    }
    this.matrix = new DMat(vectorArray.length, vectorArray[0].length);
    matrix.value = vectorArray;
    System.err.println("Created matrix ... performing svd ...");
    Svdlib svd = new Svdlib();
    System.err.println("Starting SVD using algorithm LAS2");
    svdR = svd.svdLAS2A(Svdlib.svdConvertDtoS(matrix), matrix.cols);
  }

  // Now we have an object with the reduced matrices, plot some reduced vectors.
  public void plotVectors() {
    DMat reducedVectors = this.svdR.Ut;
    ObjectVector[] plotVectors = new ObjectVector[vectorInput.length];
    int truncate = 4;
    for (int i = 0; i < vectorInput.length; i++) {
      float[] tempVec = new float[truncate];
      for (int j = 0; j < truncate; ++j) {
        tempVec[j] = (float) (reducedVectors.value[j][i]);
      }
      plotVectors[i] = new ObjectVector(vectorInput[i].getObject().toString(),
                                        new RealVector(tempVec));
    }
    Plot2dVectors myPlot = new Plot2dVectors(plotVectors);
    myPlot.createAndShowGUI();
  }

  /**
   * Main function gathers search results for a particular query,
   * performs svd, and plots results.
   */
  public static void main (String[] args) throws ZeroVectorException {
    // Stage i. Assemble command line options.
    FlagConfig flagConfig = FlagConfig.getFlagConfig(args);
    args = flagConfig.remainingArgs;

    // Get search results, perform clustering, and print out results.
    ObjectVector[] resultsVectors = Search.getSearchResultVectors(flagConfig, args, flagConfig.getNumsearchresults());
    PrincipalComponents pcs = new PrincipalComponents(resultsVectors);
    pcs.plotVectors();
  }
}
