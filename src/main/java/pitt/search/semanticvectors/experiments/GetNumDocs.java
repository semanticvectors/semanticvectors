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

public class GetNumDocs {

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
			Term t1 = new Term("contents",incoming);
			long startTime = System.currentTimeMillis();
			Query query1 = new TermQuery(t1);
			
			int numdocs = lUtils.getGlobalDocFreq(t1);
			
				   System.out.println(incoming+","+numdocs);
		
			
			
			incoming = theReader.readLine();
		}
		
		theReader.close();
		
	}

}
