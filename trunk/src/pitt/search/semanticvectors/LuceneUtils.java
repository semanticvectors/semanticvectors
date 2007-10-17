/**
Copyright (c) 2007, University of Pittsburgh

All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.

    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.

    * Neither the name of the University of Pittsburgh nor the names
      of its contributors may be used to endorse or promote products
      derived from this software without specific prior written
      permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
**/

package pitt.search.semanticvectors;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;
import java.io.IOException;
import java.lang.Math;

/**
 * Class to support reading extra information from Lucene indexes,
 * including term frequency, doc frequency.
 */

public class LuceneUtils{
    private IndexReader indexReader;

    /**
     * @param path - path to lucene index
     */
    public LuceneUtils( String path ) throws IOException {
	this.indexReader = IndexReader.open(path);
    }

    /**
     * Gets the global term frequency of a term, 
     * i.e. how may times it occurs in the whole corpus
     * @param term whose frequency you want
     */
    public int getGlobalTermFreq(Term term){
	int tf = 0;
	try{
	    TermDocs tDocs = this.indexReader.termDocs(term);
	    tf = tDocs.freq();
	    while( tDocs.next() ){
		tf += tDocs.freq();
	    }
	}
	catch( IOException e ){
	    System.err.println("Couldn't get term frequency for term " + term.text());
	}
	return tf;
    }

    /**
     * Gets the global term weight for a term, used in query weighting 
     * Currently returns log(tf)/log(df) - you can experiment
     * @param term whose frequency you want
     */
    public float getGlobalTermWeight(Term term){
	try{
	    int globalTF = getGlobalTermFreq(term);
	    return (float) Math.log(globalTF) / (indexReader.docFreq(term));
	}
	catch( IOException e ){
	    System.err.println("Couldn't get term weight for term " + term.text());	    
	    return 1;
	}
    }
}	