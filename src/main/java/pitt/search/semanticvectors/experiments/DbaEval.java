package pitt.search.semanticvectors.experiments;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.PriorityQueue;
import java.util.Random;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;

import pitt.search.semanticvectors.CompoundVectorBuilder;
import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.LuceneUtils;
import pitt.search.semanticvectors.ObjectVector;
import pitt.search.semanticvectors.SearchResult;
import pitt.search.semanticvectors.VectorSearcher;
import pitt.search.semanticvectors.VectorSearcher.VectorSearcherPerm;
import pitt.search.semanticvectors.VectorStoreRAM;
import pitt.search.semanticvectors.vectors.PermutationUtils;
import pitt.search.semanticvectors.vectors.PermutationVector;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorType;
import pitt.search.semanticvectors.vectors.ZeroVectorException;

public class DbaEval {

	
	 
   
     
	
	public static void main(String[] args) throws IOException, IllegalArgumentException, ZeroVectorException
	{
		FlagConfig flagConfig = FlagConfig.getFlagConfig(args);
		VectorStoreRAM semanticVectors = new VectorStoreRAM(flagConfig);
		semanticVectors.initFromFile(flagConfig.semanticvectorfile());
		VectorStoreRAM elementalVectors = new VectorStoreRAM(flagConfig);
		elementalVectors.initFromFile(flagConfig.elementalvectorfile());
		
		String inputFile = flagConfig.remainingArgs[0];
		
		BufferedReader inputReader = new BufferedReader(new FileReader(new File(inputFile)));
		String inputString = inputReader.readLine();
		
		ArrayList<String> cues = new ArrayList<String>();
		ArrayList<String> targets = new ArrayList<String>();
		ArrayList<String> negativetargets = new ArrayList<String>();
		
		ArrayList <String> drugs = new ArrayList<String>();
		
		Random random = new Random();
		
		Vector cueVector = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());
		
		VectorType vtype1 = flagConfig.vectortype();
		int dimension1    = flagConfig.dimension();
		
		VectorStoreRAM embeddingVectors = new VectorStoreRAM(flagConfig);
		embeddingVectors.initFromFile(flagConfig.queryvectorfile());
		
