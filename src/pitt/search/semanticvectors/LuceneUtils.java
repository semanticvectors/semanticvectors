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
public class LuceneUtils {
  private static final Logger logger = Logger.getLogger(DocVectors.class.getCanonicalName());
  private FlagConfig flagConfig;
  private IndexReader indexReader;
  private Hashtable<Term, Float> termEntropy = new Hashtable<Term, Float>();
  private Hashtable<Term, Float> termIDF = new Hashtable<Term, Float>();
  private TreeSet<String> stopwords = null;
  private TreeSet<String> startwords = null;

  /**
   * Determines which term-weighting strategy to use in indexing, 
   * and in search if {@link FlagConfig#usetermweightsinsearch()} is set.
   * 
   * <p>Names may be passed as command-line arguments, so underscores are avoided.
   */
  public enum TermWeight {
    /** No term weighting: all terms have weight 1. */
    NONE,
    /** Use inverse document frequency: see {@link LuceneUtils#getIDF}. */
    IDF,
    /** Use log entropy: see {@link LuceneUtils#getEntropy}. */
    LOGENTROPY,
  }

  /**
   * @param flagConfig Contains all information necessary for configuring LuceneUtils.
   *        {@link FlagConfig#luceneindexpath()} must be non-empty. 
   */
  public LuceneUtils(FlagConfig flagConfig) throws IOException {
    if (flagConfig.luceneindexpath().isEmpty()) {
      throw new IllegalArgumentException(
          "-luceneindexpath is a required argument for initializing LuceneUtils instance.");
    }
    this.indexReader = IndexReader.open(FSDirectory.open(new File(flagConfig.luceneindexpath())));
    this.flagConfig = flagConfig;
    if (!flagConfig.stoplistfile().isEmpty())
      loadStopWords(flagConfig.stoplistfile());
  }


  /**
   * Loads the stopword file into the {@link #stopwords} data structure.
   * @param stoppath Path to stopword file.
   * @throws IOException If stopword file cannot be read.
   */
  public void loadStopWords(String stoppath) throws IOException  {
    logger.info("Using stopword file: "+stoppath);
    stopwords = new TreeSet<String>();
    try {
      BufferedReader readIn = new BufferedReader(new FileReader(stoppath));
      String in = readIn.readLine();
      while (in != null) {
        stopwords.add(in);
        in = readIn.readLine();
      }
    }
    catch (IOException e) {
      throw new IOException("Couldn't open file "+stoppath);
    }
  }

  /**
   * Loads the startword file into the {@link #startwords} data structure.
   * @param startpath Path to startword file
   * @throws IOException If startword file cannot be read.
   */
  public void loadStartWords(String startpath) throws IOException  {
    System.err.println("Using startword file: " + startpath);
    startwords = new TreeSet<String>();
    try {
      BufferedReader readIn = new BufferedReader(new FileReader(startpath));
      String in = readIn.readLine();
      while (in != null) {
        startwords.add(in);
        in = readIn.readLine();
      }	
    }
    catch (IOException e) {
      throw new IOException("Couldn't open file "+startpath);
    }
  }

  /**
   * Returns true if term is in stoplist, false otherwise.
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
   * Gets a term weight for a string, adding frequency over occurrences
   * in all contents fields.
   */
  public float getGlobalTermWeightFromString(String termString) {
    int freq = 0;
    for (String field: flagConfig.contentsfields())
      freq += getGlobalTermWeight(new Term(field, termString));
    return freq;
  }

  /**
   * Gets a global term weight for a term, depending on the setting for
   * {@link FlagConfig#termweight()}.
   * 
   * Used in indexing. Used in query weighting if
   * {@link FlagConfig#usetermweightsinsearch} is true.
   *
   * @param term whose frequency you want
   * @return Global term weight, or 1 if unavailable.
   */
  public float getGlobalTermWeight(Term term) {
    switch (flagConfig.termweight()) {
    case NONE:
      return 1;
    case IDF:
      return getIDF(term);
    case LOGENTROPY:
      return getEntropy(term);
    }
    VerbatimLogger.severe("Unrecognized termweight option: " + flagConfig.termweight()
        + ". Returning 1.");
    return 1;
  }

  /**
   * Returns the number of documents in the Lucene index.
   */
  public int getNumDocs() { return indexReader.numDocs(); }

  /**
   * Gets the IDF (i.e. log10(numdocs/doc frequency)) of a term
   *	@param term the term whose IDF you would like
   */
  private float getIDF(Term term) {
    if (termIDF.containsKey(term)) {
      return termIDF.get(term);
    } else { 
      try {
        float idf = (float) Math.log10(indexReader.numDocs()/indexReader.docFreq(term));
        termIDF.put(term, idf);
        return idf; 
      } catch (IOException e) {
        // Catches IOException from looking up doc frequency, never seen yet in practice.
        e.printStackTrace();
        return 1;
      }
    }
  }

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
  private float getEntropy(Term term){
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
   * Public version of {@link #termFilter} that gets all its inputs from the
   * {@link #flagConfig} and the provided term.
   * 
   * External callers should normally use this method, so that new filters are
   * available through different codepaths provided they pass a {@code FlagConfig}.
   * 
   * @param term Term to be filtered in or out, depending on Lucene index and flag configs.
   */
  public boolean termFilter(Term term) {
    return termFilter(term, flagConfig.contentsfields(),
        flagConfig.minfrequency(), flagConfig.maxfrequency(),
        flagConfig.maxnonalphabetchars(), flagConfig.filteroutnumbers());
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
   * Applies termFilter and additionally (if requested) filters out digit-only words. 
   * 
   * @param term Term to be filtered.
   * @param desiredFields Terms in only these fields are filtered in
   * @param minFreq minimum term frequency accepted
   * @param maxFreq maximum term frequency accepted
   * @param maxNonAlphabet reject terms with more than this number of non-alphabetic characters
   * @param filterNumbers if true, filters out tokens that represent a number
   */
  private boolean termFilter(
      Term term, String[] desiredFields, int minFreq, int maxFreq, int maxNonAlphabet, boolean filterNumbers) {
    // number filter
    if (filterNumbers) {
      try {
        // if the token can be parsed as a floating point number, no exception is thrown and false is returned
        // if not, an exception is thrown and we continue with the other termFilter method.
        // remark: this does not filter out e.g. Java or C++ formatted numbers like "1f" or "1.0d"
        Double.parseDouble( term.text() );
        return false;
      } catch (Exception e) {}
    }
    return termFilter(term, desiredFields, minFreq, maxFreq, maxNonAlphabet);
  }

  /**
   * Static method for compressing an index.
   *
   * This small preprocessing step makes sure that the Lucene index
   * is optimized to use contiguous integers as identifiers.
   * Otherwise exceptions can occur if document id's are greater
   * than indexReader.numDocs().
   */
  static void compressIndex(String indexDir) {
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
