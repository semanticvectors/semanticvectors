/**
	 Copyright (c) 2009, Queensland University of Technology

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

package qut.beagle;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;

/**
 * A custom term filter which is a combination of TermFreqFilter, TermCharacterFilter
 * and TermStopListFilter. Only terms which pass all filters are kept.
 * 
 * @author Lance De Vine
 */
public class CustomTermFilter implements TermFilter {

	TermFreqFilter tff;
	TermCharacterFilter tcf;
	TermStopListFilter tslf;
	
	IndexReader indexReader;
	int minFreq = 0;
	
	public CustomTermFilter( IndexReader indexReader, int minFreq, String stoplist )
	{
		this.indexReader = indexReader;
		this.minFreq = minFreq;
		
		this.tff = new TermFreqFilter( indexReader, minFreq );
		this.tcf = new TermCharacterFilter();
		this.tslf = new TermStopListFilter( stoplist );
	}
	
	public boolean filter(Term t) 
	{
		if (!tcf.filter(t)) return false;
		if (!tff.filter(t)) return false;
		if (!tslf.filter(t)) return false;
		
		return true;
	}

}
