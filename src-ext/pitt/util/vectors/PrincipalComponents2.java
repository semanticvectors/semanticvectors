// Add Google copyright.

package pitt.util.vectors;

import pitt.search.semanticvectors.*;
import ch.akuhn.edu.mit.tedlab.*;

public class PrincipalComponents2 {
	ObjectVector[] vectorInput;
	DMat matrix;
	Svdlib svd;
	SVDRec svdR;
	int dimension;

	public PrincipalComponents2 (ObjectVector[] vectorInput) {
		this.vectorInput = vectorInput; 
		this.dimension = vectorInput[0].getVector().length;
		double[][] vectorArray = new double[vectorInput.length][dimension];

		for (int i = 0; i < vectorInput.length; ++i) {
	    float[] tempVec = vectorInput[i].getVector();
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
	    plotVectors[i] = new ObjectVector(vectorInput[i].getObject().toString(), tempVec);
		}
		Plot2dVectors myPlot = new Plot2dVectors(plotVectors);
		myPlot.createAndShowGUI();
	}

	/**
	 * Main function gathers search results for a particular query,
	 * performs svd, and plots results.
	 */
	public static void main (String[] args) throws ZeroVectorException {
		int numResults = 50;
		String[] searchArgs = args;

		/*
    // parse command-line args
    while (args[argc].substring(0, 1).equals("-")) {
      if (args[argc].equals("-v")) {
        queryFile = args[argc + 1];
        argc += 2;
      }
      else if (args[argc].equals("-l")) {
        lucenePath = args[argc + 1];
        argc += 2;
      }
      else if (args[argc].equals("-lookupsyntax")) {
        if (args[argc + 1].equalsIgnoreCase("regex")) {
          CompoundVectorBuilder.lookupSyntax = CompoundVectorBuilder.LookupSyntax.REGEX;
        }
        argc += 2;
      }
      else {
        usage();
        throw new IllegalArgumentException();
      }
    }
		*/
		// Get search results, perform clustering, and print out results.		
		ObjectVector[] resultsVectors = Search.getSearchResultVectors(searchArgs, numResults);
		PrincipalComponents2 pcs = new PrincipalComponents2(resultsVectors);
		pcs.plotVectors();
	}
}
