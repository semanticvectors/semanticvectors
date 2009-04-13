/**
   Copyright (c) 2009, the SemanticVectors AUTHORS.

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

import java.lang.IllegalArgumentException;
import java.lang.reflect.Field;

/**
 * Class for representing and parsing global command line flags.
 * 
 * All command line flags for the SemanticVectors package should be defined here.
 * This design is a violation of encapsulation, but since these are things that 
 * the user can break, we believe that we'll create a much cleaner package if we
 * put this power into user's hands explicitly, but at least insist that all command
 * line flags are declared in one place - in the Flags class. Needless to say, the 
 * Flags class only looks after the basic syntax of (name, value) command line flags.
 * All semantics (i.e., in this case, behaviour affected by the flags) is up to the
 * developer to implement.
 * 
 * @author dwiddows
 */
public class Flags {

	// Add new command line flags here. By convention, please use lower case.
	//
	// DO NOT DUPLICATE NAMES HERE! YOU WILL OVERWRITE OTHER PEOPLE's FLAGS!
  public static int dimension = 200;
	public static final String dimensionDescription = "Dimension of semantic vector space";

  public static int seedlength = 10;
	public static final String seedlengthDescription =
		"Number of +1 and number of -1 entries in a sparse random vector";

  public static int minfrequency;
  public static int maxnonalphabetchars;

  public static int numsearchresults;
  public static int numclusters;

  public static int trainingcycles;

  public static String searchtype = "sum";
	public static final String searchtypeDescription = "Method used for combining and searching vectors.";
	public static final String[] searchtypeValues = {"sum", "sparsesum", "subspace", "maxsim", "tensor",
																									 "convolution", "permutation", "printquery"};

  public static String indextype;
  public static String queryvectorfile;
  public static String searchvectorfile;
  public static String luceneindexpath;

  public static String docindexing = "inmemory";
  public static String docindexingDescription = "Memory management method used for indexing documents.";
  public static String docindexingValues[] = {"inmemory", "incremental", "none"};

  public static String vectorlookupsyntax = "exactmatch";
	public static final String vectorlookupsyntaxDescription =
		"Method used for looking up vectors in a vector store";
  public static String[] vectorlookupsyntaxValues = {"exactmatch", "regex"};
  
	/**
	 * Parse command line flags and create public data structures for accessing them.
	 * @param args
	 * @return trimmed list of arguments with command line flags consumed
	 */
	// This implementation is linear in the number of flags available
	// and the number of command line arguments given. This is quadratic
	// and so inefficient, but in practice we only have to do it once
	// per command so it's probably negligible.
	public static String[] parseCommandLineFlags(String[] args)
		throws IllegalArgumentException {
    if (args.length == 0) {
      throw (new IllegalArgumentException("Cannot parse empty list of command line arguments!"));
    }

		int argc = 0;
		while (args[argc].charAt(0) == '-') {
			boolean recognized = false;
			String flagName = args[argc];
			// Ignore trivial flags (without raising an error).
			if (flagName.equals("-")) continue;
			// Strip off initial "-" repeatedly to get desired flag name.
			while (flagName.charAt(0) == '-') {
				flagName = flagName.substring(1, flagName.length());
			}
			
			try {
			  Field field = Flags.class.getField(flagName);

			  // Parse String arguments.
	      if (field.getType().getName().equals("java.lang.String")) {
					// All string values are lowercased.
					String flagValue = args[argc + 1].toLowerCase();
	        field.set(field, flagValue);
					// If there is an enum of accepted values, check that it's one of these.
					try {
						Field valuesField = Flags.class.getField(flagName + "Values");
						String[] valuesList = (String[]) valuesField.get(Flags.class);
						boolean found = false;
						for (int i = 0; i < valuesList.length; ++i) {
							if (flagValue.equals(valuesList[i])) {
								found = true;
								break;
							}
						}
						if (!found) {
							String errString = "Value '" + flagValue + "' not valid value for option -" + flagName
								+ "\nValid values are: " + joinStringArray(valuesList);
							throw new IllegalArgumentException(errString);
						}
					} catch (NoSuchFieldException e) {
						// This just means there isn't a list of allowed values.		
						continue;
					}

	      // Parse int arguments.
	      } else if (field.getType().getName().equals("int")) {
	        field.setInt(field, Integer.parseInt(args[argc + 1]));
	      }
			  argc += 2;
			} catch (NoSuchFieldException e) {
			  throw new IllegalArgumentException("Command line flag not defined: " + flagName);
			} catch (IllegalAccessException e) {
	      System.err.println("Must be able to access all fields publicly, including: " + flagName);
	      e.printStackTrace();
			}

			if (argc >= args.length) {
				System.err.println("Consumed all command line input while parsing flags");
				return null;
			}
		}

		// No more command line flags to parse.
		// Trim args[] list and return.
		String[] trimmedArgs = new String[args.length - argc];
		for (int i = 0; i < args.length - argc; ++i) {
			trimmedArgs[i] = args[argc + i];
		}
		return trimmedArgs;
	}

	/**
	 * String pretty print a String array.
	 * @return String representation of input array.
	 */
	public static String joinStringArray(String[] values) {
		String result = "";
		for (int i = 0; i < values.length - 1; ++i) {
			result += values[i] + ", ";
		}
		result += values[values.length - 1];
		return result;
	}
}