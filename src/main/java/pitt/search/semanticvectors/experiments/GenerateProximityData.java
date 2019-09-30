package pitt.search.semanticvectors.experiments;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import pitt.search.semanticvectors.CompoundVectorBuilder;
import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.LuceneUtils;
import pitt.search.semanticvectors.VectorStoreRAM;
import pitt.search.semanticvectors.vectors.RealVector;
import pitt.search.semanticvectors.vectors.Vector;




	public class GenerateProximityData {

		//public String exportFileName = "skipgram";
		//public String VECTOR_FILE = "hpv_skipgram_10.bin";
		public static String TERMS = "terms3.txt";
		private static Map<String, Vector> termCollection = new LinkedHashMap<String, Vector>();


		public GenerateProximityData() {
			// TODO Auto-generated constructor stub
		}

		public void outputProximityData(FlagConfig flagConfig) {
			System.out.println("***Processing started****");
			readTermFile(flagConfig.startlistfile());

			try {
				VectorStoreRAM searchVectorStore = VectorStoreRAM.readFromFile(flagConfig, flagConfig.queryvectorfile());
				for (Map.Entry<String, Vector> entry : termCollection.entrySet()) {
					System.out.println(entry.getKey());
					Vector searchVector = CompoundVectorBuilder.getQueryVector(searchVectorStore, null, flagConfig, entry.getKey().split("_")); //)searchVectorStore.getVector(entry.getKey());
					entry.setValue(searchVector);
				}

				float min = 100000;
				float max = -100000;

				//Vector output
				String vectorOutput = "";
				int dimension = 5;
				ArrayList<String> final_terms = new ArrayList<String>();
				for (Map.Entry<String, Vector> entry : termCollection.entrySet()) {


					if (entry.getValue() != null) {
						dimension = entry.getValue().getDimension();

						for (int i = 0; i < dimension; i++) {
							vectorOutput = vectorOutput.concat(Float.toString(((RealVector) entry.getValue()).getCoordinates()[i]) + " ");
							min = Math.min(((RealVector) entry.getValue()).getCoordinates()[i], min);
							max = Math.max(((RealVector) entry.getValue()).getCoordinates()[i], max);
						}
						vectorOutput = vectorOutput.concat("\r\n");
						final_terms.add(entry.getKey());
					}

				}


				String headerTemplate = "data\r\nsimilarity\r\n" + final_terms.size() + " nodes\r\n" + "9 decimal places\r\n"
						+ min + " minimum data value\r\n" + max + " maximum data value\r\ncoord\r\n" + dimension + " dimensions\r\n"
						+ "Cosine Standard\r\n";

				String finalOutput = headerTemplate.concat(vectorOutput);


				outputFile(finalOutput, flagConfig.queryvectorfile().replaceAll(".bin", ""), final_terms);

				System.out.println("***Processing completed****");


			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		

		public static void main(String[] args) {
			// TODO Auto-generated method stub

			FlagConfig flagConfig = FlagConfig.getFlagConfig(args);
			GenerateProximityData g = new GenerateProximityData();
			g.outputProximityData(flagConfig); //redo for 2019 with seelength mod *** training cycles do not improve coherence***
			
			
		}

		public static void outputFile(String content, String filename, ArrayList<String> terms) throws FileNotFoundException, IOException {

			File file = new File(filename + ".prx.txt");
			try (FileOutputStream fop = new FileOutputStream(file)) {

				byte[] contentInBytes = content.getBytes();
				fop.write(contentInBytes);
				fop.flush();
				fop.close();
			}
			FileWriter writer = new FileWriter("terms_" + filename + ".txt");
			for (String term : terms) {
				writer.write(term + "\n");
			}
			writer.close();

		}

		public static void readTermFile(String fileNamePath) {
			if(!termCollection.isEmpty()) {
				termCollection.clear();
			}
			
			try {
				FileReader fr = new FileReader(fileNamePath);
				BufferedReader bf = new BufferedReader(fr);

				String line = "";

				while ((line = bf.readLine()) != null) {
					String key = line.toLowerCase().trim();
					key = (key.contains(" "))? key.replaceAll(" ", "_") : key;
					termCollection.put(key, null);
				}
				//System.out.println("Size of term collection is " + termCollection.size());

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}

	}

