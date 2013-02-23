/**
   Copyright 2009, SemanticVectors AUTHORS.
   All rights reserved.

   Redistribution and use in source and binary forms, with or without
   modification, are permitted provided that the following conditions are
   met:

 * Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.

 * Redistributions in binary form must reproduce the above
   copyright notice, this list of conditions and the following disclaimer
   in the documentation and/or other materials provided with the
   distribution.

 * Neither the name of Google Inc. nor the names of its
   contributors may be used to endorse or promote products derived from
   this software without specific prior written permission.

   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
   A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
   OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
   SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
   LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
   DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
   THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
   (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
   THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

   @author Yevgeniy Treyvus.
 **/

package pitt.search.semanticvectors.integrationtests;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import pitt.search.semanticvectors.BuildIndex;
import pitt.search.semanticvectors.ObjectVector;
import pitt.search.semanticvectors.Search;
import pitt.search.semanticvectors.SearchResult;

public class ThreadSafetyTest {
  private static final Logger logger = Logger.getLogger(ThreadSafetyTest.class.getCanonicalName());

  @Before
  public void setUp() {
    try {
      RunTests.prepareTestData();
    } catch (IOException e) {
      e.printStackTrace();
      fail();
    }
  }

  @Test
  public void TestSearchThreadSafety() throws Exception {
    // Build termvectors and docvectors
    String[] buildArgs = new String[] {"-dimension", "200", "-luceneindexpath", "positional_index"};
    
    File termFile = new File("termvectors.bin");
    assertFalse(termFile.isFile());
    File docFile = new File("docvectors.bin");
    assertFalse(docFile.isFile());
    BuildIndex.main(buildArgs);
    assertTrue(termFile.isFile());
    assertTrue(docFile.isFile());
    
    List<Thread> threads = new ArrayList<Thread>();
    final String queries[] = new String[]{"jesus", "mary", "peter", "light", "word"};
    final boolean[] done = new boolean[queries.length];
    for(final String query : queries) {
      Thread t = new Thread(new Runnable(){
        public void run() {
          try {
            outputSuggestions(query);
            done[Arrays.asList(queries).indexOf(query)] = true;
          } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
          }
        }}, "query: " + query);
      t.start();
      threads.add(t);
    }
    int counter = 0;
    while(counter < queries.length) {
      for(boolean b : done) {
        if(b) {
          counter++;
        }
      }
    }
    for(Thread t : threads) {
      t.join();
    }
    
    // Clean up files and force garbage collection of file handles.
    termFile.delete();
    docFile.delete();
    termFile = null;
    docFile = null;
    // This is a weird step: without forcing garbage collection, subsequent tests
    // fail on Windows filesystems, I think because the JVM thinks the files are
    // not released so other methods are not allowed to delete them.
    System.gc();
  }

  private static void outputSuggestions(String query) throws Exception  {
    int maxResults = 10;
    String[] args = new String[] {
        "-queryvectorfile", "termvectors.bin",
        "-luceneindexpath", RunTests.lucenePositionalIndexDir,
        query };
    List<SearchResult> results = Search.RunSearch(args, maxResults);

    if (results.size() > 0) {
      for (SearchResult result: results) {
        String suggestion = ((ObjectVector)result.getObjectVector()).getObject().toString();
        logger.finest("query:"+query + " suggestion:" + suggestion + " score:" + result.getScore());
      }
    }
  }
}
