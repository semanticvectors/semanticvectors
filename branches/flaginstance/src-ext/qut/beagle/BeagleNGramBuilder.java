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

import java.util.ArrayList;

import pitt.search.semanticvectors.FlagConfig;

import cern.colt.matrix.tfloat.impl.DenseFloatMatrix1D;
import cern.jet.random.tdouble.Normal;
import cern.jet.random.tdouble.engine.DoubleMersenneTwister;

/**
 * This class is used to construct ngram vectors for use by BeagleNGramVectors and BeagleCompoundVecBuilder
 *
 * The primary method is:
 * generateNGramVector( docterms, positions, localindexvectors, start, end, focus )
 *
 * Ngram vectors are built by:
 * a) convolving term vectors before the focus term with rotations added to neutralise
 * the associativity of the convolution operation.
 * b) convolving term vectors after the focus term with rotations added to neutralise
 * the associativity of the convolution operation.
 * c) convolving the result of a) and b)
 *
 * This is slightly different to the way that Jones and Mewhort encode word but is used for
 * performance reasons since it eliminates one of the convolution operations.
 *
 * @author Lance De Vine
 */
public class BeagleNGramBuilder
{
  private FlagConfig flagConfig;
	private static BeagleNGramBuilder instance = null;
	private BeagleUtils utils;

	private DenseFloatMatrix1D phi;
	private int[] Permute1;
	private int[] Permute2;

	// For performance reasons: arrays for holding the ngrams generated for a particular
	// document. The ngrams are generated and kept so they can be re-used. They are
	// indexed by term position within the document.
	private ArrayList<DenseFloatMatrix1D> ngrams2 = new ArrayList<DenseFloatMatrix1D>();
	private ArrayList<DenseFloatMatrix1D> ngrams3 = new ArrayList<DenseFloatMatrix1D>();
	private ArrayList<DenseFloatMatrix1D> ngrams4 = new ArrayList<DenseFloatMatrix1D>();


	protected BeagleNGramBuilder(FlagConfig flagConfig)
	{
	  this.flagConfig = flagConfig;
		utils = BeagleUtils.getInstance();
		utils.setNormal( 0.0f, (float)(Math.sqrt(1.0/(double)flagConfig.getDimension())));

		phi = utils.generateColtRandomVector(flagConfig.getDimension());
		Permute1 = utils.makeScrambledIntArray(flagConfig.getDimension());
		Permute2 = utils.makeScrambledIntArray(flagConfig.getDimension());
	}

	public static BeagleNGramBuilder getInstance(FlagConfig flagConfig)
	{
      if(instance == null) {
         instance = new BeagleNGramBuilder(flagConfig);
      } else if (instance.flagConfig != flagConfig) {
        throw new IllegalArgumentException(
            "Trying to create instances with two different FlagConfig objects. This is not supported.");
      }
      return instance;
	}

	public void initialise()
	{
		utils.initialise();

	}

	public void initialiseNGrams( int size )
	{
		ensureSize(ngrams2, size);
		ensureSize(ngrams3, size);
		ensureSize(ngrams4, size);

		for (int i = 0; i < size; ++i)
		{
			ngrams2.set(i, null);
			ngrams3.set(i, null);
			ngrams4.set(i, null);
		}

	}

	public void setFFTCacheSize( int size )
	{
		System.out.println("Size: " + size);
		utils.setFFTCacheSize(size);
	}

	protected void ensureSize( ArrayList<DenseFloatMatrix1D> list, int size )
	{
		int lSize = list.size();
		if (size <= lSize) return;

		for (int i=lSize; i<size; i++) list.add( null );
	}

	protected DenseFloatMatrix1D get2Gram( String[] docterms, short[] positions,
			  DenseFloatMatrix1D[] localindexvectors, int start )
	  {
		  DenseFloatMatrix1D ngram = ngrams2.get(start);

		  if (ngram==null)
		  {
			  // Need to generate the ngram
			  ngram = generate2GramVector( docterms, positions, localindexvectors, start );
			  ngrams2.set(start, ngram);
		  }

		  return ngram;
	  }

	  protected DenseFloatMatrix1D get3Gram( String[] docterms, short[] positions,
			  DenseFloatMatrix1D[] localindexvectors, int start )
	  {

		  DenseFloatMatrix1D ngram = ngrams3.get(start);

		  if (ngram==null)
		  {
			  // Need to generate the ngram
			  ngram = generate3GramVector( docterms, positions, localindexvectors, start );
			  ngrams3.set(start, ngram);
		  }
		  return ngram;
	  }

