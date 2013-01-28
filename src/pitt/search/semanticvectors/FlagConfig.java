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

import java.lang.Enum;
import java.lang.IllegalArgumentException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.logging.Logger;

import pitt.search.semanticvectors.vectors.VectorType;

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
public class FlagConfig {
  private static final Logger logger = Logger.getLogger(FlagConfig.class.getCanonicalName());
  
  private FlagConfig() {
    Field[] fields = FlagConfig.class.getDeclaredFields();
    for (int q = 0; q < fields.length; q++)
      fields[q].setAccessible(true);
  }
  
  public String[] remainingArgs;

  // Add new command line flags here. By convention, please use lower case.
  //
  // DO NOT DUPLICATE NAMES HERE! YOU WILL OVERWRITE OTHER PEOPLE's FLAGS!
  private int dimension = 200;
  public int getDimension() { return dimension; }
  public void setDimension(int dimension) { this.dimension = dimension; }
  public static final String dimensionDescription = "Dimension of semantic vector space";

  private VectorType vectortype = VectorType.REAL;
  public VectorType getVectortype() { return vectortype; }
  public void setVectortype(VectorType vectortype) {this.vectortype = vectortype; }
  public static final String vectortypeDescription = "Ground field for vectors: real, binary or complex.";
  public static final String[] vectortypeValues = {"binary", "real", "complex"};

  public int seedlength = 10;
  public int getSeedlength() { return seedlength; }
  public static final String seedlengthDescription =
    "Number of +1 and number of -1 entries in a sparse random vector";
  
  private int minfrequency = 0;
  public int getMinfrequency() { return minfrequency; }
  private int maxfrequency = Integer.MAX_VALUE;
  public int getMaxfrequency() { return maxfrequency; }
  private int maxnonalphabetchars = Integer.MAX_VALUE;
  public int getMaxnonalphabetchars() { return maxnonalphabetchars; }
  private boolean filteroutnumbers = true;
  public boolean getFilteroutnumbers() { return filteroutnumbers; }
  private boolean deterministicvectors = false;
  public boolean getDeterministicvectors() { return deterministicvectors; }
  
  
  private String indexrootdirectory = "";
  public String getIndexrootdirectory() { return indexrootdirectory; }
  public String indexrootdirectoryDescription = "Allow for the specification of a directory to place the lucene index in. Requires a trailing slash";
  
  private int numsearchresults = 20;
  public int getNumsearchresults() { return numsearchresults; }
  
  private double searchresultsminscore = -1.0;
  public double getSearchresultsminscore() { return searchresultsminscore; }
  public static final String searchresultsminscoreDescription = "Search results with similarity scores below "
    + "this value will not be included in search results.";

  private int numclusters = 5;
  public int getNumclusters() { return numclusters; }
  private int trainingcycles = 0;
  public int getTrainingcycles() { return trainingcycles; }
  private int windowradius = 5;
  public int getWindowradius() { return windowradius; }

  private String searchtype = "sum";
  public String getSearchtype() { return searchtype; }
  public static final String searchtypeDescription = "Method used for combining and searching vectors.";
  public static final String[] searchtypeValues =
    {"sum", "sparsesum", "subspace", "maxsim", "balanced_permutation", "permutation",
     "boundproduct", "boundproductsubspace", "analogy", "printquery"};

  private boolean fieldweight = false;
  public boolean getFieldweight() { return fieldweight; }
  public static final String fieldweightDescription =
	  "Set to true if you want document vectors built from multiple fields to emphasize terms from shorter fields";
  
  private String termweight = "none";
  public String getTermweight() { return termweight; }
  public static final String termweightDescription = "Term weighting used when constructing document vectors.";
  public static final String[] termweightValues = {"logentropy","idf", "none"};

  private boolean porterstemmer = false;
  public boolean getPorterstemmer() { return porterstemmer; }
  public static final String porterstemmerDescription =
    "Set to true when using IndexFilePositions if you would like to stem terms";

  private boolean usetermweightsinsearch = false;
  public boolean getUsetermweightsinsearch() { return usetermweightsinsearch; }
  public static final String usetermweightsinsearchDescription =
    "Set to true only if you want to scale each comparison score by a term weight during search.";

  private boolean stdev = false;
  public boolean getStdev() { return stdev; }
  public static final String stdevDescription =
    "Set to true when you would prefer results scored as SD above the mean across all search vectors";

  private boolean expandsearchspace = false;
  public boolean getExpandsearchspace() { return expandsearchspace; }
  public static final String expandsearchspaceDescription =
	  "Set to true to generated bound products from each pairwise element of the search space. "+
	  "Expands the size of the space to n-squared";
  
