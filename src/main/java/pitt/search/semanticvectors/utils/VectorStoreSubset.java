package pitt.search.semanticvectors.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;

import pitt.search.semanticvectors.CompoundVectorBuilder;
import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.VectorStoreRAM;
import pitt.search.semanticvectors.VectorStoreWriter;
import pitt.search.semanticvectors.vectors.ComplexVector;
import pitt.search.semanticvectors.vectors.RealVector;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorType;


public class VectorStoreSubset {

	/**
	 * Main class of a utility method that takes as input:
	 * (1) -queryvectorfile: the original vector store OR
	 * (1.1) -elementalvectofile, -semanticvectorfile, -predicatevectorfile (for PSI queries)
	 * (2) -startlistfile: the list of queries (e.g. terms), one per line, from which to construct a new vector store 
	 * @param args
	 * @throws IOException 
	 */
	
	
	public static void main(String[] args) throws IOException {
		
		FlagConfig flagConfig = FlagConfig.getFlagConfig(args);
		boolean PSIquery = true;
		
		VectorStoreRAM queryVectors = new VectorStoreRAM(flagConfig);
		VectorStoreRAM elementalVectors = new VectorStoreRAM(flagConfig);
		VectorStoreRAM predicateVectors = new VectorStoreRAM(flagConfig);
		VectorStoreRAM semanticVectors = new VectorStoreRAM(flagConfig);
	
		
		//if all PSI/ESP query files are at the default value, assume this is not a PSI/ESP query
		if (flagConfig.elementalvectorfile().equals("elementalvectors")
				&& flagConfig.semanticvectorfile().equals("semanticvectors")
				  && flagConfig.elementalpredicatevectorfile().equals("predicatevectors")
				) 
			{
				PSIquery = false;
				queryVectors.initFromFile(flagConfig.queryvectorfile());
			}
		else
		{
			elementalVectors.initFromFile(flagConfig.elementalvectorfile());
			semanticVectors.initFromFile(flagConfig.semanticvectorfile());
			predicateVectors.initFromFile(flagConfig.elementalpredicatevectorfile());
		}
		
		VectorStoreRAM outGoingVectors = new VectorStoreRAM(flagConfig);
		
		
		BufferedReader theReader = new BufferedReader(new FileReader(new File(flagConfig.startlistfile())));
		
		String inputString = theReader.readLine();
		while (inputString != null)
		{
			Vector vectorToAdd = null;
			
			if (!PSIquery) vectorToAdd = CompoundVectorBuilder.getQueryVectorFromString(queryVectors, null, flagConfig, inputString);
			else 		   vectorToAdd = CompoundVectorBuilder.getBoundProductQueryVectorFromString(flagConfig, elementalVectors, semanticVectors, predicateVectors, null, inputString);
			
			if (vectorToAdd == null || 
					vectorToAdd.isZeroVector() || 
						(flagConfig.vectortype().equals(VectorType.REAL) && (Float.isNaN(  ((RealVector) vectorToAdd).getCoordinates()[0]))) ||
						(flagConfig.vectortype().equals(VectorType.COMPLEX) && (Float.isNaN(  ((ComplexVector) vectorToAdd).getCoordinates()[0])))
							
					)
			{	VerbatimLogger.info("Could not represent "+inputString);}
			else
			{
				VerbatimLogger.info("Adding "+inputString.replaceAll(" ", "_"));
				outGoingVectors.putVector(inputString.replaceAll(" ", "_"),vectorToAdd);
			}  
			
			inputString = theReader.readLine();
		}
		
		theReader.close();
		
		VerbatimLogger.info("\nFinished adding vectors, proceeding to write out\n");
		
		VectorStoreWriter.writeVectorsInLuceneFormat(flagConfig.startlistfile().replaceAll("\\..*", "_subset.bin"), flagConfig, outGoingVectors);
		

	}

}
