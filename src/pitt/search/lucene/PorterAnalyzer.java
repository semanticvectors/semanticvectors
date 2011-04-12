package pitt.search.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.*;
import java.io.*;

public class PorterAnalyzer  extends Analyzer {
		      
	public final TokenStream tokenStream(String fieldName, Reader reader) {
		        return new PorterStemFilter(new LowerCaseTokenizer(reader));
		      }
		    }

