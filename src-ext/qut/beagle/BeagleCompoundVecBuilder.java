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

import pitt.search.semanticvectors.VectorStore;
import pitt.search.semanticvectors.vectors.RealVector;
import cern.colt.matrix.tfloat.impl.DenseFloatMatrix1D;

/**
 * This class is based on CompoundVectorBuilder and is used to construct
 * query vectors for searching.
 * 
 * @author Lance De Vine
 */

public class BeagleCompoundVecBuilder 
{

	BeagleNGramBuilder ngBuilder;
	public BeagleCompoundVecBuilder () 
	{			
		ngBuilder = BeagleNGramBuilder.getInstance();		
	}

	
	public float[] getNGramQueryVector(VectorStore vecReader, String[] queryTerms) throws IllegalArgumentException 
	{		
		// Check basic invariant that there must be one and only one "?" in input.
		int queryTermPosition = -1;
		for (int j = 0; j < queryTerms.length; ++j) 
		{
			if (queryTerms[j].equals("?")) 
			{ 
				if (queryTermPosition == -1) 
				{
					queryTermPosition = j;
				} 
				else 
				{
					// If we get to here, there was more than one "?" argument.
					System.err.println("Illegal query argument: arguments to getNGramQueryVector must " +
					"have only one '?' string to denote target term position.");
					throw new IllegalArgumentException();
				}
			}
		}
		// If we get to here, there were no "?" arguments.
		if (queryTermPosition == -1) 
		{
			System.err.println("Illegal query argument: arguments to getNGramQueryVector must " +
			"have exactly one '?' string to denote target term position.");
			throw new IllegalArgumentException();
		}		
			
		DenseFloatMatrix1D vec;
		
		int numpositions = queryTerms.length;
		
		ngBuilder.initialiseNGrams(numpositions);
		
		short[] positions = new short[numpositions];
		DenseFloatMatrix1D[] indexvectors = new DenseFloatMatrix1D[numpositions];
		String[] docterms = queryTerms;		
		float[] tmpVec;
		boolean problem = false;
		
		for (int i=0; i<numpositions; i++)
		{
			positions[i] = (short)i;
			if (i!=queryTermPosition) 
			{
				tmpVec = ((RealVector) vecReader.getVector(queryTerms[i])).getCoordinates();
				if (tmpVec==null)
				{
					problem = true;
					System.out.println("No vector for " + queryTerms[i]);
					break;
				}
				indexvectors[i] = new DenseFloatMatrix1D( tmpVec );
			}			
		}	
		
		if (problem) return null;
				
		vec = ngBuilder.generateNGramVector( docterms, positions, indexvectors, 0, numpositions, queryTermPosition ); 
				
		return vec.toArray();
	}
	
	
	
}




