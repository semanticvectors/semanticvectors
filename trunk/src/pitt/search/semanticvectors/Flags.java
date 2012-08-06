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
import java.util.Arrays;
import java.util.logging.Logger;

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
 * @author Dominic Widdows
 */
public class Flags {
  private static final Logger logger = Logger.getLogger(Flags.class.getCanonicalName());

  // Add new command line flags here. By convention, please use lower case.
  //
  // DO NOT DUPLICATE NAMES HERE! YOU WILL OVERWRITE OTHER PEOPLE's FLAGS!
  public static int dimension = 200;
  public static final String dimensionDescription = "Dimension of semantic vector space";

  public static String vectortype = "real";
  public static final String vectortypeDescription = "Ground field for vectors: real, binary or complex.";
  public static final String[] vectortypeValues = {"binary", "real", "complex"};

  public static int seedlength = 10;
  public static final String seedlengthDescription =
    "Number of +1 and number of -1 entries in a sparse random vector";

  public static int binaryvectordecimalplaces = 2;
  public static final String binaryvectordecimalplacesDescription =
    "Number of decimal places to consider in weighted superpositions of binary vectors. Higher precision requires additional memory during training.";
  
  
  public static int minfrequency;
  public static int maxfrequency = Integer.MAX_VALUE;
  public static int maxnonalphabetchars;

  public static String indexRootDirectory = "";
  public static final String indexRootDirectoryDescription = "Allow for the specification of a directory to place the lucene index in. Requires a trailing slash";
  
  public static int numsearchresults = 20;
  public static double searchresultsminscore = -1.0;
  public static final String searchresultsminscoreDescription = "Search results with similarity scores below "
    + "this value will not be included in search results.";

  public static int numclusters = 5;

  public static int trainingcycles;
  public static int windowradius = 5;

  public static String searchtype = "sum";
  public static final String searchtypeDescription = "Method used for combining and searching vectors.";
  public static final String[] searchtypeValues =
    {"sum", "sparsesum", "subspace", "maxsim", "balanced_permutation", "permutation",
     "boundproduct", "boundproductsubspace", "analogy", "printquery"};

  public static boolean fieldweight = false;
  public static final String fieldweightDescription =
	  "Set to true if you want document vectors built from multiple fields to emphasize terms from shorter fields";
  
  public static String termweight = "none";
  public static final String termweightDescription = "Term weighting used when constructing document vectors.";
  public static final String[] termweightValues = {"logentropy","idf", "none"};

  public static boolean porterstemmer = false;
  public static final String porterstemmerDescription =
    "Set to true when using IndexFilePositions if you would like to stem terms";

  public static boolean usetermweightsinsearch = false;
  public static final String usetermweightsinsearchDescription =
    "Set to true only if you want to scale each comparison score by a term weight during search.";

  public static boolean stdev = false;
  public static final String stdevDescription =
    "Set to true when you would prefer results scored as SD above the mean across all search vectors";

  public static boolean expandsearchspace = false;
  public static final String expandsearchspaceDescription =
	  "Set to true to generated bound products from each pairwise element of the search space. "+
	  "Expands the size of the space to n-squared";
  
  public static String indexfileformat = "lucene";
  public static final String indexfileformatDescription =
    "Format used for serializing / deserializing vectors from disk";
  public static final String[] indexfileformatValues = {"lucene", "text"};

  public static String termvectorsfile = "termvectors";
  public static String docvectorsfile = "docvectors";
  public static String termtermvectorsfile = "termtermvectors";
  
  public static String queryvectorfile = "termvectors";
  public static String queryvectorfileDescription = "Principal vector store for finding query vectors.";

  public static String searchvectorfile = "";
  public static String searchvectorfileDescription =
      "Vector store for searching. Defaults to being the same as {@link #queryVecReader}. "
      + "May be different from queryvectorfile e.g., when using terms to search for documents.";
  
  public static String boundvectorfile = "";
  public static String boundvectorfileDescription =
      "Auxiliary vector store used when searching for boundproducts. Used only in some searchtypes.";

  public static boolean binarybindingwithpermute = false;
  
  public static String elementalvectorfile = "elementalvectors";
  public static String elementalvectorfileDescription =
      "Random elemental vectors, sometimes written out, and used (e.g.) in conjunction with permuted vector file.";
  
  public static String semanticvectorfile = "semanticvectors";
  public static String semanticvectorfileDescription = "Semantic vectors; used so far as a name in PSI.";

  public static String predicatevectorfile = "predicatevectors";
  public static String predicatevectorfileDescription = "Vectors used to represent predicates in PSI.";
  
  public static String permutedvectorfile = "permtermvectors";
  public static String permutedvectorfileDescription =
      "Permuted term vectors, output by -positionalmethod permutation.";
  
  public static String directionalvectorfile ="drxntermvectors";
  public static String directionalvectorfileDescription =
      "Permuted term vectors, output by -positionalmethod directional";
  
  public static String permplustermvectorfile ="permplustermvectors";
  public static String permplustermvectorfileDescription =
      "Permuted term vectors, output by -positionalmethod permutation_plus_basic";
  
  public static String positionalmethod = "basic";
  public static String positionalmethodDescription = "Method used for positional indexing.";
  public static String positionalmethodValues[] =
      {"basic", "directional", "permutation","permutation_plus_basic"};
  
  public static String stoplistfile = "";
  public static String startlistfile = "";
  public static String luceneindexpath = "";
  public static String initialtermvectors = "";
  public static String initialtermvectorsDescription =
    "Use the vectors in this file for initialization instead of new random vectors.";

