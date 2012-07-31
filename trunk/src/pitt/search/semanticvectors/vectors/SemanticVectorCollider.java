package pitt.search.semanticvectors.vectors;

import java.util.ArrayList;
import java.util.Random;

import pitt.search.semanticvectors.Flags;
import pitt.search.semanticvectors.vectors.ComplexVector.Mode;

public class SemanticVectorCollider {

	public static void main(String[] args)
	{
		Flags.parseCommandLineFlags(args);
		
		ComplexVector.setDominantMode(Mode.CARTESIAN);
		
		Random random = new Random();
		
		
		int iterations = 1000; //number of times to perform experiment
		int superpositions = 15000; //number of superpositions per experiment (at most)
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		
		System.out.println("Number of iterations "+iterations);
		System.out.println("Number of superpositions per iteration (if no collision occurs) "+superpositions);
		System.out.println("Vector type "+Flags.vectortype);
		System.out.println("Dimension "+Flags.dimension);
		System.out.println("Seed length "+Flags.seedlength);
		
		int overlapcnt = 0;
		int overlaprank = 0;
		ArrayList<Double> overlapRank = new ArrayList<Double>();
		
		int overlapcount = 0;
		double overlapscore=0;
		ArrayList<Double> overlapScore = new ArrayList<Double>();
		
		
		for (int cnt = 0; cnt < iterations; cnt++)
		{	
			System.err.println("\nIteration "+cnt);
			
		
		Vector originalVector = VectorFactory.generateRandomVector(VectorType.valueOf(Flags.vectortype.toUpperCase()),Flags.dimension, Flags.seedlength, random);
		
		Vector superPosition = VectorFactory.createZeroVector(VectorType.valueOf(Flags.vectortype.toUpperCase()), Flags.dimension);
		
		superPosition.superpose(originalVector, 1, null);
		if (Flags.vectortype.equalsIgnoreCase("binary"))
		{ superPosition.normalize(); 	}
		
		
		Vector additionalVector = VectorFactory.generateRandomVector(VectorType.valueOf(Flags.vectortype.toUpperCase()),Flags.dimension, Flags.seedlength, random);
		
		for (int x =0; x < superpositions; x++)
		{
			if (x % 100 == 0) System.err.print("...");
			
			
			double overlapWithOrigin = superPosition.measureOverlap(originalVector); 
		
			//generate another random vector
			Vector randomVector = VectorFactory.generateRandomVector(VectorType.valueOf(Flags.vectortype.toUpperCase()),Flags.dimension, Flags.seedlength, random);
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
			
			additionalVector = VectorFactory.generateRandomVector(VectorType.valueOf(Flags.vectortype.toUpperCase()),Flags.dimension, Flags.seedlength, random);
			
			superPosition.superpose(additionalVector, 1, null);
			
			if (Flags.vectortype.equalsIgnoreCase("binary"))
			{ superPosition.normalize(); 	}
			
		
			
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