  private String indexfileformat = "lucene";
  public String getIndexfileformat() { return indexfileformat; }
  public static final String indexfileformatDescription =
    "Format used for serializing / deserializing vectors from disk";
  public static final String[] indexfileformatValues = {"lucene", "text"};

  private String termvectorsfile = "termvectors";
  public String getTermvectorsfile() { return termvectorsfile; }
  private String docvectorsfile = "docvectors";
  public String getDocvectorsfile() { return docvectorsfile; }
  private String termtermvectorsfile = "termtermvectors";
  public String getTermtermvectorsfile() { return termtermvectorsfile; }
  
  private String queryvectorfile = "termvectors";
  public String getQueryvectorfile() { return queryvectorfile; }
  public static final String queryvectorfileDescription = "Principal vector store for finding query vectors.";

  private String searchvectorfile = "";
  public String getSearchvectorfile() { return searchvectorfile; }
  public static final String searchvectorfileDescription =
      "Vector store for searching. Defaults to being the same as {@link #queryVecReader}. "
      + "May be different from queryvectorfile e.g., when using terms to search for documents.";
  
  private String boundvectorfile = "";
  public String getBoundvectorfile() { return boundvectorfile; }
  public static final String boundvectorfileDescription =
      "Auxiliary vector store used when searching for boundproducts. Used only in some searchtypes.";

  private String elementalvectorfile = "elementalvectors";
  public String getElementalvectorfile() { return elementalvectorfile; }
  public static final String elementalvectorfileDescription =
      "Random elemental vectors, sometimes written out, and used (e.g.) in conjunction with permuted vector file.";
  
  private String semanticvectorfile = "semanticvectors";
  public String getSemanticvectorfile() { return semanticvectorfile; }
  public static final String semanticvectorfileDescription =
      "Semantic vectors; used so far as a name in PSI.";

  private String predicatevectorfile = "predicatevectors";
  public String getPredicatevectorfile() { return predicatevectorfile; }
  public static final String predicatevectorfileDescription =
      "Vectors used to represent predicates in PSI.";
  
  private String permutedvectorfile = "permtermvectors";
  public String getPermutedvectorfile() { return permutedvectorfile; }
  public static final String permutedvectorfileDescription =
      "Permuted term vectors, output by -positionalmethod permutation.";
  
  private String directionalvectorfile ="drxntermvectors";
  public String getDirectionalvectorfile() { return directionalvectorfile; }
  public static final String directionalvectorfileDescription =
      "Permuted term vectors, output by -positionalmethod directional";
  
  private String permplustermvectorfile ="permplustermvectors";
  public String getPermplustermvectorfile() { return permplustermvectorfile; }
  public static final String permplustermvectorfileDescription =
      "Permuted term vectors, output by -positionalmethod permutation_plus_basic";
  
  private String positionalmethod = "basic";
  public String getPositionalmethod() { return positionalmethod; }
  public static final String positionalmethodDescription =
      "Method used for positional indexing.";
  public static String positionalmethodValues[] =
      {"basic", "directional", "permutation","permutation_plus_basic"};
  
  private String stoplistfile = "";
  public String getStoplistfile() { return stoplistfile; }

  private String startlistfile = "";
  public String getStartlistfile() { return startlistfile; }
  
  private String luceneindexpath = "";
  public String getLuceneindexpath() { return luceneindexpath; }
  
  private String initialtermvectors = "";
  public String getInitialtermvectors() { return initialtermvectors; }
  public static final String initialtermvectorsDescription =
    "Use the vectors in this file for initialization instead of new random vectors.";

  public String initialdocumentvectors = "";
  public static final String initialdocumentvectorsDescription =
    "Use the vectors in this file for initialization instead of new random vectors.";

  private String docindexing = "inmemory";
  public String getDocindexing() { return docindexing; }
  public static final String docindexingDescription = "Memory management method used for indexing documents.";
  public static String docindexingValues[] = {"inmemory", "incremental", "none"};

  private String vectorlookupsyntax = "exactmatch";
  public String getVectorlookupsyntax() { return vectorlookupsyntax; }
  public static final String vectorlookupsyntaxDescription =
    "Method used for looking up vectors in a vector store";
  public static String[] vectorlookupsyntaxValues = {"exactmatch", "regex"};

  private boolean matchcase = false;
  public boolean getMatchcase() { return matchcase; }
  public static final String matchcaseDescription =
      "If true, matching of query terms is case-sensitive; otherwise case-insensitive";
  
