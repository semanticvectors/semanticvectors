/**
   Copyright (c) 2013, the SemanticVectors AUTHORS.

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

package pitt.search.semanticvectors.vectors;

import java.util.ArrayList;
import java.util.Random;

import pitt.search.semanticvectors.FlagConfig;

public class SemanticVectorCollider {

	public static void main(String[] args)
	{
		FlagConfig flagConfig = FlagConfig.getFlagConfig(args);
	  args = flagConfig.remainingArgs;
				
		Random random = new Random();
		
		
		int iterations = 1000; //number of times to perform experiment
		int superpositions = 15000; //number of superpositions per experiment (at most)
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		
		System.out.println("Number of iterations "+iterations);
		System.out.println("Number of superpositions per iteration (if no collision occurs) "+superpositions);
		System.out.println("Vector type "+flagConfig.vectortype());
		System.out.println("Dimension "+flagConfig.dimension());
		System.out.println("Seed length "+flagConfig.seedlength());
		
		int overlapcnt = 0;
		int overlaprank = 0;
		ArrayList<Double> overlapRank = new ArrayList<Double>();
		
		int overlapcount = 0;
		double overlapscore=0;
		ArrayList<Double> overlapScore = new ArrayList<Double>();
		
		
		for (int cnt = 0; cnt < iterations; cnt++)
		{	
			System.err.println("\nIteration "+cnt);
			
		
		Vector originalVector = VectorFactory.generateRandomVector(
		    flagConfig.vectortype(), flagConfig.dimension(), flagConfig.seedlength(), random);
		
		Vector superPosition = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());
		
		superPosition.superpose(originalVector, 1, null);
		if (flagConfig.vectortype() == VectorType.BINARY) {
		  ((BinaryVector) superPosition).tallyVotes();
		}
		
		Vector additionalVector = VectorFactory.generateRandomVector(
        flagConfig.vectortype(), flagConfig.dimension(), flagConfig.seedlength(), random);
		
		for (int x =0; x < superpositions; x++)
		{
			if (x % 100 == 0) System.err.print("...");
			
			
			double overlapWithOrigin = superPosition.measureOverlap(originalVector); 
		
			//generate another random vector
			Vector randomVector = VectorFactory.generateRandomVector(
	        flagConfig.vectortype(), flagConfig.dimension(), flagConfig.seedlength(), random);
			double overlapWithRandom = superPosition.measureOverlap(randomVector); 
			
			
				overlapscore += overlapWithRandom;
				overlapScore.add(new Double(overlapWithRandom));
				
			
			if (overlapWithRandom >= overlapWithOrigin) //version 2.0 based on Roger Schvaneveldt's Matlab edition: compare superposition:origin vs. superposition:random (this is different than the implementation in Wahle et al 2012, which compared origin:superposition vs. origin:random) 
			{
				System.out.println("Iteration " +cnt+": Incidental overlap occurred at superposition number "+x);
				
				min = Math.min(min,x);
				max = Math.max(max,x);
				
				overlapcnt++;
				overlaprank += x;
				overlapRank.add(new Double(x));
				
					
				x = 999999999;
			}
			
			additionalVector = VectorFactory.generateRandomVector(
			    flagConfig.vectortype(), flagConfig.dimension(), flagConfig.seedlength(), random);
			
			superPosition.superpose(additionalVector, 1, null);
			
			if (flagConfig.vectortype() == VectorType.BINARY) {
			  ((BinaryVector) superPosition).tallyVotes();
			}
		}
		
		}
		
		double stdRank = calculateSTD(overlapRank, (double) overlaprank/ (double) overlapcnt);
		
		System.out.println("Collisions occurred in "+(100)*((double) overlapcnt/(double) iterations) +"% of iterations");
		System.out.println("\nAverage collision rank "+ (double) overlaprank/ (double) overlapcnt);
		System.out.println("STD collision rank "+ stdRank);
		System.out.println("Minimum collision rank "+min);
		System.out.println("Maximum collision rank "+max);
		
		
	}
	
		public static double calculateSTD(ArrayList<Double> values, double mean)
		{
			double std = 0;
			
			for (int x=0; x < values.size(); x++)
			{
				std += Math.pow(values.get(x).doubleValue() - mean,2);
				
				
			}
			
			std = std/(double) values.size()-1;
			std = Math.sqrt(std);
			return std;
		}
	
	
}
