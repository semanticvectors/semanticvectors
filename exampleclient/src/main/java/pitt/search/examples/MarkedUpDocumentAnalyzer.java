package pitt.search.examples;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.VectorStoreDeterministic;
import pitt.search.semanticvectors.orthography.ProportionVectors;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;

import java.io.IOException;
import java.io.StringReader;

/**
 * Class for analyzing documents that have xml markup for tagged sections.
 */
public class MarkedUpDocumentAnalyzer {

  private FlagConfig flagConfig;
  private VectorStoreDeterministic elementalVectors;
  private DocumentBuilder documentBuilder;
  private ProportionVectors proportionVectors;

  public MarkedUpDocumentAnalyzer(FlagConfig flagConfig) {
    this.flagConfig = flagConfig;
    this.elementalVectors = new VectorStoreDeterministic(flagConfig);
    this.proportionVectors = new ProportionVectors(flagConfig);
    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    try {
      this.documentBuilder = dbFactory.newDocumentBuilder();
    } catch (ParserConfigurationException e) {
      e.printStackTrace();
    }
  }

  public Document getXmlDocumentFromString(String source) throws IOException, SAXException {
    return documentBuilder.parse(new InputSource(new StringReader(source)));
  }

  public Vector getVectorForTaggedDocument(Document document) {
    Vector documentVector = VectorFactory.createZeroVector(this.flagConfig.vectortype(), this.flagConfig.dimension());
    NodeList nodeList = document.getElementsByTagName("*");
    int numElements = nodeList.getLength();
    for (int i = 0; i < numElements; i++) {
      Node node = nodeList.item(i);
      Vector elementVector = this.elementalVectors.getVector(node.getNodeName()).copy();
      elementVector.bind(this.proportionVectors.getProportionVector(((double)i)/numElements));
      documentVector.superpose(elementVector, 1, null);
    }

    documentVector.normalize();
    return documentVector;
  }

  public Vector getVectorForString(String input) throws IOException, SAXException {
    return this.getVectorForTaggedDocument(this.getXmlDocumentFromString(input));
  }

  private static String othello =
      "<document><setting>Othello is a hero.</setting>"
      + "<marriage>Othello and Desdemona get married.</marriage>"
      + "<confusion>Othello wrongly suspects and kills Desdemona.</confusion></document>";

  private static String midsummerNightsDream =
      "<document><setting>Everyone goes to the forest.</setting>"
          + "<confusion>They are tricked into falling in love with the wrong people.</confusion>"
          + "<marriage>Hermia marries Lysander. Helena marries Demetrius.</marriage></document>";

  private static String twelfthNight =
      "<document><setting>Viola is shipwrecked but useful.</setting>"
          + "<confusion>She disguises herself and woos Olivia on behalf of Orsino.</confusion>"
          + "<marriage>Viola marries Orsino. Olivia marries Sebastian.</marriage></document>";

  public static void main(String[] args) throws ParserConfigurationException, IOException, SAXException {
    FlagConfig flagConfig = FlagConfig.getFlagConfig(args);
    MarkedUpDocumentAnalyzer analyzer = new MarkedUpDocumentAnalyzer(flagConfig);

    Vector othelloVector = analyzer.getVectorForString(othello);
    Vector midsummerVector = analyzer.getVectorForString(midsummerNightsDream);
    Vector twelfthNightVector = analyzer.getVectorForString(twelfthNight);

    System.out.println("Structural similarity of Othello with A Midsummer Night's Dream:");
    System.out.println(othelloVector.measureOverlap(midsummerVector));
    System.out.println("Structural similarity of Othello with Twelfth Night:");
    System.out.println(twelfthNightVector.measureOverlap(othelloVector));
    System.out.println("Structural similarity of A Midsummer Night's Dream with Twelfth Night:");
    System.out.println(twelfthNightVector.measureOverlap(midsummerVector));
  }
}
