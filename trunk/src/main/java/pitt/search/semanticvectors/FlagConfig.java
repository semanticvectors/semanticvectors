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

import pitt.search.semanticvectors.utils.VerbatimLogger;
import pitt.search.semanticvectors.vectors.RealVector;
import pitt.search.semanticvectors.vectors.RealVector.RealBindMethod;
/** Imports must include the declarations of all enums used as flag values */
import pitt.search.semanticvectors.vectors.VectorType;
import pitt.search.semanticvectors.CompoundVectorBuilder.VectorLookupSyntax;
import pitt.search.semanticvectors.DocVectors.DocIndexingStrategy;
import pitt.search.semanticvectors.ElementalVectorStore.ElementalGenerationMethod;
import pitt.search.semanticvectors.LuceneUtils.TermWeight;
import pitt.search.semanticvectors.Search.SearchType;
import pitt.search.semanticvectors.TermTermVectorsFromLucene.PositionalMethod;
import pitt.search.semanticvectors.VectorStoreUtils.VectorStoreFormat;

/**
 * Class for representing and parsing command line flags into a configuration
 * instance to be passed to other components.
 *
 * Nearly all flags are configured once when an instance is created. Exceptions
 * are {@link #dimension()} and {#link vectortype()}, since these can be set
 * when a {@code VectorStore} is opened for reading.
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

  private int dimension = 200;
  /** Dimension of semantic vector space, default value 200. Can be set when a {@code VectorStore} is opened.
      Recommended values are in the hundreds for {@link VectorType#REAL} and {@link VectorType#COMPLEX} 
      and in the thousands for {@link VectorType#BINARY}, since binary dimensions are single bits. */
  public int dimension() { return dimension; }
  /** Sets the {@link #dimension()}. */
  public void setDimension(int dimension) {
    this.dimension = dimension;
    this.makeFlagsCompatible();
  }

  private VectorType vectortype = VectorType.REAL;
  /** Ground field for vectors: real, binary or complex. Can be set when a {@code VectorStore} is opened.
   * Default value {@link VectorType#REAL}, corresponding to "-vectortype real". */
  public VectorType vectortype() { return vectortype; }
  /** Sets the {@link #vectortype()}. */
  public void setVectortype(VectorType vectortype) {
    this.vectortype = vectortype;
    this.makeFlagsCompatible();
  }

  private RealBindMethod realbindmethod = RealBindMethod.CONVOLUTION; 
  /** The binding method used for real vectors, see {@link RealVector#BIND_METHOD}. */
  public RealBindMethod realbindmethod() { return realbindmethod; }
  
  private ElementalGenerationMethod elementalmethod = ElementalGenerationMethod.RANDOM;
  /** The method used for generating elemental vectors. */
  public ElementalGenerationMethod elementalmethod() { return elementalmethod; }
  
  public int seedlength = 10;
  /** Number of nonzero entries in a sparse random vector, default value 10 except for
   * when {@link #vectortype()} is {@link VectorType#BINARY}, in which case default of
   * {@link #dimension()} / 2 is enforced by {@link #makeFlagsCompatible()}.
   */
  public int seedlength() { return seedlength; }
  
  private int minfrequency = 0;
  /** Minimum frequency of a term for it to be indexed, default value 0. */
  public int minfrequency() { return minfrequency; }

  private int maxfrequency = Integer.MAX_VALUE;
  /** Maximum frequency of a term for it to be indexed, default value {@link Integer#MAX_VALUE}. */
  public int maxfrequency() { return maxfrequency; }

  private int maxnonalphabetchars = Integer.MAX_VALUE;
  /** Maximum number of nonalphabetic characters in a term for it to be indexed, default value {@link Integer#MAX_VALUE}. */
  public int maxnonalphabetchars() { return maxnonalphabetchars; }

  private boolean filteroutnumbers = false;
  /** If {@code true}, terms containing only numeric characters are filtered out during indexing, default value {@code true}. */
  public boolean filteroutnumbers() { return filteroutnumbers; }

  private boolean hybridvectors = false;
  /** If {@code true}, the StringEdit Class will produce hybrid vectors where each term vector = orthographic vector + semantic vector (from -queryvectorfile), default value {@code false}. */
  public boolean hybridvectors() { return hybridvectors; }
  
  private boolean deterministicvectors = false;
  /** If {@code true}, deterministic vectors will be used throughout indexing, using {@code pitt.search.semanticvectors.hashing}. */
  public boolean deterministicvectors() { return deterministicvectors; }
  
  private int numsearchresults = 20;
  /** Number of search results to return, default value 20. */
  public int numsearchresults() { return numsearchresults; }
  
  private String jsonfile = "";
  /** Output search results as graph representation of a connectivity matrix in JSON**/
  public String jsonfile() { return jsonfile;}
  
  /** Pathfinder parameters - default q = (n-1), default r = + infinity**/
  private int pathfinderQ = -1; 
  public int pathfinderQ() { return pathfinderQ; }
  private double pathfinderR = Double.POSITIVE_INFINITY;
  public double pathfinderR() { return pathfinderR; }
  
  private double searchresultsminscore = -1.0;
  /** Search results with similarity scores below this value will not be included in search results, default value -1. */
  public double searchresultsminscore() { return searchresultsminscore; }

  private int numclusters = 10;
  /** Number of clusters used in {@link ClusterResults} and {@link ClusterVectorStore}, default value 10. */
  public int numclusters() { return numclusters; }
  
  private int trainingcycles = 0;
  /** Number of training cycles used for Reflective Random Indexing in {@link BuildIndex}. */
  public int trainingcycles() { return trainingcycles; }
  
  private int windowradius = 5;
  /** Window radius used in {@link BuildPositionalIndex}, default value 5. */
  public int windowradius() { return windowradius; }

  private SearchType searchtype = SearchType.SUM;
  /** Method used for combining and searching vectors,
   * default value {@link SearchType#SUM} corresponding to "-searchtype sum". */
  public SearchType searchtype() { return searchtype; }

  private boolean fieldweight = false;
  /** Set to true if you want document vectors built from multiple fields to emphasize terms from shorter fields, default value {@code false}. */
  public boolean fieldweight() { return fieldweight; }
  
  private TermWeight termweight = TermWeight.NONE;
  /** Term weighting used when constructing document vectors, default value {@link TermWeight#NONE} */
  public LuceneUtils.TermWeight termweight() { return termweight; }

  private boolean porterstemmer = false;
  /** Tells {@link pitt.search.lucene.IndexFilePositions} to stem terms using Porter Stemmer, default value false. */
  public boolean porterstemmer() { return porterstemmer; }

  private boolean usetermweightsinsearch = false;
  /** Tells search implementations to scale each comparison score by a term weight during search, default value false. */
  public boolean usetermweightsinsearch() { return usetermweightsinsearch; }
 
  private boolean stdev = false;
  /** Score search results according to number of SDs above the mean across all search vectors, default false. */
  public boolean stdev() { return stdev; }

  private boolean expandsearchspace = false;
  /** Generate bound products from each pairwise element of the search space, default false.
   *  Expands the size of the space to n-squared. */
  public boolean expandsearchspace() { return expandsearchspace; }

  private VectorStoreFormat indexfileformat = VectorStoreFormat.LUCENE;
  /** Format used for serializing / deserializing vectors from disk, default lucene. */
  VectorStoreFormat indexfileformat() { return indexfileformat; }

  private String termvectorsfile = "termvectors";
  /** File to which termvectors are written during indexing. */
  public String termvectorsfile() { return termvectorsfile; }
  
  private String docvectorsfile = "docvectors";
  /** File to which docvectors are written during indexing. */
  public String docvectorsfile() { return docvectorsfile; }
  
  private String termtermvectorsfile = "termtermvectors";
  /** File to which docvectors are written during indexing. */
  public String termtermvectorsfile() { return termtermvectorsfile; }
  
  private String queryvectorfile = "termvectors";
  /** Principal vector store for finding query vectors, default termvectors.bin. */
  public String queryvectorfile() { return queryvectorfile; }
  
  private String searchvectorfile = "";
  /** Vector store for searching. Defaults to being the same as {@link #queryvectorfile}.
    May be different from queryvectorfile e.g., when using terms to search for documents. */
  public String searchvectorfile() { return searchvectorfile; }
  
  private String boundvectorfile = "";
  /** Auxiliary vector store used when searching for boundproducts. Used only in some searchtypes. */
  public String boundvectorfile() { return boundvectorfile; }

  private String elementalvectorfile = "elementalvectors";
  /** Random elemental vectors, sometimes written out, and used (e.g.) in conjunction with permuted vector file. */
  public String elementalvectorfile() { return elementalvectorfile; }

  private String semanticvectorfile = "semanticvectors";
  /** Semantic vectors; used so far as a name in PSI. */
  public String semanticvectorfile() { return semanticvectorfile; }

  private String predicatevectorfile = "predicatevectors";
  /** Vectors used to represent predicates in PSI. */
  public String predicatevectorfile() { return predicatevectorfile; }
  
  private String permutedvectorfile = "permtermvectors";
  /** "Permuted term vectors, output by -positionalmethod permutation. */
  public String permutedvectorfile() { return permutedvectorfile; }
  
  private String proximityvectorfile = "proxtermvectors";
  /** "Permuted term vectors, output by -positionalmethod permutation. */
  public String proximityvectorfile() { return proximityvectorfile; }
  
  private String directionalvectorfile ="drxntermvectors";
  /** Permuted term vectors, output by -positionalmethod directional. */
  public String directionalvectorfile() { return directionalvectorfile; }      
  
  private String permplustermvectorfile ="permplustermvectors";
  /** "Permuted term vectors, output by -positionalmethod permutationplusbasic. */
  public String permplustermvectorfile() { return permplustermvectorfile; }      
  
  private PositionalMethod positionalmethod = PositionalMethod.BASIC;
  /** Method used for positional indexing. */
  public PositionalMethod positionalmethod() { return positionalmethod; }
  
  private String stoplistfile = "";
  /** Path to file containing stopwords, one word per line, no default value. */
  public String stoplistfile() { return stoplistfile; }

  private String startlistfile = "";
  /** Path to file containing startwords, to be indexed always, no default value. */
  public String getStartlistfile() { return startlistfile; }
  
  private String luceneindexpath = "";
  /** Path to a Lucene index. Must contain term position information for positional applications,
   * See {@link BuildPositionalIndex}.
   */
  public String luceneindexpath() { return luceneindexpath; }
  
  private String initialtermvectors = "";
  /** If set, use the vectors in this file for initialization instead of new random vectors. */
  public String initialtermvectors() { return initialtermvectors; }

  private String initialdocumentvectors = "";
  /** If set, use the vectors in this file for initialization instead of new random vectors. */
  public String initialdocumentvectors() { return initialdocumentvectors; }

  private DocIndexingStrategy docindexing = DocIndexingStrategy.INMEMORY;
  /** Memory management method used for indexing documents. */
  public DocIndexingStrategy docindexing() { return docindexing; }

  private VectorLookupSyntax vectorlookupsyntax = VectorLookupSyntax.EXACTMATCH;
  /** Method used for looking up vectors in a vector store, default value {@link VectorLookupSyntax#EXACTMATCH}. */
  public VectorLookupSyntax vectorlookupsyntax() { return vectorlookupsyntax; }

  private boolean matchcase = false;
  /** If true, matching of query terms is case-sensitive; otherwise case-insensitive, default false. */
  public boolean matchcase() { return matchcase; }

  private String batchcompareseparator = "\\|";
  /** Separator for documents on a single line in batch comparison mode, default '\\|' (as a regular expression for '|'). */
  public String batchcompareseparator() { return batchcompareseparator; }

  private boolean suppressnegatedqueries = false;
  /** If true, suppress checking for the query negation token which indicates subsequent terms are to be negated when comparing terms, default false.
   * If this is set to {@code true}, all terms are treated as positive. */
  public boolean suppressnegatedqueries() { return suppressnegatedqueries; }

  private String[] contentsfields = {"contents"};
  /** Fields to be indexed for their contents, e.g., "title,description,notes", default "contents". */
  public String[] contentsfields() { return contentsfields; }
  /** Set contentsfields (e.g. to specify for TermFilter **/
  public void setContentsfields(String[] contentsfields) {
		this.contentsfields = contentsfields;
	}

  
  private String docidfield = "path";
  /** Field used by Lucene to record the identifier for each document, default "path". */
  public String docidfield() { return docidfield; }
  
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
          argc += 2;
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
            @SuppressWarnings({ "rawtypes", "unchecked" })
            Class<Enum> className = (Class<Enum>) field.getType();
            try {
              field.set(flagConfig, Enum.valueOf(className, args[argc + 1].toUpperCase()));
            } catch (IllegalArgumentException e) {
              VerbatimLogger.warning(String.format(
                  "Accepted values for '-%s' are:\n%s%n",
                  field.getName(), Arrays.asList(className.getEnumConstants())));
              throw e;
            }
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
   * <ul><li>If {@link #vectortype()} is {@code binary}, {@link #dimension()} is a multiple of 64,
   * or is increased to be become a multiple of 64.  {@link #seedlength()} is set to be half this
   * number.</li>
   * </ul>
   */
  private void makeFlagsCompatible() {
    if (vectortype == VectorType.BINARY) {
      // Impose "multiple-of-64" constraint, to facilitate permutation of 64-bit chunks.
      if (dimension % 64 != 0) {
        dimension = (1 + (dimension / 64)) * 64;
        logger.fine("For performance reasons, dimensions for binary vectors must be a mutliple "
            + "of 64. Flags.dimension set to: " + dimension + ".");
      }
      // Impose "balanced binary vectors" constraint, to facilitate reasonable voting.
      if (seedlength != dimension / 2) {
        seedlength = dimension / 2;
        logger.fine("Binary vectors must be generated with a balanced number of zeros and ones."
            + " FlagConfig.seedlength set to: " + seedlength + ".");
      }
    }
    
    if (searchvectorfile.isEmpty()) searchvectorfile = queryvectorfile;
    
    // This is a potentially dangerous pattern! An alternative would be to make this setting
    // part of each real vector, as with complex Modes. But they aren't so nice either.
    // Let's avoid getting too committed to either approach and refactor at will.
    // dwiddows, 2013-09-27.
    if (vectortype == VectorType.REAL && realbindmethod == RealVector.RealBindMethod.PERMUTATION) {
      RealVector.setBindType(RealVector.RealBindMethod.PERMUTATION);
    }
  }

}
