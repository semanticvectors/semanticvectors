/** 
Copyright (c) 2008, University of Pittsburgh

	 All rights reserved.

	 Redistribution and use in source and binary forms, with or without
	 modification, are permitted provided that the following conditions are
	 met:

	 * Redistributions of source code must retain the above copyright
	 notice, this list of conditions and the following disclaimer.

	 * Redistributions in binary form must reproduce the above
	 copyright notice, this list of conditions and the following
	 disclaimer in the documentation and/or other materials provided
	 with the distribution.

	 * Neither the name of the University of Pittsburgh nor the names
	 of its contributors may be used to endorse or promote products
	 derived from this software without specific prior written
	 permission.

	 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
	 "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
	 LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
	 A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
	 CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
	 EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
	 PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
	 PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
	 LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
	 NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
	 SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
**/

package pitt.search.semanticvectors;

import java.io.IOException;
import java.lang.IllegalArgumentException;
import java.util.LinkedList;

/**
 * Command line utility for creating semantic vector indexes using the
 * sliding context window approach (see work on HAL, and by Shutze).
 */
public class BuildPositionalIndex {
	/* These can now be modified with command line arguments */
	static int seedLength = 20;
	static int nonAlphabet = 0;
	static int minFreq = 10;
	static int windowLength = 5;
	static int trainingCycles = 1;
	static IndexType indexType = IndexType.BASIC;
	static VectorStore newBasicTermVectors = null;

	/**
	 * Enumeration of different indexTypes - basic, directional and permutation.
	 */
	public enum IndexType {
		/**
		 * Default option.
		 */
		BASIC,
		/**
		 * Directional - distinguishes between terms occuring pefore and
		 after target term.
		*/
		DIRECTIONAL,
		/**
		 * Uses permutation to encode word order - see Sahlgren et al, 2008.
		 */
		PERMUTATION
	}

	/**
	 * Prints the following usage message:
	 * <code>
	 * <br> BuildPositionalIndex class in package pitt.search.semanticvectors
	 * <br> Usage: java pitt.search.semanticvectors.BuildPositionalIndex PATH_TO_LUCENE_INDEX
	 * <br> BuildPositionalIndex creates file termtermvectors.bin in local directory.
	 * <br> Other parameters that can be changed include windowlength (size of context window),
	 * <br>     vector length (number of dimensions), seed length (number of non-zero
	 * <br>     entries in basic vectors), and minimum term frequency.
	 * <br> To change these use the following command line arguments:
	 * <br> -d [number of dimensions]
	 * <br> -s [seed length]
	 * <br> -m [minimum term frequency]
	 * <br> -w [window size]
	 * <br> -indextype [type of index: basic (default), directional (HAL), permutation (Sahlgren 2008)
	 * </code>
	 */
		public static void usage() {
			String usageMessage = "\nBuildPositionalIndex class in package pitt.search.semanticvectors"
				+ "\nUsage: java pitt.search.semanticvectors.BuildPositionalIndex PATH_TO_LUCENE_INDEX"
				+ "\nBuildPositionalIndex creates file termtermvectors.bin in local directory."
				+ "\nOther parameters that can be changed include vector length,"
				+ "\n windowlength (size of sliding context window),"
				+ "\n    (number of dimensions), seed length (number of non-zero"
				+ "\n    entries in basic vectors), size of sliding window (including focus term)"
				+ "\n and minimum term frequency.\n"
				+ "\nTo change these use the command line arguments "
				+ "\n  -d [number of dimensions]"
				+ "\n  -s [seed length]"
				+ "\n  -m [minimum term frequency]"
				+ "\n -pt [name of preexisting vectorstore for term vectors]"
				+ "\n  -w [window size]"
				+ "\n  -indextype [type of index: basic (default), directional (HAL), permutation (Sahlgren 2008)";

			System.out.println(usageMessage);
		}