	  protected DenseFloatMatrix1D get4Gram( String[] docterms, short[] positions,
			  DenseFloatMatrix1D[] localindexvectors, int start )
	  {
		  DenseFloatMatrix1D ngram = ngrams4.get(start);

		  if (ngram==null)
		  {
			  // Need to generate the ngram
			  ngram = generate4GramVector( docterms, positions, localindexvectors, start );
			  ngrams4.set(start, ngram);
		  }
		  return ngram;
	  }

	/**
	   * This method generates a 2-gram vector.
	   *
	   * @params docterms Array of terms in the document
	   * @param positions An array of term ids for terms at the corresponding positions.
	   * @param localindexvectors An array of index vectors local to the document.
	   * @param start The term position within the document at which this ngram starts
	   * @return The vector generated by convolving the terms in the ngram.
	   */
	  protected DenseFloatMatrix1D generate2GramVector( String[] docterms, short[] positions,
			  DenseFloatMatrix1D[] localindexvectors, int start )
	  {
		  int termId1, termId2;
		  DenseFloatMatrix1D vec1, vec2;

		  termId1 = positions[start];
		  vec1 = localindexvectors[termId1];

		  termId2 = positions[start+1];
		  vec2 = utils.rotateVector(localindexvectors[termId2],1);

		  vec2 = utils.doConvolveFFT( vec1, vec2, docterms[termId1], null );

		  return vec2;
	  }

	  protected DenseFloatMatrix1D generate3GramVector( String[] docterms, short[] positions,
			  DenseFloatMatrix1D[] localindexvectors, int start )
	  {
		  int termId3;

		  DenseFloatMatrix1D ngram2 = get2Gram( docterms, positions, localindexvectors, start );
		  DenseFloatMatrix1D vec;

		  termId3 = positions[start+2];
		  vec = utils.rotateVector(localindexvectors[termId3],2);
		  vec = utils.doConvolveFFT( ngram2, vec );

		  return vec;
	  }

	  protected DenseFloatMatrix1D generate4GramVector( String[] docterms, short[] positions,
			  DenseFloatMatrix1D[] localindexvectors, int start )
	  {
		  int termId4;

		  DenseFloatMatrix1D ngram3 = get3Gram( docterms, positions, localindexvectors, start );
		  DenseFloatMatrix1D vec;

		  termId4 = positions[start+3];
		  vec = utils.rotateVector(localindexvectors[termId4],3);
		  vec = utils.doConvolveFFT( ngram3, vec );

		  return vec;
	  }


	  /**
	   * This method generates a vector by convolving the index vectors of the
	   * terms in the ngram.
	   *
	   * @params docterms Array of terms in the document
	   * @param positions An array of term ids for terms at the corresponding positions.
	   * @param localindexvectors An array of index vectors local to the document.
	   * @param start The term position within the document at which this ngram starts
	   * @param end The term position within the document at which this ngram ends
	   * @param focus The focus word
	   * @return The vector generated by convolving the terms in the ngram.
	   */

