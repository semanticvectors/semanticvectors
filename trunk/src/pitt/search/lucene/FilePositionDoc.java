package pitt.search.lucene;
import java.io.File;
import java.io.FileReader;

import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;

/**
 *  This class makes a minor modification to org.apache.lucene.FileDocument
 *  such that it records TermPositionVectors for each document
 *  @author Trevor Cohen
 */
public class FilePositionDoc  {

  public static Document Document(File f)
       throws java.io.FileNotFoundException {
		Document doc = new Document();
    doc.add(new Field("path", f.getPath(), Field.Store.YES, Field.Index.NOT_ANALYZED));
    doc.add(new Field("modified",
                      DateTools.timeToString(f.lastModified(), DateTools.Resolution.MINUTE),
                      Field.Store.YES, Field.Index.NOT_ANALYZED ));
    doc.add(new Field("contents", new FileReader(f), Field.TermVector.WITH_POSITIONS));
    return doc;
  }
}