		Vector cueVector2 = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());
		VectorType vtype2 = flagConfig.vectortype();
		int dimension2    = flagConfig.dimension();
		
		flagConfig.setVectortype(vtype1);
		flagConfig.setDimension(dimension1);
		
		//read in test cases
		while (inputString != null)
		{
			String[] input = inputString.split("\t");
			if (input.length < 2) 
				{inputString = inputReader.readLine();
				continue;
				}
			
			if (input[1].startsWith("_"))
				input[1] = input[1].substring(1);
			
			flagConfig.setVectortype(vtype1);
			flagConfig.setDimension(dimension1);
			Vector testv1 = CompoundVectorBuilder.getQueryVector(semanticVectors, null, flagConfig, (input[0]+" "+input[0].replaceAll("_"," ")).split(" "));
			Vector testv11= CompoundVectorBuilder.getQueryVector(elementalVectors, null, flagConfig, (input[1]+" "+input[1].replaceAll("_"," ")).split(" "));
			flagConfig.setVectortype(vtype2);
			flagConfig.setDimension(dimension2);
			Vector testv2 = CompoundVectorBuilder.getQueryVector(embeddingVectors, null, flagConfig, (input[0]+" "+input[0].replaceAll("_"," ")).split(" "));
			Vector testv22= CompoundVectorBuilder.getQueryVector(embeddingVectors, null, flagConfig, (input[1]+" "+input[1].replaceAll("_"," ")).split(" "));
			boolean test1 = (!testv1.isZeroVector() && !testv11.isZeroVector());
			boolean test2 = (!testv2.isZeroVector() && !testv22.isZeroVector());
					
			if (test1 && test2 && random.nextDouble() > .9)
			{
			if (random.nextDouble() > .99)
			{
				cues.add(inputString);
			}
			
			else
				targets.add(inputString);
			}
			if (test1 && test2 && !drugs.contains(input[0]))
			drugs.add(input[0]);
			
			inputString = inputReader.readLine();
		}
		inputReader.close();
		

		
		//add negative test cases
		for (String posExample:targets)
		{
			String[] input = posExample.split("\t");
			String randomDrug = "";
			
			while (randomDrug.isEmpty() || negativetargets.contains(randomDrug+"\t"+input[1]) || targets.contains(randomDrug+"\t"+input[1]) || cues.contains(randomDrug+"\t"+input[1]))
			{
				String next = drugs.get(random.nextInt(drugs.size()));
				randomDrug = next;
			}
			
			negativetargets.add(randomDrug+"\t"+input[1]);
		}
		
		flagConfig.setVectortype(vtype1);
		flagConfig.setDimension(dimension1);
	
		for (String cue:cues)
		{
			String[] input = cue.split("\t");
			Vector sVec = 
					CompoundVectorBuilder.getQueryVector(semanticVectors, null, flagConfig, (input[0]+" "+input[0].replaceAll("_"," ")).split(" "));
			Vector eVec = 
					CompoundVectorBuilder.getQueryVector(elementalVectors, null, flagConfig, (input[1]+" "+input[1].replaceAll("_"," ")).split(" "));

			sVec.bind(eVec);
			cueVector.superpose(sVec,1,null);
		}
		
		cueVector.normalize();
		
		int[] labels = new int[targets.size()+negativetargets.size()];
		double[] scores = new double[targets.size()+negativetargets.size()];
		
		int cnt = 0;
		
		for (String target:targets)
		{
			String[] input = target.split("\t");
			Vector sVec = 
					CompoundVectorBuilder.getQueryVector(semanticVectors, null, flagConfig, (input[0]+" "+input[0].replaceAll("_"," ")).split(" "));
			Vector eVec = 
					CompoundVectorBuilder.getQueryVector(elementalVectors, null, flagConfig, (input[1]+" "+input[1].replaceAll("_"," ")).split(" "));
			sVec.bind(cueVector);
			double score = sVec.measureOverlap(eVec);
			scores[cnt] = score;
			labels[cnt++]=1;
			//System.out.println("1\t"+score);
			
		}
		
		for (String ntarget:negativetargets)
		{
			String[] input = ntarget.split("\t");
			Vector sVec = 
					CompoundVectorBuilder.getQueryVector(semanticVectors, null, flagConfig, (input[0]+" "+input[0].replaceAll("_"," ")).split(" "));
			Vector eVec = 
					CompoundVectorBuilder.getQueryVector(elementalVectors, null, flagConfig, (input[1]+" "+input[1].replaceAll("_"," ")).split(" "));
			sVec.bind(cueVector);
			
			double score = sVec.measureOverlap(eVec);
			scores[cnt] = score;
			labels[cnt++]=0;
			
			//System.out.println("0\t"+score);
			
			
		}
		
		flagConfig.setVectortype(vtype2);
		flagConfig.setDimension(dimension2);
		
		
		for (String cue:cues)
		{
			String[] input = cue.split("\t");
			Vector sVec = 
					CompoundVectorBuilder.getQueryVector(embeddingVectors, null, flagConfig, (input[0]+" "+input[0].replaceAll("_"," ")).split(" "));
			Vector eVec = 
					CompoundVectorBuilder.getQueryVector(embeddingVectors, null, flagConfig, (input[1]+" "+input[1].replaceAll("_"," ")).split(" "));

			eVec.superpose(sVec,-1,null);
			cueVector2.superpose(eVec,1,null);
		}
		
		cueVector2.normalize();
		int[] labels2 = new int[targets.size()+negativetargets.size()];
		double[] scores2 = new double[targets.size()+negativetargets.size()];
		
		int cnt2 = 0;
		ArrayList<String> test = new ArrayList<String>();
		for (String target:targets)
		{
			String[] input = target.split("\t");
			test.add(target);
			Vector sVec = 
					CompoundVectorBuilder.getQueryVector(embeddingVectors, null, flagConfig, (input[0]+" "+input[0].replaceAll("_"," ")).split(" "));
			Vector eVec = 
					CompoundVectorBuilder.getQueryVector(embeddingVectors, null, flagConfig, (input[1]+" "+input[1].replaceAll("_"," ")).split(" "));
			sVec.superpose(cueVector2,1,null);
			double score = sVec.measureOverlap(eVec);
			scores2[cnt2] = score;
			labels2[cnt2++]=1;
			//System.out.println("sv1\t"+score);
			
		}
		for (String ntarget:negativetargets)
		{
			test.add(ntarget);
			String[] input = ntarget.split("\t");
			Vector sVec = 
					CompoundVectorBuilder.getQueryVector(embeddingVectors, null, flagConfig, (input[0]+" "+input[0].replaceAll("_"," ")).split(" "));
			Vector eVec = 
					CompoundVectorBuilder.getQueryVector(embeddingVectors, null, flagConfig, (input[1]+" "+input[1].replaceAll("_"," ")).split(" "));
			sVec.superpose(cueVector2,1,null);
			
			double score = sVec.measureOverlap(eVec);
			scores2[cnt2] = score;
			labels2[cnt2++]=0;
			
			//System.out.println("sv0\t"+score);
			
			
		}
		
		
		for (int q=0; q < labels.length; q++)
		{
			
			System.out.println(labels[q]+" "+scores[q]+" "+scores2[q]+" "+test.get(q));
		}
	}
	
}
