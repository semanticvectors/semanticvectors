package pitt.search.examples;

/*
Class for indexing folk-tale narratives

Input format is tab-separated "id	docsource	sentencenum	arg1coref1	relnorm	arg2coref1"
e.g.,
1	/thoth/data/russkieNarodnyeSkazki/093_Soltseva_Sestra.txt	2	Ivan	WAS	quiet
2	/thoth/data/russkieNarodnyeSkazki/093_Soltseva_Sestra.txt	2	Ivan	SAY	mother
*/

import pitt.search.semanticvectors.*;
import pitt.search.semanticvectors.orthography.ProportionVectors;
import pitt.search.semanticvectors.vectors.Vector;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class NarrativeRelationsIndexer {

  String TRIPLES_OUTPUT_FILE = "triples";
  String TRIPLES_POSITIONS_OUTPUT_FILE = "triples_positions";
  String DYADS_OUTPUT_FILE = "dyads";
  String DYADS_POSITIONS_OUTPUT_FILE = "dyads_positions";
  String VERBS_OUTPUT_FILE = "verbs";
  String VERBS_POSITIONS_OUTPUT_FILE = "verbs_positions";

  private ElementalVectorStore elementalVectors;
  private List<ParsedRecord> parsedRecords = new ArrayList<>();
  private HashMap<String, Integer> docToMaxPosition = new HashMap<>();
  private ProportionVectors proportionVectors;

  private static class ParsedRecord {
    String docName;
    int position;
    String subject;
    String predicate;
    String object;

    private ParsedRecord(String line) {
      String[] records = line.split("\t");
      String[] pathElements = records[1].split("/");
      this.docName = pathElements[pathElements.length - 1];
      this.position = Integer.parseInt(records[2]);
      this.subject = records[3];
      this.predicate = records[4];
      this.object = records[5];
    }
  }

  private void bindWithPosition(ParsedRecord record, Vector vector) {
    Vector proportionVector = this.proportionVectors.getProportionVector(
        record.position / (double) this.docToMaxPosition.get(record.docName));
    vector.bind(proportionVector);
  }

  private Vector getPsiTripleVector(ParsedRecord record) {
    Vector returnVector = this.elementalVectors.getVector(record.subject).copy();
    returnVector.bind(this.elementalVectors.getVector(record.predicate).copy());
    returnVector.bind(this.elementalVectors.getVector(record.predicate).copy());
    return returnVector;
  }

  private Vector getPsiDyadVector(ParsedRecord record) {
    Vector subjectRelationVector = this.elementalVectors.getVector(record.subject).copy();
    subjectRelationVector.bind(this.elementalVectors.getVector(record.predicate).copy());

    Vector objectRelationVector = this.elementalVectors.getVector(record.predicate).copy();
    objectRelationVector.bind(this.elementalVectors.getVector(record.object).copy());

    subjectRelationVector.superpose(objectRelationVector, 1, null);
    return subjectRelationVector;
  }

  /* Parse all the records in the inputFile, skipping the header line. */
  private void parseInputFile(String inputFile) throws IOException {
    List<String> lines = Files.readAllLines(FileSystems.getDefault().getPath(inputFile), Charset.defaultCharset());
    this.parsedRecords = new ArrayList<>();
    for (int i = 1; i < lines.size(); ++i) {
      this.parsedRecords.add(new ParsedRecord(lines.get(i)));
    }
  }

  /* First pass over corpus to collect document names and lengths for later. */
  private void getDocLengths() {
    HashMap<String, Integer> docToMaxPosition = new HashMap<>();
    for (ParsedRecord record : this.parsedRecords) {
      if (!this.docToMaxPosition.containsKey(record.docName) ||
          this.docToMaxPosition.containsKey(record.docName) && this.docToMaxPosition.get(record.docName) < record.position) {
        this.docToMaxPosition.put(record.docName, record.position);
      }
    }
  }

  private void indexRelations(String inputFile, FlagConfig flagConfig) throws IOException {
    this.elementalVectors = new ElementalVectorStore(flagConfig);
    this.proportionVectors = new ProportionVectors(flagConfig);
    VectorStoreWriter writer = new VectorStoreWriter();

    // Turn all the text lines into parsed records.
    this.parseInputFile(inputFile);
    this.getDocLengths();

    // Now the various indexing techniques.
    VectorStoreRAM triplesVectors = new VectorStoreRAM(flagConfig);
    VectorStoreRAM triplesPositionsVectors = new VectorStoreRAM(flagConfig);
    VectorStoreRAM dyadsVectors = new VectorStoreRAM(flagConfig);
    VectorStoreRAM dyadsPositionsVectors = new VectorStoreRAM(flagConfig);
    VectorStoreRAM verbsVectors = new VectorStoreRAM(flagConfig);
    VectorStoreRAM verbsPositionsVectors = new VectorStoreRAM(flagConfig);

    for (ParsedRecord record : this.parsedRecords) {
      Vector tripleVector = this.getPsiTripleVector(record);
      triplesVectors.getVectorOrZero(record.docName).superpose(tripleVector, 1, null);
      this.bindWithPosition(record, tripleVector);
      triplesPositionsVectors.getVectorOrZero(record.docName).superpose(tripleVector, 1, null);

      Vector dyadVector = this.getPsiDyadVector(record);
      dyadsVectors.getVectorOrZero(record.docName).superpose(dyadVector, 1, null);
      this.bindWithPosition(record, dyadVector);
      dyadsPositionsVectors.getVectorOrZero(record.docName).superpose(dyadVector, 1, null);

      Vector verbVector = this.elementalVectors.getVector(record.predicate);
      verbsVectors.getVectorOrZero(record.docName).superpose(verbVector, 1, null);
      this.bindWithPosition(record, verbVector);
      verbsPositionsVectors.getVectorOrZero(record.docName).superpose(verbVector, 1, null);
    }

    writer.writeVectors(TRIPLES_OUTPUT_FILE, flagConfig, triplesVectors);
    writer.writeVectors(TRIPLES_POSITIONS_OUTPUT_FILE, flagConfig, triplesPositionsVectors);

    writer.writeVectors(DYADS_OUTPUT_FILE, flagConfig, dyadsVectors);
    writer.writeVectors(DYADS_POSITIONS_OUTPUT_FILE, flagConfig, dyadsPositionsVectors);

    writer.writeVectors(VERBS_OUTPUT_FILE, flagConfig, verbsVectors);
    writer.writeVectors(VERBS_POSITIONS_OUTPUT_FILE, flagConfig, verbsPositionsVectors);
  }

  public static void main(String[] args) throws IOException {
    FlagConfig flagConfig = FlagConfig.getFlagConfig(args);
    String inputFile = flagConfig.remainingArgs[0];
    NarrativeRelationsIndexer indexer = new NarrativeRelationsIndexer();
    indexer.indexRelations(inputFile, flagConfig);
  }
}