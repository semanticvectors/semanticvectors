package pitt.search.lucene;
import java.io.File;
import java.io.FileReader;

import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;

/**
 *  This class makes a minor modification to org.apache.lucene.FileDocument
 *  such that it records TermPositionVectors for each document
 *  @author Trevor Cohen
 */
public class FilePositionDoc  {

  public static Document Document(File f)
       throws java.io.FileNotFoundException {
    Document doc = new Document();
    doc.add(new StoredField("path", f.getPath()));
    doc.add(new StoredField("modified",
                      DateTools.timeToString(f.lastModified(), DateTools.Resolution.MINUTE)));
    doc.add(new TextField("contents", new FileReader(f)));
    return doc;
  }
}
