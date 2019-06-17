package pitt.search.semanticvectors.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;

import org.apache.lucene.index.Term;

import pitt.search.semanticvectors.CloseableVectorStore;
import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.LuceneUtils;
import pitt.search.semanticvectors.ObjectVector;
import pitt.search.semanticvectors.SearchResult;
import pitt.search.semanticvectors.VectorStoreRAM;
import pitt.search.semanticvectors.VectorStoreReader;
import pitt.search.semanticvectors.VectorStoreWriter;

public class GetFrequentConcepts {

	public static void main(String[] args) throws IOException {
		// TODO Auto-generated method stub
		FlagConfig flagConfig = FlagConfig.getFlagConfig(args);
		
		LuceneUtils lUtils = new LuceneUtils(flagConfig);
		CloseableVectorStore incomingVectors = 
				VectorStoreReader.openVectorStore(flagConfig.queryvectorfile(), flagConfig);
		
		Enumeration<ObjectVector> allVecs = incomingVectors.getAllVectors();
		
		ArrayList<SearchResult> toSort = new ArrayList<SearchResult>(); 
		
				while (allVecs.hasMoreElements())
				{
					ObjectVector ov = allVecs.nextElement();
					int count = 0;
					for (String field:flagConfig.contentsfields())
					{
						count += lUtils.getGlobalTermFreq(new Term(field,ov.getObject().toString()));
					}
					
					if (count > flagConfig.minfrequency())
						toSort.add(new SearchResult(count,ov));
				}
		
				Collections.sort(toSort);
				
				int k=0;
				
				VectorStoreRAM reducedVectors = new VectorStoreRAM(flagConfig);
				
				for (SearchResult sr:toSort)
				{
					k++;
					System.out.println(k+"\t"+sr.getScore()+"\t"+((ObjectVector) sr.getObjectVector()).getObject());
					reducedVectors.putVector(sr.getObjectVector().getObject(), sr.getObjectVector().getVector());
					if (k >= flagConfig.numsearchresults()) break;
				}
				
				incomingVectors.close();
				
				VectorStoreWriter.writeVectors("reduced_"+flagConfig.queryvectorfile(), flagConfig, reducedVectors);
				
	}

}
