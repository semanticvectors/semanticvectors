/**
   Copyright 2008, Google Inc.
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
**/

package pitt.search.lucene;


import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.util.Version;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Date;

/**
 * Index pairs of bilingual texts in a parallel corpus.  See <a
 * href="http://code.google.com/p/semanticvectors/wiki/BilingualModels">
 * http://code.google.com/p/semanticvectors/wiki/BilingualModels</a>
 * for more thorough documentation of preparation of corpora and
 * creation of models.
 */
public class IndexBilingualFiles {
  File INDEX_DIR;
  String LANGUAGE1;
  String LANGUAGE2;

  public IndexBilingualFiles(String lang1, String lang2) {
    LANGUAGE1 = lang1;
    LANGUAGE2 = lang2;
  }

	private void runIndexer() {
    INDEX_DIR = new File("bilingual_index");
    if (INDEX_DIR.exists()) {
      if (INDEX_DIR.exists()) {
        throw new IllegalArgumentException(
            "Cannot save index to '" + INDEX_DIR.getAbsolutePath() + "' directory, please delete it first");
      }
    }

    Date start = new Date();
    try {
			final File docDir1 = new File(LANGUAGE1);
			final File docDir2 = new File(LANGUAGE2);
      IndexWriter writer = new IndexWriter(FSDirectory.open(INDEX_DIR),
                                           new StandardAnalyzer(Version.LUCENE_30),
                                           true, MaxFieldLength.UNLIMITED);
      System.out.println("Indexing to directory '" + INDEX_DIR + "'...");
			runDeepIndexer(docDir1, docDir2, writer);

      System.out.println("Optimizing...");
      writer.optimize();
      writer.close();

      Date end = new Date();
      System.out.println(end.getTime() - start.getTime() + " total milliseconds");

    } catch (IOException e) {
      System.out.println(" caught a " + e.getClass() +
                         "\n with message: " + e.getMessage());
    }
  }

  private void runDeepIndexer(File docDir1, File docDir2, IndexWriter writer) {
    // Run several tests to see if corpus is well formed.
    if (!docDir1.exists()) {
      System.out.println("Test directory exists failed: " + docDir1);
      System.exit(1);
      if (!docDir1.canRead()) {
        System.out.println("Test readable failed: " + docDir1);
        System.exit(1);
        if (!docDir1.isDirectory()) {
          System.out.println("Test is directory failed: " + docDir1);
          System.exit(1);
        }
      }
    }

    if (!docDir2.exists()) {
      System.out.println("Test directory exists failed: " + docDir2);
      System.exit(1);
      if (!docDir2.canRead()) {
        System.out.println("Test readable failed: " + docDir2);
        System.exit(1);
        if (!docDir2.isDirectory()) {
          System.out.println("Test is directory failed: " + docDir2);
          System.exit(1);
        }
      }
    }

    System.err.println("Trying to index files in directories:\n" +
                       docDir1.getAbsolutePath() + "\n" + docDir2.getAbsolutePath());

    String[] files1 = docDir1.list();
    String[] files2 = docDir2.list();
    if (!checkStringArraysEqual(files1, files2)) {
      System.err.println("Contents of directories don't match up; " +
                         "not creating bilingual index.\n" +
                         "Please check corpora contents, clean up your data, " +
                         "and try again.");
      //System.exit(1);
    }

		for (int i = 0; i < files1.length; ++i) {
			System.out.println("adding " + files1[i]);
			File newFile1 = new File(docDir1 + "/" + files1[i]);
			File newFile2 = new File(docDir2 + "/" + files1[i]);
			if (newFile1.isDirectory() && newFile2.isDirectory()) {
				runDeepIndexer(newFile1, newFile2, writer);
			}

			try {
				writer.addDocument(fileBilingualDocument(newFile1, newFile2));
			}
			catch (IOException e) {
				System.err.println("Got exception with filepair: " + files1[i]);
				e.printStackTrace();
			}
		}
	}

  // A method for making Lucene Documents from a bilingual file pair.
  protected Document fileBilingualDocument(File file1, File file2)
      throws java.io.IOException {
    /** Makes a document for a File.
        <p>
        The document has three fields:
        <ul>
        <li><code>filename</code>--name of the file, as a stored,
        untokenized field; to get the full path for each pair,
        add the language specific prefix.
        <li><code>contents_LANGUAGE1</code>--containing the full contents
        of the file in LANGUAGE1, as a Reader field; e.g., contents_en.
        <li><code>contents_LANGUAGE2</code>--containing the full contents
        of the file in LANGUAGE2, as a Reader field; e.g., contents_fr.
    */

    // make a new, empty document
    Document doc = new Document();

    // Add the path of the file as a field named "filename".  Use a field that is
    // indexed (i.e. searchable), but don't tokenize the field into words.
    doc.add(new Field("filename",
                      file1.getPath(),
                      Field.Store.YES,
                      Field.Index.NOT_ANALYZED));

    // Add the contents of the file to a fields named
    // "contents_LANGUAGE1" and "contents_LANGUAGE2".  Specify a
    // Reader, so that the text of the file is tokenized and
    // indexed, but not stored.  Note that FileReader expects the
    // file to be in the system's default encoding.  If that's not
    // the case searching for special characters will fail.
    doc.add(new Field("contents_" + LANGUAGE1, new FileReader(file1)));
    doc.add(new Field("contents_" + LANGUAGE2, new FileReader(file2)));

    // return the document
    return doc;
  }

  // Utility for checking if two lists of filenames are the same.
  static boolean checkStringArraysEqual(String[] array1, String[] array2) {
    if (array1.length != array2.length) {
      System.err.println("checkStringArraysEqual: arrays are of different lengths!");
      return false;
    }
    if (array1.length == 0) {
      System.err.println("checkStringArraysEqual: arrays are empty!");
      return false;
    }
    for (int i = 0; i < array1.length; ++i) {
      if (!array1[i].equals(array2[i])) {
        System.err.println("checkStringArraysEqual: following pairs differ: "
                           + array1[i] + " " + array2[i]);
        return false;
      }
    }
    return true;
  }

  // Main function.
  public static void main(String[] args) {
    String usage = "java pitt.search.preporcessing.IndexBilingualFiles <directory1> <directory2>";
    if (args.length != 2) {
      System.err.println("Usage: " + usage);
      System.exit(1);
    }
    IndexBilingualFiles indexer = new IndexBilingualFiles(args[0], args[1]);
    indexer.runIndexer();
  }
}