  public static String initialdocumentvectors = "";
  public static String initialdocumentvectorsDescription =
    "Use the vectors in this file for initialization instead of new random vectors.";

  public static String docindexing = "inmemory";
  public static String docindexingDescription = "Memory management method used for indexing documents.";
  public static String docindexingValues[] = {"inmemory", "incremental", "none"};

  public static String vectorlookupsyntax = "exactmatch";
  public static final String vectorlookupsyntaxDescription =
    "Method used for looking up vectors in a vector store";
  public static String[] vectorlookupsyntaxValues = {"exactmatch", "regex"};

  public static boolean matchcase = false;

  public static String vectorstorelocation = "ram";
  public static String vectorstorelocationDescription = "Where to store vectors - in memory or on disk";
  public static String[] vectorstorelocationValues = {"ram", "disk"};

  public static String batchcompareseparator = "\\|";
  public static String batchcompareseparatorDescription = "Separator for documents on a single line in batch comparison mode.";

  public static boolean suppressnegatedqueries = false;
  public static String suppressnegatedqueriesDescription = "Suppress checking for the query negation token which indicates subsequent terms are to be negated when comparing terms. If this is set all terms are treated as positive";

  public static String[] contentsfields = {"contents"};
  public static String docidfield = "path";

  /**
   * Parse flags from a single string.  Presumes that string contains only command line flags.
   */
  public static void parseFlagsFromString(String header) {
    String[] args = header.split("\\s");
    parseCommandLineFlags(args);
  }

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
      return new String[0];
    }

    int argc = 0;
    while (args[argc].charAt(0) == '-') {
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
          String flagValue;
          try {
            flagValue = args[argc + 1];
          } catch (ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("option -" + flagName + " requires an argument");
          }
          field.set(field, flagValue);
          // If there is an enum of accepted values, check that it's one of these.
          try {
            Field valuesField = Flags.class.getField(flagName + "Values");
            String[] valuesList = (String[]) valuesField.get(Flags.class);
            boolean found = false;
            for (int i = 0; i < valuesList.length; ++i) {
              if (flagValue.equals(valuesList[i])) {
                found = true;
                argc += 2;
                break;
              }
            }
            if (!found) {
              String errString = "Value '" + flagValue + "' not valid value for option -" + flagName
              + "\nValid values are: " + Arrays.toString(valuesList);
              throw new IllegalArgumentException(errString);
            }
          } catch (NoSuchFieldException e) {
            // This just means there isn't a list of allowed values.
            argc += 2;
          }
          // Parse String[] arguments, presuming they are comma-separated.
          // String[] arguments do not currently support fixed Value lists.
        } else if (field.getType().getName().equals("[Ljava.lang.String;")) {
          // All string values are lowercased.
          String flagValue;
          try {
            flagValue = args[argc + 1].toLowerCase();
          } catch (ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("option -" + flagName + " requires an argument");
          }
          field.set(field, flagValue.split(","));
          argc += 2;
        } else if (field.getType().getName().equals("int")) {
          // Parse int arguments.
          try {
            field.setInt(field, Integer.parseInt(args[argc + 1]));
          } catch (ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("option -" + flagName + " requires an argument");
          }
          argc += 2;
        } else if (field.getType().getName().equals("double")) {
          // Parse double arguments.
          try {
            field.setDouble(field, Double.parseDouble(args[argc + 1]));
          } catch (ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("option -" + flagName + " requires an argument");
          }
          argc += 2;
        } else if (field.getType().getName().equals("boolean")) {
          // Parse boolean arguments.
          field.setBoolean(field, true);
          ++argc;
        } else {
          logger.warning("No support for fields of type: "  + field.getType().getName());
          argc += 2;
        }
      } catch (NoSuchFieldException e) {
        throw new IllegalArgumentException("Command line flag not defined: " + flagName);
      } catch (IllegalAccessException e) {
        logger.warning("Must be able to access all fields publicly, including: " + flagName);
        e.printStackTrace();
      }

      if (argc >= args.length) {
        logger.fine("Consumed all command line input while parsing flags");
        makeFlagsCompatible();
        return null;
      }
    }

    // Enforce constraints between flags.
    makeFlagsCompatible();

    // No more command line flags to parse.
    // Trim args[] list and return.
    String[] trimmedArgs = new String[args.length - argc];
    for (int i = 0; i < args.length - argc; ++i) {
      trimmedArgs[i] = args[argc + i];
    }
    return trimmedArgs;
  }

  /**
   * Checks some interaction between flags, and fixes them up to make them compatible.
   * 
   * <br/>
   * In practice, this means:
   * <ul><li>If {@link vectortype} is {@code binary}, {@link dimension} is a multiple of 64,
   * or is increased to be become a multiple of 64.  {@link seedlength} is set to be half this
   * number.</li>
   * </ul>
   */
  private static void makeFlagsCompatible() {
    if (vectortype.equals("binary")) {
      // Impose "multiple-of-64" constraint, to facilitate permutation of 64-bit chunks.
      if (dimension % 64 != 0) {
        dimension = (1 + (dimension / 64)) * 64;
        logger.warning("For performance reasons, dimensions for binary vectors must be a mutliple "
            + "of 64. Flags.dimension set to: " + dimension + ".");
      }
      // Impose "balanced binary vectors" constraint, to facilitate reasonable voting.
      if (seedlength != dimension / 2) {
        seedlength = dimension / 2;
        logger.warning("Binary vectors must be generated with a balanced number of zeros and ones."
            + " Flags.seedlength set to: " + seedlength + ".");
      }
    }
  }
}
