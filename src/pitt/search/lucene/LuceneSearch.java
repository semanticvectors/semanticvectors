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

package pitt.search.lucene;

import java.io.*;
import org.apache.lucene.analysis.*;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.queryParser.*;

/**
 * Simple standalone class for searching a lucene index.
 * Initially built (quickly) for Pitt OTM demo, partly adapted from lucene demo code.
 * @author Dominic Widdows
 */
public class LuceneSearch {

  public static void usage(){
    System.out.println("Lucene Search Wrapper");
    System.out.println("Usage: java pitt.search.lucene.LuceneWrapper [-i path_to_index] " +
                       "[-f lucene_field] query terms ...");
    System.out.println("If no index given, defaults to directory './index'");
    System.out.println("The field argument may be used for searching bilingual indexes," +
                       "e.g., -f contents_en or -field contents_fr");
    System.exit(0);
  }

  public static void main( String[] args ){

    if (args.length == 0) {usage();}

    IndexSearcher searcher = null;
    String luceneIndex = "index";
    String luceneField = "contents";
    Hits hits = null;
    Query query = null;
    int argc = 0, startIndex=0, maxPage=10, thisPage=0;
    boolean error = false;

    /* parse command line args */
    if(args[argc].equals("-i")){
      luceneIndex = args[argc + 1];
      argc += 2;
    }
    if(args[argc].equals("-f")){
      luceneField = args[argc + 1];
      argc += 2;
    }
    String queryString = "";
    for( int i=argc; i<args.length; i++ ){
      queryString += args[i] + " ";
    }

    /* try to open index ... note that this takes longer than searching for
       large indexes, so a more scalable solution is to have the index open
       in a running webapp, rather than opening it each time in a standalone */
    try {
      searcher = new IndexSearcher(luceneIndex);
    } catch (Exception e) {
      System.err.println("Error parsing query ...");
      e.printStackTrace();
      error = true;
    }

    /* some of the rest of this code is instructed by Lucene's
     * results.jsp in the web demo */
    Analyzer analyzer = new StopAnalyzer();
    QueryParser queryParser = new QueryParser(luceneField, analyzer);
    try {
      query = queryParser.parse(queryString);
    } catch (ParseException e) {
      System.err.println("Error parsing query ...");
      e.printStackTrace();
      error = true;
    }

    if (error == false && searcher != null) {
      thisPage = maxPage;   // default last element to maxPage
      try{
        hits = searcher.search(query);    // run the query
      } catch (Exception e) {
        System.err.println("Error when searching ...");
        e.printStackTrace();
        error = true;
      }

      if (hits.length() == 0) {       // if we got no results tell the user
        System.out.println("<p> I'm sorry, there were no Lucene results. </p>");
        error = true;
      }
    }

    if ((startIndex + maxPage) > hits.length()) {
      thisPage = hits.length() - startIndex;   // set the max index to maxPage or last
    }                                            // actual search result whichever is less

    for (int i = startIndex; i < (thisPage + startIndex); i++) {
      try {
        float score = hits.score(i);
        Document doc = hits.doc(i);                 // get the next document
        String title = doc.get("title");            // get its title
        String url = doc.get("url");                // get its url field
        String filename = doc.get("path");
        // For bilingual docs, we index "filename" not "path",
        // since there are two system paths, one for each
        // language. So if there was no "path", get the "filename".
        if (filename == null) {
          filename = doc.get("filename");
        }
        System.out.println("Document:" + filename);
        System.out.println("Score:" + score);
        if (title != null) { System.out.println("Title:" + title); }
      }
      catch (Exception e) {
        System.err.println("Error while getting data from search results ...");
      }
    }
  }
}
