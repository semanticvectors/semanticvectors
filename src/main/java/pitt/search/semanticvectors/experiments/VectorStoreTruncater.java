package pitt.search.semanticvectors.experiments;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.util.Enumeration;

import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;

import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.ObjectVector;
import pitt.search.semanticvectors.VectorStoreRAM;
import pitt.search.semanticvectors.VectorStoreWriter;
import pitt.search.semanticvectors.utils.VerbatimLogger;

public class VectorStoreTruncater {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

		try {
		FlagConfig flagConfig 			= FlagConfig.getFlagConfig(args);
		VectorStoreRAM objectVectors 	= new VectorStoreRAM(flagConfig);
		String[] argsRemaining			= flagConfig.remainingArgs;
		String incomingVecs				= argsRemaining[0];
		int newDimension				= Integer.parseInt(argsRemaining[1]);
		objectVectors.initFromFile(incomingVecs);
		
		if (newDimension > flagConfig.dimension())
			{
			
					System.out.println("Incoming file has dimensionality of " +flagConfig.dimension());
					System.out.println("New dimensionality must be less than incoming vector length, quitting");
					System.exit(0);	
			}
		
			String vectorFileName = incomingVecs.replaceAll("\\.bin", "")+"_"+newDimension+".bin";
		  	File vectorFile = new File(vectorFileName);
		    String parentPath = vectorFile.getParent();
		    if (parentPath == null) parentPath = "";
		    FSDirectory fsDirectory = FSDirectory.open(FileSystems.getDefault().getPath(parentPath));
		    IndexOutput outputStream = fsDirectory.createOutput(vectorFile.getName(), IOContext.DEFAULT);
		 	flagConfig.setDimension(newDimension);
			outputStream.writeString(VectorStoreWriter.generateHeaderString(flagConfig));
		    Enumeration<ObjectVector> vecEnum = objectVectors.getAllVectors();

		    // Write each vector.
		    while (vecEnum.hasMoreElements()) {
		      ObjectVector objectVector = vecEnum.nextElement();
		      outputStream.writeString(objectVector.getObject().toString());
		      objectVector.getVector().writeToLuceneStream(outputStream,flagConfig.dimension());
		    }
		    
		    
		    outputStream.close();
		    fsDirectory.close();
			
		    VerbatimLogger.info("wrote "+objectVectors.getNumVectors()+" vectors to file "+ vectorFileName);
		    VerbatimLogger.info("finished writing vectors.\n");
		 		
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.out.println("Usage: VectorStoreTruncater incomingFile.bin newDimensinoality");
		}
		
		
		
	}

}
