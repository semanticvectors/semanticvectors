package pitt.search.semanticvectors.experiments;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileSystems;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.index.CompositeReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SlowCompositeReaderWrapper;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.xml.builders.BooleanQueryBuilder;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;

import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.LuceneUtils;

public class GetContingencyTables {

	/**
	 * Get contingency tables from Lucene index
	 * Input: file with comma-delimited pairs of terms (first two used)
	 * Output: terms , a (intersection), b (t1 ! t2), c (t2 !t1), d (!t1 !t2)
	 * 
	 * Assumes terms are stored in the "contents" fields (although this could be configured differently)
	 * 
	 * @param args
	 * @throws IOException 
	 */
	
	public static void main(String[] args) throws IOException {
		// TODO Auto-generated method stub
		FlagConfig flagConfig = FlagConfig.getFlagConfig(args);
		
		String inputfile = flagConfig.startlistfile();
		String luceneindex = flagConfig.luceneindexpath();
		
		if (inputfile.isEmpty() || luceneindex.isEmpty())
		{
			System.err.println("Required arguments");
			System.err.println("-luceneindexpath: path to Lucene index");
			System.err.println("-startlistfile: path to file containing comma-delimited term pairs");
			System.exit(0);
		}
		
		CompositeReader compositeReader = DirectoryReader.open(
		        FSDirectory.open(FileSystems.getDefault().getPath(flagConfig.luceneindexpath())));
		@SuppressWarnings("deprecation")
		LeafReader leafReader = SlowCompositeReaderWrapper.wrap(compositeReader);
		IndexSearcher searcher = new IndexSearcher(leafReader);
		
		LuceneUtils lUtils = new LuceneUtils(flagConfig);
		
		BufferedReader theReader = new BufferedReader(new FileReader(new File(inputfile)));
		
		String incoming = theReader.readLine();
		
		while (incoming != null)
		{
			String[] incomingArray = incoming.split(",");
			
			if (incomingArray.length < 2)
			{
				System.err.println("Two comma-delimited terms per line required");
				System.err.println("Skipping "+incoming);
			}
			else
			{
				Term t1 = new Term("contents",incomingArray[0]);
				Term t2 = new Term("contents",incomingArray[1]);
				
				   long startTime = System.currentTimeMillis();
				   
				   //create the term query object
				   Query query1 = new TermQuery(t1);
				   Query query2 = new TermQuery(t2);
				   
				   BooleanQuery booleanQuery = new BooleanQuery.Builder()
						    .add(query1, BooleanClause.Occur.MUST)
						    .add(query2, BooleanClause.Occur.MUST)
						    .build();
				   
				   TopDocs hitsA = searcher.search(booleanQuery,Integer.MAX_VALUE);
				   int cellA = hitsA.totalHits;
				  	
				   BooleanQuery booleanQueryB = new BooleanQuery.Builder()
						    .add(query1, BooleanClause.Occur.MUST)
						    .add(query2, BooleanClause.Occur.MUST_NOT)
						    .build();
				   
				   TopDocs hitsB = searcher.search(booleanQueryB,Integer.MAX_VALUE);
				   int cellB = hitsB.totalHits;
				   
				   BooleanQuery booleanQueryC = new BooleanQuery.Builder()
						    .add(query1, BooleanClause.Occur.MUST_NOT)
						    .add(query2, BooleanClause.Occur.MUST)
						    .build();
				   
				   TopDocs hitsC = searcher.search(booleanQueryC,Integer.MAX_VALUE);
				   int cellC = hitsC.totalHits;
				   
				   int cellD = lUtils.getNumDocs() - cellA - cellB - cellC;
				   
				   System.out.println(incoming+","+cellA+","+cellB+","+cellC+","+cellD);
			}
			
			
			incoming = theReader.readLine();
		}
		
		theReader.close();
		
	}

}
