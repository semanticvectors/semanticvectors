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

import pitt.search.semanticvectors.Flags;

import cern.colt.matrix.tfcomplex.impl.DenseFComplexMatrix1D;
import cern.colt.matrix.tfloat.impl.DenseFloatMatrix1D;
import cern.jet.math.tfcomplex.FComplex;
import cern.jet.math.tfloat.FloatMult;
import cern.jet.random.tdouble.Normal;
import cern.jet.random.tdouble.engine.DoubleMersenneTwister;
import cern.jet.random.tfloat.engine.FRand;
import cern.jet.random.tfloat.engine.FloatRandomEngine;

public class BeagleUtils
{
	private static BeagleUtils instance = null;
	private Normal normal;
	private float mu = 0.0f;
	private float sigma = 0.0f;

	private int convCount = 0;

	/* For performance reasons a FFT cache of a user defined size is used to store
	 * computed transforms for re-use. This gives a small performance improvement.
	 */
	ObjectCache fftCache;

	protected BeagleUtils()
	{
		fftCache = new ObjectCache(0);
		normal = new Normal( mu, sigma, new DoubleMersenneTwister( new java.util.Date() ) );
	}

	public static BeagleUtils getInstance()
	{
      if(instance == null) {
         instance = new BeagleUtils();
      }
      return instance;
	}

	public void initialise()
	{
		fftCache.clear();
	}

	public void setFFTCacheSize( int size )
	{
		fftCache.resize(size);
	}

	public void setNormal( float mu, float sigma )
	{
		this.mu = mu;
		this.sigma = sigma;

		normal.setState( mu, sigma );
	}

	public int getNumConvolutions()
	{
		return convCount;
	}

	public void printVector(float[] vector)
	{
		System.out.println("\n");
		for (int i = 0; i < vector.length - 1; i++) {
			System.out.print(vector[i] + "|");
		}
		// Print last coordinate followed by newline, not "|".
		System.out.println(vector[vector.length - 1]);
	}

	public void ensureSize( ArrayList<DenseFloatMatrix1D> list, int size )
	{
		int lSize = list.size();
		if (size <= lSize) return;

		for (int i=lSize; i<size; i++) list.add( null );
	}

	public DenseFloatMatrix1D createZeroVector( int size )
	{
		DenseFloatMatrix1D A = new DenseFloatMatrix1D(size);
		for (int i=0; i<A.size(); i++)
		{
			A.setQuick(i, 0.0f );
		}

		return A;
	}

	public void fillVectorRandom( DenseFloatMatrix1D vec, float mu, float sigma )
	{
		this.mu = mu; this.sigma = sigma;
		normal.setState( mu, sigma );
		for (int i=0; i<vec.size(); i++)
		{
			vec.setQuick(i, (float)normal.nextDouble() );
		}
	}

	public DenseFloatMatrix1D generateColtRandomVector()
	{
		DenseFloatMatrix1D vec = new DenseFloatMatrix1D( Flags.dimensions);

		this.mu = mu; this.sigma = sigma;
		normal.setState( mu, sigma );
		for (int i=0; i<vec.size(); i++)
		{
			vec.setQuick(i, (float)normal.nextDouble() );
		}

		return vec;
	}

	public float[] generateNormalizedRandomVector()
	{
		float[] vec = new float[Flags.dimensions];

		for (int i=0; i<vec.length; i++)
		{
			vec[i] = (float)normal.nextDouble();
		}

		normalize(vec);

		return vec;
	}

	public boolean normalize( float[] vec )
	{
		float norm = 0.0f;
		for (int i = 0; i < vec.length; ++i)
		{
		    norm += vec[i]*vec[i];
		}

		norm = (float)Math.sqrt(norm);

		if (norm==0.0f)
		{
			//System.out.println("\n##########\nERROR - zero norm\n#########");
			return false;
		}

		for (int i = 0; i < vec.length; ++i)
		{
			vec[i] = vec[i]/norm;
		}

		return true;
	}

	public DenseFloatMatrix1D normalize( DenseFloatMatrix1D vec )
	{
	  float sum = vec.zDotProduct( vec, 0, (int)vec.size() );
		float length = (float)Math.sqrt(sum);

		//if (length==0.0f) System.out.println("\n##########\nERROR - zero norm\n#########");

		if (length != 0.0f) vec.assign( FloatMult.mult(1.0f/length) );
		return vec;
	}

	public void addAssignVectors( DenseFloatMatrix1D vec1, DenseFloatMatrix1D vec2, float scale )
	{
		if (scale == 1.0f)
		{
			for (int i=0; i<vec1.size(); i++)
			{
				vec1.setQuick( i, vec1.getQuick(i) + vec2.getQuick(i) );
			}
		}
		else if (scale == -1.0f)
		{
			for (int i=0; i<vec1.size(); i++)
			{
				vec1.setQuick( i, vec1.getQuick(i) - vec2.getQuick(i) );
			}
		}
		else
		{
			for (int i=0; i<vec1.size(); i++)
			{
				vec1.setQuick( i, vec1.getQuick(i) + vec2.getQuick(i) * scale );
			}
		}
	}