	  protected DenseFloatMatrix1D generateNGramVector( String[] docterms, short[] positions,
			  DenseFloatMatrix1D[] localindexvectors, int start, int end, int focus )
	  {
		  DenseFloatMatrix1D vec1, vec2;
		  int termId1, termId2;
		  int ngramSize = end-start;

		  // If this is a 2-gram
		  if (ngramSize==2)
		  {
				if (start == focus)
				{
					termId1 = positions[start+1];
					vec1 = localindexvectors[termId1];
					vec1 = utils.rotateVector(vec1, 1);
				}
				else
				{
					termId1 = positions[start];
					vec1 = localindexvectors[termId1];
					vec1 = utils.rotateVector(vec1, -1);
				}

				return vec1;
		  }

		// If this is a 3-gram
		  if (ngramSize==3)
		  {
				if (start == focus)
				{
					vec1 = get2Gram( docterms, positions, localindexvectors, start+1 );
					vec1 = utils.rotateVector(vec1, 1);
				}
				else if ((start+1) == focus)
				{
					termId1 = positions[start];
					vec1 = utils.rotateVector(localindexvectors[termId1],-1);

					termId2 = positions[start+2];
					vec2 = utils.rotateVector(localindexvectors[termId2],1);

					vec1 = utils.doConvolveFFT( vec1, vec2 );
				}
				else
				{
					vec1 = get2Gram( docterms, positions, localindexvectors, start );
					vec1 = utils.rotateVector(vec1, -1);
				}

				return vec1;
		  }

		// If this is a 4-gram
		  if (ngramSize==4)
		  {
			  if (start == focus)
			  {
				  vec1 = get3Gram( docterms, positions, localindexvectors, start+1 );
				  //vec1 = ngrams3.get(start+1);
				  vec1 = utils.rotateVector(vec1, 1);
			  }
			  else if ((start+1) == focus)
			  {
				  termId1 = positions[start];
				  vec1 = utils.rotateVector(localindexvectors[termId1],-1);

				  vec2 = get2Gram( docterms, positions, localindexvectors, start+2 );
				  //vec2 = ngrams2.get(start+2);
				  vec2 = utils.rotateVector( vec2, 1);

				  vec1 = utils.doConvolveFFT( vec1, vec2 );
			  }
			  else if ((start+2) == focus)
			  {
				  termId2 = positions[start+3];

				  vec1 = get2Gram( docterms, positions, localindexvectors, start );
				  //vec1 = ngrams2.get(start);
				  vec1 = utils.rotateVector( vec1, -1);

				  vec2 = utils.rotateVector(localindexvectors[termId2],1);

				  vec1 = utils.doConvolveFFT( vec1, vec2 );
			  }
			  else
			  {
				  vec1 = get3Gram( docterms, positions, localindexvectors, start );
				  vec1 = ngrams3.get(start);
				  vec1 = utils.rotateVector(vec1, -1);
			  }

			  return vec1;
		  }

		  // If this is a 5-gram
		  if (ngramSize==5)
		  {
			  if (start == focus)
			  {
				  vec1 = get4Gram( docterms, positions, localindexvectors, start+1 );
				  //vec1 = ngrams4.get(start+1);
				  vec1 = utils.rotateVector(vec1, 1);
			  }
			  else if ((start+1) == focus)
			  {
				  termId1 = positions[start];
				  vec1 = utils.rotateVector(localindexvectors[termId1],-1);

				  vec2 = get3Gram( docterms, positions, localindexvectors, start+2 );
				  //vec2 = ngrams3.get(start+2);
				  vec2 = utils.rotateVector( vec2, 1);

				  vec1 = utils.doConvolveFFT( vec1, vec2 );
			  }
			  else if ((start+2) == focus)
			  {
				  vec1 = get2Gram( docterms, positions, localindexvectors, start );
				  //vec1 = ngrams2.get(start);
				  vec1 = utils.rotateVector( vec1, -1);

				  vec2 = get2Gram( docterms, positions, localindexvectors, start+3 );
				  //vec2 = ngrams2.get(start+3);
				  vec2 = utils.rotateVector( vec2, 1);

				  vec1 = utils.doConvolveFFT( vec1, vec2 );
			  }
			  else if ((start+3) == focus)
			  {
				  termId2 = positions[start+4];

				  vec1 = get3Gram( docterms, positions, localindexvectors, start );
				  //vec1 = ngrams3.get(start);
				  vec1 = utils.rotateVector( vec1, -1);

				  vec2 = utils.rotateVector(localindexvectors[termId2],1);

				  vec1 = utils.doConvolveFFT( vec1, vec2 );
			  }
			  else
			  {
				  vec1 = get4Gram( docterms, positions, localindexvectors, start );
				  //vec1 = ngrams4.get(start);
				  vec1 = utils.rotateVector(vec1, -1);
			  }

			  return vec1;
		  }

		  // If this is a 6-gram or higher
		  termId1 = positions[start];

		  if (start == focus) vec1 = phi;
		  else vec1 = localindexvectors[termId1];

		  for (int pos=start+1; pos<end; pos++ )
		  {
			  termId1 = positions[pos];
			  if (pos==focus) vec2 = phi;
			  else vec2 = localindexvectors[termId1];

			  // This needs to be changed to a rotation for consistency
			  vec1 = utils.scrambleVector( vec1, Permute1 );
			  vec2 = utils.scrambleVector( vec2, Permute2 );

			  // Do convolution
			  vec1 = utils.doConvolveFFT( vec1, vec2 );
		  }

		  return vec1;
	  }

	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
