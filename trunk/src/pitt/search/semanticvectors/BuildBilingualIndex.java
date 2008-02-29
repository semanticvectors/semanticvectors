
package pitt.search.semanticvectors;

import java.util.Hashtable;
import java.util.Enumeration;
import java.util.Random;
import java.io.IOException;
import org.apache.lucene.index.*;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

import java.util.LinkedList;
import java.io.IOException;

/**
 * Command line utility for creating semantic vector indexes.
 */
public class BuildBilingualIndex{
    // These can be modified with command line arguments.
    static int seedLength = 20;
    static int minFreq = 10;

    /**
     * Prints the following usage message:
     * <code>
     * <br> BuildBilingualIndex class in package pitt.search.semanticvectors
     * <br> Usage: java pitt.search.semanticvectors.BuildBilingualIndex PATH_TO_LUCENE_INDEX LANG1 LANG2
     * <br> BuildIndex creates files termvectors_LANG*.bin and docvectors_LANG*.bin,
     * <br> in local directory, where LANG1 and LANG2 are obtained from fields in index.
     * <br> Other parameters that can be changed include vector length,
     * <br>     (number of dimensions), seed length (number of non-zero
     * <br>     entries in basic vectors), and minimum term frequency.
     * <br> To change these use the following command line arguments:
     * <br> -d [number of dimensions]
     * <br> -s [seed length]
     * <br> -m [minimum term frequency]
     * </code>
     */
    public static void usage(){
				String usageMessage = "\nBuildIndex class in package pitt.search.semanticvectors"
						+ "\nUsage: java pitt.search.semanticvectors.BuildIndex PATH_TO_LUCENE_INDEX LANG1 LANG2"
						+ "\nBuildBilingaulIndex creates files termvectors_LANG*.bin and docvectors_LANG*.bin,"
						+ "\nin local directory, where LANG1 and LANG2 are obtained from fields in index."
						+ "\nOther parameters that can be changed include vector length,"
						+ "\n    (number of dimensions), seed length (number of non-zero"
						+ "\n    entries in basic vectors), and minimum term frequency."
						+ "\nTo change these use the command line arguments "
						+ "\n  -d [number of dimensions]"
						+ "\n  -s [seed length]"
						+ "\n  -m [minimum term frequency]";

				System.out.println(usageMessage);
				System.exit(-1);
    }

    /**
     * Builds term vector and document vector stores from a Lucene index.
     * @param args
     * @see BuildIndex#usage
     */
    public static void main (String[] args) {
				boolean wellFormed = false;
				/* If only three argument, they should be the path to Lucene index and the language pair. */
				if (args.length == 3) {
						wellFormed = true;
				}
				/* If there is an even number of arguments, there's a problem. */
				else if (args.length % 2 == 0) {
						wellFormed = false;
				}
				/* Parse command line arguments. */
				else {
						for (int x = 0; x < args.length-1; x += 2) {
								String pa = args[x];
								String ar = args[x+1];

								/* Get number of dimensions. */
								if (pa.equalsIgnoreCase("-d")) {
										try {
												ObjectVector.vecLength = Integer.parseInt(ar);
												wellFormed = true;
										} catch (NumberFormatException e) {
												System.err.println(ar + " is not a number"); usage();
										}
								}
								/* Get seedlength. */
								else if (pa.equalsIgnoreCase("-s")) {
										try {
												seedLength = Integer.parseInt(ar);
												if (seedLength > ObjectVector.vecLength) {
														System.err.println("Seed length cannot be greater than vector length");
														usage();
												}
												else wellFormed = true;
										} catch (NumberFormatException e) {
												System.err.println(ar + " is not a number"); usage();
										}
								}
								/* Get minimum term frequency. */
								else if (pa.equalsIgnoreCase("-m")) {
										try {
												minFreq = Integer.parseInt(ar);
												if (minFreq < 0) {
														System.err.println("Minimum frequency cannot be less than zero");
														usage();
												}
												else wellFormed = true;
										} catch (NumberFormatException e) {
												System.err.println(ar + " is not a number"); usage();
										}
								}
								/* All other arguments are unknown. */
								else {
										System.err.println("Unknown command line option: " + pa);
										usage();
								}
						}
				}
        if (!wellFormed) {
						usage();
				}

				String luceneIndex = args[args.length - 3];
				String lang1 = args[args.length - 2];
				String lang2 = args[args.length - 1];
				String termFile1 = "termvectors_" + lang1 + ".bin";
				String termFile2 = "termvectors_" + lang2 + ".bin";
				String docFile1 = "docvectors_" + lang1 + ".bin";
				String docFile2 = "docvectors_" + lang2 + ".bin";
				String[] fields1 = new String[] {"contents_" + lang1};
				String[] fields2 = new String[] {"contents_" + lang2};

				System.err.println("seedLength = " + seedLength);
				System.err.println("Vector length = " + ObjectVector.vecLength);
				System.err.println("Minimum frequency = " + minFreq);
				try{
						TermVectorsFromLucene vecStore1 =
								new TermVectorsFromLucene(luceneIndex, seedLength, minFreq, null, fields1);
						VectorStoreWriter vecWriter = new VectorStoreWriter();
						System.err.println("Writing term vectors to " + termFile1);
						vecWriter.WriteVectors(termFile1, vecStore1);
						// DocVectors docVectors = new DocVectors(vecStore1);
						// System.err.println("Writing doc vectors to " + docFile1);
						// vecWriter.WriteVectors(docFile1, docVectors);

						short[][] basicDocVectors = vecStore1.getBasicDocVectors();
						System.out.println("Keeping basic doc vectors, number: " + basicDocVectors.length);
						TermVectorsFromLucene vecStore2 =
								new TermVectorsFromLucene(luceneIndex, seedLength, minFreq, 
																					basicDocVectors, fields2);
						System.err.println("Writing term vectors to " + termFile2);
						vecWriter.WriteVectors(termFile2, vecStore2);
						// docVectors = new DocVectors(vecStore2);
						// System.err.println("Writing doc vectors to " + docFile2);
						// vecWriter.WriteVectors(docFile2, docVectors);
				}
				catch (IOException e) {
						e.printStackTrace();
				}
    }
}
