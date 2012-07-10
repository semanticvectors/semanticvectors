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
		
		
		int iterations = 100; //number of times to perform experiment
		int superpositions = 15000; //number of superpositions per experiment
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
		
		Vector additionalVector = VectorFactory.generateRandomVector(VectorType.valueOf(Flags.vectortype.toUpperCase()),Flags.dimension, Flags.seedlength, random);
		
		for (int x =0; x < superpositions; x++)
		{
			if (x % 100 == 0) System.err.print("...");
			
				if (Flags.vectortype.equalsIgnoreCase("binary"))
				{ superPosition.normalize(); 	}
			
			double overlapWithOrigin = superPosition.measureOverlap(originalVector); 
			double overlapWithAddition = originalVector.measureOverlap(additionalVector);
			
				overlapcount++;
				overlapscore += overlapWithAddition;
				overlapScore.add(new Double(overlapWithAddition));
				
			
			if (overlapWithAddition >= overlapWithOrigin)
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
