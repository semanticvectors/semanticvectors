package pitt.search.semanticvectors.vectors;

import java.util.ArrayList;
import java.util.Random;

import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.vectors.ComplexVector.Mode;

public class SemanticVectorCollider {

	public static void main(String[] args)
	{
		FlagConfig flagConfig = FlagConfig.getFlagConfig(args);
	  args = flagConfig.remainingArgs;
		
		ComplexVector.setDominantMode(Mode.CARTESIAN);
		
		Random random = new Random();
		
		
		int iterations = 1000; //number of times to perform experiment
		int superpositions = 15000; //number of superpositions per experiment (at most)
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		
		System.out.println("Number of iterations "+iterations);
		System.out.println("Number of superpositions per iteration (if no collision occurs) "+superpositions);
		System.out.println("Vector type "+flagConfig.getVectortype());
		System.out.println("Dimension "+flagConfig.getDimension());
		System.out.println("Seed length "+flagConfig.getSeedlength());
		
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
		    flagConfig.getVectortype(), flagConfig.getDimension(), flagConfig.getSeedlength(), random);
		
		Vector superPosition = VectorFactory.createZeroVector(flagConfig.getVectortype(), flagConfig.getDimension());
		
		superPosition.superpose(originalVector, 1, null);
		if (flagConfig.getVectortype() == VectorType.BINARY) {
		  superPosition.normalize();
		}
		
		Vector additionalVector = VectorFactory.generateRandomVector(
        flagConfig.getVectortype(), flagConfig.getDimension(), flagConfig.getSeedlength(), random);
		
		for (int x =0; x < superpositions; x++)
		{
			if (x % 100 == 0) System.err.print("...");
			
			
			double overlapWithOrigin = superPosition.measureOverlap(originalVector); 
		
			//generate another random vector
			Vector randomVector = VectorFactory.generateRandomVector(
	        flagConfig.getVectortype(), flagConfig.getDimension(), flagConfig.getSeedlength(), random);
			double overlapWithRandom = superPosition.measureOverlap(randomVector); 
			
			
				overlapscore += overlapWithRandom;
				overlapScore.add(new Double(overlapWithRandom));
				
			
			if (overlapWithRandom >= overlapWithOrigin) //version 2.0 based on Roger Schvaneveldt's Matlab edition: compare superposition:origin vs. superposition:random 
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
			    flagConfig.getVectortype(), flagConfig.getDimension(), flagConfig.getSeedlength(), random);
			
			superPosition.superpose(additionalVector, 1, null);
			
			if (flagConfig.getVectortype() == VectorType.BINARY) {
			  superPosition.normalize();
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
