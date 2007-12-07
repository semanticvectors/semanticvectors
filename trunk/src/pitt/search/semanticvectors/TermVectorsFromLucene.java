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

import java.util.Hashtable;
import java.util.Random;
import java.util.Enumeration;
import java.io.IOException;
import org.apache.lucene.index.*;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

/** 
 * Implementation of vector store that creates term vectors by
 * iterating through all the terms in a Lucene index.
 * @param indexDir directory containing Lucene index.
 * @param seedLength number of +1 or -1 entries in basic vectors.
 * @param minFreq the minimum term frequecny for a term to be indexed.
 */
public class TermVectorsFromLucene implements VectorStore {

    private Hashtable<String, ObjectVector> termVectors;
    private IndexReader indexReader;
    private int seedLength;
    private Random random;
    private int minFreq;

    public IndexReader getIndexReader(){ return this.indexReader; }
    
    public TermVectorsFromLucene( String indexDir, int seedLength, int minFreq ) throws IOException {
	this.seedLength = seedLength;
	this.minFreq = minFreq;

	IndexModifier modifier = new IndexModifier(indexDir, new StandardAnalyzer(), false);
	modifier.optimize();
	modifier.close();
	indexReader = IndexReader.open(indexDir);

	int i;
	termVectors = new Hashtable();
	random = new Random();

	/* create basic doc vectors */	
	float[][] basicDocVectors = new float[indexReader.numDocs()][ObjectVector.vecLength];
	System.err.println("Populating basic doc vector table ...");
	for( i=0; i<indexReader.numDocs(); i++ ){
	    basicDocVectors[i] = generateRandomVector();
	}	

	/* iterate through an enumeration of terms and create termVector table*/
	System.err.println("Creating term vectors ...");
	TermEnum terms = indexReader.terms();
	int tc = 0;
	while(terms.next()){
	    tc++;
	}
	System.err.println("There are " + tc + " terms (and " + indexReader.numDocs() + " docs)");
	
	tc = 0;
	terms = indexReader.terms();
	while( terms.next() ){
	    /* output progress counter */
	    if( ( tc % 10000 == 0 ) || ( tc < 10000 && tc % 1000 == 0 ) ){
		System.err.print(tc + " ... ");
	    }
	    tc++;

	    Term term = terms.term();
	    /* skip terms that don't pass the filter */
	    if( !termFilter(terms.term()) ){
		continue;
	    }

	    float[] termVector = new float[ObjectVector.vecLength];
	    for(i=0; i<ObjectVector.vecLength; i++){termVector[i]=0;}
	    TermDocs tDocs = indexReader.termDocs(term);
	    while( tDocs.next() ){
		int doc = tDocs.doc();
		int freq = tDocs.freq();
		for( i=0; i<ObjectVector.vecLength; i++ ){
		    termVector[i] += freq*basicDocVectors[doc][i];
		}
	    }
	    termVector = VectorUtils.getNormalizedVector(termVector);
	    termVectors.put(term.text(), new ObjectVector(term.text(), termVector));
	}
	System.err.println("\nCreated " + termVectors.size() + " term vectors ...");
    }

    public float[] getVector(Object term){
	return termVectors.get(term).getVector();
    }

    public Enumeration getAllVectors(){
	return termVectors.elements();
    }

    /**
     * filters out non-alphabetic terms and those of low frequency
     * it might be a good idea to factor this out as a separate component
     */
    private boolean termFilter ( Term term ) throws IOException {
	/* character filter */
	String termText = term.text();
	for( int i=0; i<termText.length(); i++ ){
	    if( !Character.isLetter(termText.charAt(i)) ){
		return false;
	    }
	}

	/* freqency filter */
	int freq = 0;
	TermDocs tDocs = indexReader.termDocs(term);
	while( tDocs.next() ){
	    freq += tDocs.freq();
	}
	if( freq < minFreq ){
	    return false;
	}

	return true;
    }

    /**
     * generates a basic sparse vector (dimension = ObjectVector.vecLength) 
     * with mainly zeros and some 1 and -1 entries (seedLength of each)
     */ 
    private float[] generateRandomVector(){
	float[] randVector = new float[ObjectVector.vecLength];
	for(int i=0; i<ObjectVector.vecLength; i++){
	    randVector[i]=0;
	}

	int testPlace, entryCount = 0;

	/* put in +1 entries */
	while(entryCount < seedLength ){
	    testPlace = random.nextInt(ObjectVector.vecLength);
	    if( randVector[testPlace] == 0 ){
		randVector[testPlace] = 1;
		entryCount++;
	    }
	}

	/* put in -1 entries */
	entryCount = 0;
	while(entryCount < seedLength ){
	    testPlace = random.nextInt(ObjectVector.vecLength);
	    if( randVector[testPlace] == 0 ){
		randVector[testPlace] = -1;
		entryCount++;
	    }
	}

	return randVector;
    }
} 