import java.io.IOException;
import java.util.Date;
import java.util.Enumeration;
import pitt.search.semanticvectors.*;

/**
 * Small class for performance testing the VectorStoreReaderRAMCache
 * against the disk-based VectorStoreReader.
 */
class RAMIndexTester {
  public static void main(String[] args) {
		String vectorFile = "";
		if (args.length == 0) {
			vectorFile = "termvectors.bin";
		}
		else if (args.length == 1) {
			vectorFile = args[1];
		}
		else {
			System.out.println("Must give 0 or 1 vector filename arguments.");
			System.exit(0);
		}

		try {
			VectorStore reader1 = new VectorStoreReader(vectorFile);
			VectorStore reader2 = new VectorStoreReader(vectorFile);
			System.out.println("\nQuery from disk, search from disk     ...");
			QuadraticTest(reader1, reader2);

			reader1 = new VectorStoreReader(vectorFile);
			reader2 = new VectorStoreReaderRAMCache(vectorFile);
			System.out.println("\nQuery from disk, search from memory   ...");
			QuadraticTest(reader1, reader2);

			reader1 = new VectorStoreReaderRAMCache(vectorFile);
			reader2 = new VectorStoreReader(vectorFile);
			System.out.println("\nQuery from memory, search from disk   ...");
			QuadraticTest(reader1, reader2);

			reader1 = new VectorStoreReaderRAMCache(vectorFile);
			reader2 = new VectorStoreReaderRAMCache(vectorFile);
			System.out.println("\nQuery from memory, search from memory ...");
			QuadraticTest(reader1, reader2);
		} catch (IOException e) {
			e.printStackTrace();
		}
  }

	private static void QuadraticTest(VectorStore queryVectors, VectorStore searchVectors) {
		Date start = new Date();
		int counter = 0;
		boolean writeCounts = false;
		Enumeration<ObjectVector> queryVecEnum = queryVectors.getAllVectors();
		while (queryVecEnum.hasMoreElements()) {
			String[] queryTerms = {queryVecEnum.nextElement().getObject().toString()};
			VectorSearcher searcher = new VectorSearcher.VectorSearcherCosine(queryVectors,
																																				searchVectors,
																																				null,
																																				queryTerms);
			searcher.getNearestNeighbors(20);
			if (counter % 100 == 0 && writeCounts) {
				System.out.print(counter + " ... ");
			}
			++counter;
		}
		Date end = new Date();
		System.out.println((end.getTime() - start.getTime()) + " total milliseconds\n");
	}
}
