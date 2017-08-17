package pitt.search.semanticvectors.experiments;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Set;

import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.ObjectVector;
import pitt.search.semanticvectors.VectorStoreRAM;
import pitt.search.semanticvectors.VectorStoreWriter;
import pitt.search.semanticvectors.vectors.RealVector;
import pitt.search.semanticvectors.vectors.VectorFactory;

public class Retrofit {
	
	/**
	 * Java implementation of vector retrofitting technique described in:
	 * 
	 * @InProceedings{faruqui:2014:NIPS-DLRLW,
	 *	 author    = {Faruqui, Manaal and Dodge, Jesse and Jauhar, Sujay K.  and  Dyer, Chris and Hovy, Eduard and Smith, Noah A.},
	 * title     = {Retrofitting Word Vectors to Semantic Lexicons},
	 * booktitle = {Proceedings of NAACL},
	 * year      = {2015},
	 *}
	 * @param args
	 * @throws IOException 
	 */

	public static void main(String[] args) throws IOException {
		
		double alpha=1; double beta=1; //standard parameters
		
		FlagConfig flagConfig 			 = FlagConfig.getFlagConfig(args);
		VectorStoreRAM initialVectors  	 = new VectorStoreRAM(flagConfig);
		VectorStoreRAM retroVectors		 = new VectorStoreRAM(flagConfig);
		
		//read in initial term vectors, initialize retrofitted vectors to initial vectors
		initialVectors.initFromFile(flagConfig.initialtermvectors());
		retroVectors.initFromFile(flagConfig.initialtermvectors());
		
		
		//read in lexicon, a space-separated file consisting of all concepts related to the first concept to appear
		BufferedReader lexiconReader = new BufferedReader(new FileReader(new File(flagConfig.startlistfile())));
		
		Hashtable<String,String[]> relations = new Hashtable<String,String[]>();
		
		String lexiconLine = lexiconReader.readLine();
		
		while (lexiconLine != null)
		{
			String[] tokenizedLine = lexiconLine.split(" "); 
			relations.put(tokenizedLine[0].toLowerCase(),tokenizedLine);
			lexiconLine = lexiconReader.readLine();
			}
		
		lexiconReader.close();
		
		//facilitate shuffling of concept list on each iteration of training
		ArrayList<String> keyList = new ArrayList<String>();
		Enumeration<String> keySet = relations.keys();
		while (keySet.hasMoreElements())
			keyList.add(keySet.nextElement());
		
		//iterate
		for (int epoch=0; epoch < flagConfig.trainingcycles(); epoch++)
		{
			System.out.println("Training cycle "+epoch);
			
			Collections.shuffle(keyList);
			
				for (String key:keyList)
				{
					
					String[] relationships = relations.get(key);
					
					//skip if no relationships for this CUI
					if (!retroVectors.containsVector(key)) continue;
					if (relationships.length <= 1) continue;
					
						
					retroVectors.removeVector(key);
					retroVectors.putVector(key, VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension()));
					
					int relcnt = 0;
					
					for (int x=1; x < relationships.length; x++)
						if (retroVectors.containsVector(relationships[x].toLowerCase()))
						{
							retroVectors.getVector(key).superpose(retroVectors.getVector(relationships[x].toLowerCase()), beta, null);
							
							relcnt++;
						}
					
					if (relcnt > 0)
					{
						//rescale
						for (int y=0; y < flagConfig.dimension(); y++)
						((RealVector) retroVectors.getVector(key)).getCoordinates()[y] /= (double) 2*alpha*beta*relcnt;
				
					}
				}
				
	
				
		}
		
		VectorStoreWriter.writeVectorsInLuceneFormat(flagConfig.initialtermvectors().substring(0,flagConfig.initialtermvectors().indexOf("."))+flagConfig.startlistfile().substring(flagConfig.startlistfile().lastIndexOf("/")+1,flagConfig.startlistfile().indexOf("."))+".bin", flagConfig, retroVectors);
		
	}

}