	/**
	 * Builds term vector stores from a Lucene index - this index must
	 contain TermPositionVectors.
	 * @param args
	 * @see CopyOfBuildPositionalIndex#usage
	 */
	public static void main (String[] args) throws IllegalArgumentException {
		boolean wellFormed = false;
		
		/* If only one argument, it should be the path to Lucene index. */
		if (args.length == 1) {
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
						System.err.println(ar + " is not a number");
						usage();
						throw new IllegalArgumentException("Failed to parse command line arguments.");
					}
				}
					/* Allow n non-alphabet characters, or -1 for no character screening*/
				else if (pa.equalsIgnoreCase("-n")) {
					try {
						nonAlphabet = Integer.parseInt(ar);
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
							throw new IllegalArgumentException("Failed to parse command line arguments.");
						}
						else wellFormed = true;
					} catch (NumberFormatException e) {
						System.err.println(ar + " is not a number");
						usage();
						throw new IllegalArgumentException("Failed parse command line arguments.");
					}
				}
				/* Get minimum term frequency. */
				else if (pa.equalsIgnoreCase("-m")) {
					try {
						minFreq = Integer.parseInt(ar);
						if (minFreq < 0) {
							System.err.println("Minimum frequency cannot be less than zero");
							usage();
							throw new IllegalArgumentException("Failed to parse command line arguments.");
						}
						else wellFormed = true;
					} catch (NumberFormatException e) {
						System.err.println(ar + " is not a number"); usage();
					}
				}
				else if (pa.equalsIgnoreCase("-indextype")) {
					/* Determine index type */
					String indexTypeString = ar;
					boolean validindex = false;
					// TODO(dwiddows): Find if this routine can be autogenerated from enum.
					if (indexTypeString.equalsIgnoreCase("basic")) {
						indexType = IndexType.BASIC;
						wellFormed = true;
					} else
					if (indexTypeString.equalsIgnoreCase("directional")) {
						indexType = IndexType.DIRECTIONAL;
						wellFormed = true;
					} else
					if (indexTypeString.equalsIgnoreCase("permutation")) {
						indexType = IndexType.PERMUTATION;
						wellFormed = true;
					} else {
						System.err.println("Did not recognize index type " + indexTypeString);
						System.err.println("Building basic (sliding window, non-directional) positional index");
					}
				}
				/* Get window size */
				else if (pa.equalsIgnoreCase("-w")) {
					try {
						windowLength = Integer.parseInt(ar);
						if ((windowLength <= 2) |  (windowLength %2 == 0)  ) {
							System.err.println("Windowlength must be an odd number " +
																 "(to accommodate a central focus term), larger than 2");
							usage();
							throw new IllegalArgumentException("Failed to parse command line arguments.");
						}
						else wellFormed = true;
					} catch (NumberFormatException e) {
						System.err.println(ar + " is not a number");
						usage();
						throw new IllegalArgumentException("Failed to parse command line arguments.");
					}
				}	/* Get number of training cycles. */
				else if (pa.equalsIgnoreCase("-tc")) {
					try {
						trainingCycles = Integer.parseInt(ar);
						if (trainingCycles < 1) {
							System.err.println("Minimum frequency cannot be less than one.");
							usage();
							throw new IllegalArgumentException();
						}
						else wellFormed = true;
					} catch (NumberFormatException e) {
						System.err.println(ar + " is not a number.");
						usage();
						throw new IllegalArgumentException();
					}
				}
				/* Get term vectors to use for pre-trained (rather than random) indexes */
				else if (pa.equalsIgnoreCase("-pt")) {
					try {
							VectorStoreRAM vsr = new VectorStoreRAM();
							vsr.InitFromFile(ar);
						newBasicTermVectors = vsr;
						wellFormed = true;
						System.err.println("Using trained index vectors from vector store "+ar);
						
					} catch (IOException e) {
						System.err.println("Could not read from vector store "+ar);
						usage();
						throw new IllegalArgumentException();
					}
				}
				/* All other arguments are unknown. */
				else {
					System.err.println("Unknown command line option: " + pa);
					usage();
					throw new IllegalArgumentException("Failed to parse command line arguments.");
				}
				
			}
		}
		if (!wellFormed) {
			usage();
			throw new IllegalArgumentException("Failed to parse command line arguments.");
		}

		String luceneIndex = args[args.length-1];
		String termFile = "termtermvectors.bin";
		if (indexType == IndexType.PERMUTATION) termFile = "permtermvectors.bin";
		else if (indexType == IndexType.DIRECTIONAL) termFile = "drxntermvectors.bin";
		
		String docFile = "docvectors.bin";
		String[] fieldsToIndex = {"contents"};
		System.err.println("seedLength = " + seedLength);
		System.err.println("Vector length = " + ObjectVector.vecLength);
		System.err.println("Minimum frequency = " + minFreq);
			System.err.println("Nubmer non-alphabet characters = " + nonAlphabet);
		System.err.println("Window length = " + windowLength);
		
		
		
		try {
			TermTermVectorsFromLucene vecStore =
				new TermTermVectorsFromLucene(luceneIndex, seedLength, minFreq, nonAlphabet,
																			windowLength, newBasicTermVectors, fieldsToIndex, indexType);
			VectorStoreWriter vecWriter = new VectorStoreWriter();
			System.err.println("Writing term vectors to " + termFile);
			vecWriter.WriteVectors(termFile, vecStore);
			
			for (int i = 1; i < trainingCycles; ++i) {
				newBasicTermVectors = vecStore.getBasicTermVectors();
				System.err.println("\nRetraining with learned term vectors ...");
				vecStore = new TermTermVectorsFromLucene(luceneIndex,
																						 seedLength,
																						 minFreq,
																						 nonAlphabet,
																						 windowLength,
																						 newBasicTermVectors,
																						 fieldsToIndex, indexType);
				
			}
	
			if (trainingCycles > 1) {
				termFile = "termvectors" + trainingCycles + ".bin";
				docFile = "docvectors" + trainingCycles + ".bin";
			}
			
			// Write document vectors except for permutation index.
			if (indexType == IndexType.BASIC) {
				IncrementalDocVectors docVectors =
					new IncrementalDocVectors(vecStore, luceneIndex, fieldsToIndex,	"incremental_"+docFile);
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
}

