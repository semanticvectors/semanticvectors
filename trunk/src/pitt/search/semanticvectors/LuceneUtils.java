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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import java.lang.Math;
import java.util.Hashtable;
import java.util.TreeSet;
import java.util.logging.Logger;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;


/**
 * Class to support reading extra information from Lucene indexes,
 * including term frequency, doc frequency.
 */
public class LuceneUtils{
  private static final Logger logger = Logger.getLogger(DocVectors.class.getCanonicalName());
  
  private IndexReader indexReader;
  private Hashtable<Term, Float> termEntropy = new Hashtable<Term, Float>();
  private TreeSet<String> stopwords = null;

  /**
   * @param path - path to lucene index
   */
  public LuceneUtils (String path) throws IOException {
    this.indexReader = IndexReader.open(FSDirectory.open(new File(path)));
    if (Flags.stoplistfile.length() > 0)
    	loadStopWords(Flags.stoplistfile);
  }


  /**
   * Loads the stopword file into memory
   * @param stoppath - path to stopword file
   * @throws IOException
   */

  public void loadStopWords(String stoppath) throws IOException
  {  logger.info("Using stopword file: "+stoppath);
	  stopwords = new TreeSet<String>();
  	try{
  BufferedReader readIn = new BufferedReader(new FileReader(stoppath));
  String in = readIn.readLine();
  while (in != null)
  {stopwords.add(in);
  in = readIn.readLine();
  }
  }
  catch (IOException e)
  {throw new IOException("Couldn't open file "+stoppath);}
  }

  /**
   * Returns true if term is in stoplist (returns false if no stoplist)
   */
  public boolean stoplistContains(String x) {
    if (stopwords == null) return false;
    return stopwords.contains(x);
  }

  /**
   * Gets the global term frequency of a term,
   * i.e. how may times it occurs in the whole corpus
   * @param term whose frequency you want
   * @return Global term frequency of term, or 1 if unavailable.
   */
  public int getGlobalTermFreq(Term term){
    int tf = 0;
    try{
      TermDocs tDocs = this.indexReader.termDocs(term);
      if (tDocs == null) {
        logger.info("Couldn't get term frequency for term " + term.text());
        return 1;
      }
      while (tDocs.next()) {
        tf += tDocs.freq();
      }
    }
    catch (IOException e) {
      logger.info("Couldn't get term frequency for term " + term.text());
      return 1;
    }
    return tf;
  }

  /**
   * Gets a term weight for a string, adding frequency over occurences
   * in all contents fields.
   * Currently returns some power of inverse document frequency - you can experiment.
   */
  public float getGlobalTermWeightFromString(String termString) {
    try {
      int freq = 0;
      for (String field: Flags.contentsfields)
	freq += indexReader.docFreq(new Term(field, termString));
      return (float) Math.pow(freq, -0.05);
    } catch (IOException e) {
      logger.info("Couldn't get term weight for term '" + termString + "'");
      return 1;
    }
  }

  /**
   * Gets the global term weight for a term, used in query weighting.
   * Currently returns some power of inverse document frequency - you can experiment.
   * @param term whose frequency you want
   * @return Global term weight, or 1 if unavailable.
   */
  public float getGlobalTermWeight(Term term) {
    try {
      return (float) Math.pow(indexReader.docFreq(term), -0.05);
    } catch (IOException e) {
      logger.info("Couldn't get term weight for term '" + term.text() + "'");
      return 1;
    }
  }

  /**
   * Gets the number of documents
   */
  public int getNumDocs()
  {return indexReader.numDocs();}

  /**
   * Gets the 1 - entropy (i.e. 1+ plogp) of a term,
   * a function that favors terms that are focally distributed
   * We use the definition of log-entropy weighting provided in
   * Martin and Berry (2007):
   * Entropy = 1 + sum ((Pij log2(Pij)) /  log2(n))
   * where Pij = frequency of term i in doc j / global frequency of term i
   * 		 n	 = number of documents in collection
   * @param term whose entropy you want
   * Thanks to Vidya Vasuki for adding the hash table to
   * eliminate redundant calculation
   */
  public float getEntropy(Term term){
    if(termEntropy.containsKey(term))
      return termEntropy.get(term);
    int gf = getGlobalTermFreq(term);
    double entropy = 0;
    try {
      TermDocs tDocs = indexReader.termDocs(term);
      while (tDocs.next())
      {
        double p = tDocs.freq(); //frequency in this document
        p=p/gf;		//frequency across all documents
        entropy += (p*(Math.log(p)/Math.log(2))); //sum of Plog(P)
      }
      int n= this.getNumDocs();
      double log2n = Math.log(n)/Math.log(2);
      entropy = entropy/log2n;
    }
    catch (IOException e) {
      logger.info("Couldn't get term entropy for term " + term.text());
    }
    termEntropy.put(term, 1+(float)entropy);
    return (float) (1 + entropy);
  }

  /**
   * Filters out non-alphabetic terms and those of low frequency.
   * 
   * Thanks to Vidya Vasuki for refactoring and bug repair
   * 
   * @param term Term to be filtered.
   * @param desiredFields Terms in only these fields are filtered in
   * @param minFreq minimum term frequency accepted
   * @param maxFreq maximum term frequency accepted
   * @param maxNonAlphabet reject terms with more than this number of non-alphabetic characters
   */
  protected boolean termFilter(
      Term term, String[] desiredFields, int minFreq, int maxFreq, int maxNonAlphabet) {
    // Field filter.
    boolean isDesiredField = false;
    for (int i = 0; i < desiredFields.length; ++i) {
      if (term.field().compareToIgnoreCase(desiredFields[i]) == 0) {
        isDesiredField = true;
      }
    }

    // Stoplist (if active)
    if (stoplistContains(term.text()))
    	return false;

    if (!isDesiredField) {
      return false;
    }

    // Character filter.
    if (maxNonAlphabet != -1) {
      int nonLetter = 0;
      String termText = term.text();
      for (int i = 0; i < termText.length(); ++i) {
        if (!Character.isLetter(termText.charAt(i)))
          nonLetter++;
        if (nonLetter > maxNonAlphabet)
          return false;
      }
    }

    // Frequency filter.
    int termfreq = getGlobalTermFreq(term);
    if (termfreq < minFreq | termfreq > maxFreq)  {
      return false;
    }

    // If we've passed each filter, return true.
    return true;
  }

  /**
   * Static method for compressing an index.
   *
   * This small preprocessing step makes sure that the Lucene index
   * is optimized to use contiguous integers as identifiers.
   * Otherwise exceptions can occur if document id's are greater
   * than indexReader.numDocs().
   */
  static void CompressIndex(String indexDir) {
    try {
      IndexWriter compressor = new IndexWriter(FSDirectory.open(new File(indexDir)),
					       new StandardAnalyzer(Version.LUCENE_30),
					       false,
					       MaxFieldLength.UNLIMITED);
      compressor.optimize();
      compressor.close();
    } catch (CorruptIndexException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
