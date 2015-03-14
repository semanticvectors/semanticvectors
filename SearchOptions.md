# Semantic Vectors Search Options #

As the SemanticVectors package grows more sophisticated, the options for building and searching semantic vector indexes have grown more complex. The command line interface for all of these options is still all through [Search.java](http://semanticvectors.googlecode.com/svn/javadoc/latest-stable/pitt/search/semanticvectors/Search.html), which is by now quite complex but still (we hope) fairly usable and  maintainable.

The purpose of this Wiki page is to document some of the search options in a slightly friendlier fashion: however, if any gradual discrepancies arise between this Wiki page and the code / javadoc in the svn repository, the svn repository should be regarded as more authoritative.

## Basic Searching ##

Searching is performed using the command `java pitt.search.semanticvectors.Search QUERYARGS`, as documented in the InstallationInstructions.

Search options are principally configured using command-line arguments. See CommandLineFlags for details and FlagConfig for a complete list of flags.

If none of the special command line arguments are given, the default behavior is to presume that the arguments given are all query terms to be looked up in a vector file called `termvectors.bin`. The query vector will be produced by adding up the vectors for all query terms, and the search will be performed using the cosine similarity measure.

Several other options are available: these fall broadly into the categories of file arguments (where to find the vectors and what formats to expect), search types (how to combine several terms into a single query expression), and query terms (which terms to look up and use as query terms).

The simplest way to find out more about these arguments is to run `java pitt.search.semanticvectors.Search` with no arguments, which will result in a basic usage message being written to the console. All changes to interface of Search.java should be reflected in the http://semanticvectors.googlecode.com/svn/javadoc/latest-stable/pitt/search/semanticvectors/Search.html#usage() usage function].

## Other Useful Tools and Options ##

See DocumentSearch, FilteredSearchResults, PermutationSearch, and ClusteringAndVisualization.

## File Arguments ##

The Search program needs a file to look up vectors to form the query, and a file to search through vectors to find nearest neighbors. By default, these are the same file (`termvectors.bin`), but it's sometimes useful to have different files -- for example, to use term vectors to look up nearby documents, or to use terms from one language to look up neighbors from another (see BilingualModels).

To change the file from which the queries are built, use the `-q` option. to change the file from which search results are found, use the `-s` option.

See also VectorStoreFormats for a description of the formats that are supported for reading vectors from disk.

## Search Type Arguments ##

The available search types are chosen using the `-searchtype` argument. The available search types are listed in an enumeration and documented at http://semanticvectors.googlecode.com/svn/javadoc/latest-stable/pitt/search/semanticvectors/Search.html

Most of these options correspond directly to implementations of the
[VectorSearcher](http://semanticvectors.googlecode.com/svn/javadoc/latest-stable/pitt/search/semanticvectors/VectorSearcher.html) class.

Note that the options (like all command line options at the moment) are case insensitive.

All of the examples below are generated from the default `termvectors.bin` file derived from the King James Bible corpus.

### SUM ###
Default option - build a query by adding together (weighted) vectors for each of the query terms, and search using cosine similarity.

Example:

```
$ java pitt.search.semanticvectors.Search -searchtype sum abraham isaac
Opening query vector store from file: termvectors.bin
Dimensions = 200
Searching term vectors, searchtype SUM ... Search output follows ...
0.8739137:abraham
0.8739133:isaac
0.57702935:rebekah
0.5297739:bethuel
0.4821766:digged
0.4661227:gerar
...
```


### SPARSESUM ###
Build a query as with SUM option, but quantize to sparse vectors before taking scalar product at search time.

Example:
```
$ java pitt.search.semanticvectors.Search -searchtype sparsesum abraham isaac
Opening query vector store from file: termvectors.bin
Dimensions = 200
Searching term vectors, searchtype SPARSESUM ... Search output follows ...
16.0:abraham
15.0:isaac
10.0:bethuel
10.0:rebekah
8.0:room
8.0:stopped
...
```

(Careful readers may note that these are scalar products, not cosine similarities, i.e., the scores are not normalized. This is another story, feel free to write to the group if you're interested.)

### SUBSPACE ###
"Quantum disjunction" - get vectors for each query term, create a representation for the subspace spanned by these vectors, and score by measuring cosine similarity with this subspace.

Example:
```
$ java pitt.search.semanticvectors.Search -searchtype subspace abraham isaac
Opening query vector store from file: termvectors.bin
Dimensions = 200
Searching term vectors, searchtype SUBSPACE ... Search output follows ...
1.3770361:isaac
1.0000002:abraham
0.89358807:rebekah
0.73906654:gerar
0.7289439:digged
0.7102588:bethuel
...
```

Careful readers will note that there is something wrong with these scores - they should never go above 1. I have been unable to track down the source of this problem, the unit tests for the orthogonalization VectorUtils all work just fine. Any help with this problem would be much appreciated.

### MAXSIM ###
"Closest disjunction" - get vectors for each query term, score by measuring distance to each term and taking the minimum.

Example:
```
$ java pitt.search.semanticvectors.Search -searchtype maxsim abraham isaac
Opening query vector store from file: termvectors.bin
Dimensions = 200
Searching term vectors, searchtype MAXSIM ... Search output follows ...
1.0000002:isaac
1.0:abraham
0.6413876:sarah
0.64067477:rebekah
0.5391283:gerar
0.51310706:digged
...
```

### PERMUTATION ###
See PermutationSearch.

### PRINTQUERY ###
Build an additive query vector (as with SUM and print out the query vector for debugging.