  private String vectorstorelocation = "ram";
  public String getVectorstorelocation() { return vectorstorelocation; }
  public static final String vectorstorelocationDescription = "Where to store vectors - in memory or on disk";
  public static String[] vectorstorelocationValues = {"ram", "disk"};

  private String batchcompareseparator = "\\|";
  public String getBatchcompareseparator() { return batchcompareseparator; }
  public static final String batchcompareseparatorDescription = "Separator for documents on a single line in batch comparison mode.";

  private boolean suppressnegatedqueries = false;
  public boolean getSuppressnegatedqueries() { return suppressnegatedqueries; }
  public static final String suppressnegatedqueriesDescription = "Suppress checking for the query negation token which indicates subsequent terms are to be negated when comparing terms. If this is set all terms are treated as positive";

  private String[] contentsfields = {"contents"};
  public String[] getContentsfields() { return contentsfields; }
  private String docidfield = "path";
  public String getDocidfield() { return docidfield; }
  
  /**
   * Parse flags from a single string.  Presumes that string contains only command line flags.
   */
  public static FlagConfig parseFlagsFromString(String header) {
    String[] args = header.split("\\s");
    return getFlagConfig(args);
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
  public static FlagConfig getFlagConfig(String[] args) throws IllegalArgumentException {
    FlagConfig flagConfig = new FlagConfig();
    
    if (args == null || args.length == 0) {
      flagConfig.remainingArgs = new String[0];
      return flagConfig;
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
        Field field = FlagConfig.class.getDeclaredField(flagName);

        // Parse String arguments.
        if (field.getType().getName().equals("java.lang.String")) {
          String flagValue;
          try {
            flagValue = args[argc + 1];
          } catch (ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("option -" + flagName + " requires an argument");
          }
          field.set(flagConfig, flagValue);
          // If there is an enum of accepted values, check that it's one of these.
          try {
            Field valuesField = FlagConfig.class.getField(flagName + "Values");
            String[] valuesList = (String[]) valuesField.get(FlagConfig.class);
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
          field.set(flagConfig, flagValue.split(","));
          argc += 2;
        } else if (field.getType().getName().equals("int")) {
          // Parse int arguments.
          try {
            field.setInt(flagConfig, Integer.parseInt(args[argc + 1]));
          } catch (ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("option -" + flagName + " requires an argument");
          }
          argc += 2;
        } else if (field.getType().getName().equals("double")) {
          // Parse double arguments.
          try {
            field.setDouble(flagConfig, Double.parseDouble(args[argc + 1]));
          } catch (ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("option -" + flagName + " requires an argument");
          }
          argc += 2;
        } else if (field.getType().isEnum()) {
          // Parse enum arguments.
          try {
            Class<Enum> className = (Class<Enum>) field.getType();
            field.set(flagConfig, Enum.valueOf(className, args[argc + 1].toUpperCase()));
          } catch (ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("option -" + flagName + " requires an argument");
          }
          argc += 2;
        } else if (field.getType().getName().equals("boolean")) {
          // Parse boolean arguments.
          field.setBoolean(flagConfig, true);
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
        flagConfig.makeFlagsCompatible();
        return flagConfig;
      }
    }

    // Enforce constraints between flags.
    flagConfig.makeFlagsCompatible();

    // No more command line flags to parse. Trim args[] list and return.
    flagConfig.remainingArgs = new String[args.length - argc];
    for (int i = 0; i < args.length - argc; ++i) {
      flagConfig.remainingArgs[i] = args[argc + i];
    }
    return flagConfig;
  }

  public static void mergeWriteableFlagsFromString(String source, FlagConfig target) {
    FlagConfig sourceConfig = FlagConfig.parseFlagsFromString(source);
    mergeWriteableFlags(sourceConfig, target);
  }
  
  /**
   * Sets dimension and vectortype of target to be the same as that of source.
   */
  public static void mergeWriteableFlags(FlagConfig source, FlagConfig target) {
    if (target.dimension != source.dimension)
    {
      VerbatimLogger.info("Setting dimension of target config to: " + source.dimension + "\n");
      target.dimension = source.dimension;
    }
    if (target.vectortype != source.vectortype)
    {
      VerbatimLogger.info("Setting vectortype of target config to: " + source.vectortype + "\n");
      target.vectortype = source.vectortype;
    }
    target.makeFlagsCompatible();
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
  private void makeFlagsCompatible() {
    if (vectortype == VectorType.BINARY) {
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
