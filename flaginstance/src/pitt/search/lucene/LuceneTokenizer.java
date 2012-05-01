package pitt.search.lucene;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;

import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.util.Version;

/**
 * 
 * @author sjonnalagadda
 *
 */
public class LuceneTokenizer {

	
	
	
	public static void main(String[] args) throws CorruptIndexException, LockObtainFailedException, IOException{
		
		//String testString = "peri-kappa B site";
		System.out.println(tokenize("100(hosp pack)"));
		
	}
	
	/**
	 * 
	 * @param string
	 * @return arrayList of tokens of string converted to lowercase
	 * @throws IOException
	 */
	public static ArrayList<String> tokenize(String string) throws IOException{
		ArrayList<String> retList = new ArrayList<String>();
		StringReader reader = new StringReader(string);
		StandardTokenizer tokenizer = new StandardTokenizer(Version.LUCENE_30, reader);
		while(tokenizer.incrementToken()){
			retList.add(tokenizer.getAttribute(TermAttribute.class).term());
			//System.out.println(tokenizer.getAttribute(TermAttribute.class).term());
		}
		reader.close();
		return retList;
	}
}
	