	public DenseFloatMatrix1D getRandomNormalizedVector( int dim, float mu, float sigma )
	{
		this.mu = mu; this.sigma = sigma;
		DenseFloatMatrix1D A = new DenseFloatMatrix1D(dim);
		fillVectorRandom( A, mu, sigma );
		normalize(A);

		return A;
	}

	public DenseFloatMatrix1D doConvolve( DenseFloatMatrix1D vec1, DenseFloatMatrix1D vec2, int dim )
	{
		DenseFloatMatrix1D vec3 = new DenseFloatMatrix1D(dim);
		int idx1, idx2;

		for (int i=0; i<dim; i++ )
		{
			for (int j=0; j<dim; j++ )
			{
				idx1 = j%dim;
				idx2 = (i-j)%dim;

				if (idx2 < 0) idx2 = idx2 + dim;

				vec3.setQuick( i, vec3.getQuick(i) + vec1.getQuick(idx1) * vec2.getQuick(idx2) );
			}
		}

		return vec3;
	}

	public DenseFComplexMatrix1D getFFT( String str, DenseFloatMatrix1D vec )
	{
		DenseFComplexMatrix1D fft;

		if (fftCache.containsKey(str))
		{
			fft = (DenseFComplexMatrix1D)fftCache.getObject(str);
		}
		else
		{
			fft = vec.getFft();
			fftCache.addObject(str, fft);
		}

		return fft;
	}

	public DenseFloatMatrix1D doConvolveFFT( DenseFloatMatrix1D vec1, DenseFloatMatrix1D vec2,
			String term1, String term2 )
	{

		// Process:
		// a) get fft 1
		// b) get fft 2
		// c) complex element-wise multiply
		// d) inverse fft

	  int dim = (int) vec1.size();
		DenseFloatMatrix1D result;
		DenseFComplexMatrix1D C;
		DenseFComplexMatrix1D D;

		// Check if we are able to use the fft cache
		if (term1!=null)
		{
			C = getFFT( term1, vec1 );

		}
		else C = vec1.getFft();

		if (term2!=null)
		{
			D = getFFT( term2, vec2 );
		}
		else D = vec2.getFft();

		//**************************************
		// Use this when not using fft cache
		//C = vec1.getFft();
		//D = vec2.getFft();
		//**************************************

		// Do convolution in the fourier domain
		// First: Element-wise complex multiplication
		for (int i=0; i<dim; i++ )
		{
			D.setQuick( i, FComplex.mult( C.getQuick(i), D.getQuick(i) ) );
		}

		// Second:
		// Inverse transform
		D.ifft(true);

		// Count number of convolutions
		convCount++;

		result = ((DenseFloatMatrix1D)(D.getRealPart()));
		normalize(result);

		return result;
	}

	public DenseFloatMatrix1D doConvolveFFT( DenseFloatMatrix1D vec1, DenseFloatMatrix1D vec2 )
	{
		// get fft 1
		// get fft 2
		// complex element-wise multiply
		// inverse fft

	  int dim = (int) vec1.size();
		DenseFloatMatrix1D result;
		DenseFComplexMatrix1D C = vec1.getFft();
		DenseFComplexMatrix1D D = vec2.getFft();

		for (int i=0; i<dim; i++ )
		{
			C.setQuick( i, FComplex.mult( C.getQuick(i), D.getQuick(i) ) );
		}

		C.ifft(true);

		convCount++;

		result = ((DenseFloatMatrix1D)(C.getRealPart()));
		normalize(result);

		return result;
	}

	public int[] makeScrambledIntArray( int size )
	{
		int[] array = new int[size];

		for (int i=0; i<size; i++)
		{
			array[i] = i;
		}

		FloatRandomEngine rEng = new FRand(new java.util.Date());

		int idx1, idx2, tmp;

		for (int i=0; i<size; i++)
		{
			// Do some swapping
			idx1 = (int)(rEng.nextFloat()*size);
			idx2 = (int)(rEng.nextFloat()*size);

			tmp = array[idx1];
			array[idx1] = array[idx2];
			array[idx2] = tmp;
		}

		return array;
	}

	public DenseFloatMatrix1D scrambleVector( DenseFloatMatrix1D vec, int[] permVector )
	{
	  DenseFloatMatrix1D scramVec = new DenseFloatMatrix1D((int)vec.size());

		if ((int) vec.size() != permVector.length)
		{
			System.out.println("scrambleVector: Size of vectors do not match");
			return scramVec;
		}

		for (int i=0; i<vec.size(); i++ )
		{
			scramVec.setQuick( permVector[i], vec.getQuick(i) );
		}

		return scramVec;
	}

	public DenseFloatMatrix1D rotateVector( DenseFloatMatrix1D vec, int places )
	{
	  DenseFloatMatrix1D rotVec = new DenseFloatMatrix1D((int)vec.size());
		int idx;

		for (int i=0; i<vec.size(); i++ )
		{
			idx = i+places;
			if (idx >= (int)vec.size()) idx = idx - (int)vec.size();
			if (idx < 0) idx = idx + (int)vec.size();

			rotVec.setQuick( idx, vec.getQuick(i) );
		}
		return rotVec;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {


	}

}
