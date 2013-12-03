/**
   Copyright 2008 and ongoing, the SemanticVectors AUTHORS.
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
 **/package pitt.search.lucene;

import static pitt.search.semanticvectors.LuceneUtils.LUCENE_VERSION;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import pitt.search.semanticvectors.FlagConfig;

/** Index all text files under a directory. This class makes minor
 * modifications to <code>org.apache.lucene.demos.IndexFiles</code>
 * using a new document handler.
 * @see FilePositionDoc
 */
public class IndexFilePositions {

  private IndexFilePositions() {}

  static File INDEX_DIR = new File("positional_index");

  /** Index all text files under a directory. */
  public static void main(String[] args) {
    FlagConfig flagConfig = null;
    String usage = "java pitt.search.lucene.IndexFilePositions <root_directory> ";
    if (args.length == 0) {
      System.err.println("Usage: " + usage);
      System.exit(1);
    }
    
    flagConfig = FlagConfig.getFlagConfig(args);
    // Allow for the specification of a directory to write the index to.
    if (flagConfig.luceneindexpath().length() > 0) {
      INDEX_DIR = new File(flagConfig.luceneindexpath());
    }
    if (INDEX_DIR.exists()) {
      throw new IllegalArgumentException(
          "Cannot save index to '" + INDEX_DIR.getAbsolutePath() + "' directory, please delete it first");
    }
    try {
    	IndexWriter writer;
      // Create IndexWriter using porter stemmer or no stemming. No stopword list.
    	Analyzer analyzer = flagConfig.porterstemmer() ? new PorterAnalyzer() : new StandardAnalyzer(LUCENE_VERSION, new CharArraySet(LUCENE_VERSION, 0, false));
      IndexWriterConfig writerConfig = new IndexWriterConfig(LUCENE_VERSION, analyzer);
      writer = new IndexWriter(FSDirectory.open(INDEX_DIR), writerConfig);

    	final File docDir = new File(flagConfig.remainingArgs[0]);
      if (!docDir.exists() || !docDir.canRead()) {
        writer.close();
        throw new IOException ("Document directory '" + docDir.getAbsolutePath() +
            "' does not exist or is not readable, please check the path");
      }

      Date start = new Date();

      System.out.println("Indexing to directory '" +INDEX_DIR+ "'...");
      indexDocs(writer, docDir);
      writer.close();

      Date end = new Date();
      System.out.println(end.getTime() - start.getTime() + " total milliseconds");

    } catch (IOException e) {
      System.out.println(" caught a " + e.getClass() +
                         "\n with message: " + e.getMessage());
    }
  }

  static void indexDocs(IndexWriter writer, File file)
      throws IOException {
    // Do not try to index files that cannot be read.
    if (file.canRead()) {
      if (file.isDirectory()) {
        String[] files = file.list();
        // An IO error could occur.
        if (files != null) {
          for (int i = 0; i < files.length; i++) {
            // Skip dot files.
            if (!files[i].startsWith(".")) {
              indexDocs(writer, new File(file, files[i]));
            }
          }
        }
      } else {
        System.out.println("adding " + file);
        try {
          // Use FilePositionDoc rather than FileDoc such that term
          // positions are indexed also.
          writer.addDocument(FilePositionDoc.Document(file));
        }
        // At least on windows, some temporary files raise this
        // exception with an "access denied" message. Checking if the
        // file can be read doesn't help
        catch (FileNotFoundException fnfe) {
          fnfe.printStackTrace();
        }
      }
    }
  }
}
