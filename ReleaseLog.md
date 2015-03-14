Even-numbered (after the decimal point) versions are releases. Odd-numbered versions are in development. It follows that the most recent code and the `build.xml` file in the SVN repository is always odd-numbered.

### Version 5.8 ###

  * Upgrade to Lucene 5.0.0.
    * Includes changing OpenBitSet to FixedBitSet in BinaryVector.
  * Change to doc vectors to make sure that we output zero vectors rather than no vectors for empty documents (e.g., all stop words).
  * Some flag consistency changes.
  * Set default -termweight to IDF.
  * Internal change to use doc path rather than Lucene doc int ID as key throughout.

### Version 5.6 ###

  * Addition of Tabular datasets indexing (prototype).
  * Updated to Lucene 4.10.1 dependency.
  * Various term-weighting improvements for PSI.
  * Specific updates to LSA to remove Lucene-compatibility problems.
  * Enhancements to S(X), E(X) usage in PSI-searching interface.

### Version 5.4 ###

Small incremental release to include IncrementalTermVectors training without precomputed document vectors.

### Version 5.2 ###

Main change is using maven instead of ant as primary build and deployment system. See http://search.maven.org/#browse%7C551814572.

This has several implications, now documented in GettingStarted and InstallationInstructions, but these may need checking and improving.

Other changes include:
  * Factoring out the creation of elemental vectors and introducing the new [`-elementalmethod`](http://semanticvectors.googlecode.com/svn/javadoc/latest-stable/pitt/search/semanticvectors/FlagConfig.html#elementalmethod()) flag.
  * Proper implementation of real PSI, thanks to use of ParallelColt for convolution binding operator.

### Version 4.0 ###

  * Upgraded to Lucene 4.3.1.
    * This means that installation requires more lucene jars in classpath.
  * Release of OrthographicVectors.
  * Deterministic probabilistic binary normalization.
  * Fixes to bilingual search, incremental doc vectors.

### Version 3.8 ###

  * Flag config changed to an instance rather than global variables.
    * Deliberately few user-visible changes. One main change is that index-building routines should set `-luceneindexpath FOO` explicitly, rather than just writing (say) `BuildIndex FOO`.
    * Many other improvements / simplifications to individual flags.
  * APIs updated to work with Lucene 3.6.x. This change is not backward-compatible.
  * Significant speedup to search calls for large number of results. (With thanks to Christian Prokopp.)
  * Release of deterministic pseudo-random vectors. (With thanks to Manuel Wahle.)

### Version 3.6 ###

  * Addition of SemanticVectorsCollider, used in experiments that measure frequency of random vectors overlapping over different fields and dimensions.
  * Several improvements to PSI, notably the support for `S(united_states)*S(mexico)*E(dollar)` type notation for queries including subspace queries.
  * Added support for idf termweighting in LSA.
  * Changed negation term from `NOT` to `~NOT`, and fixed tests accordingly.
  * [Changes](https://code.google.com/p/semanticvectors/source/detail?r=721) to make complex normalization and overlap measure more circular than Cartesian in nature.
  * Fixes to complete systematic use of `.bin` and `.txt` extensions as appropriate for vector store file names.
  * Added sorting to the GetAllAboveThreshold method, such that results are returned in order.

### Version 3.4 ###

Main addition is PredicationBasedSemanticIndexing (PSI). Works with real, complex, and binary numbers. This is a big advance, thanks again to Trevor Cohen.

Also (related) introduced better support for binary orthogonalization, binding and bundling operations on complex vectors.

Several other flags, tweaks, and options, including:

  * `-docidfield` can now be set to fields other than `path`. (Fixes [issue 53](https://code.google.com/p/semanticvectors/issues/detail?id=53).)
  * `idf` is an option for `-termweight` (and seems to perform particularly well for complex directional indexing).
  * SVD automatically cuts `-dimension` to the number of documents if initially greater, fixing reported bug.
  * All hardcoded filenames replaced with flag-configured variables, and extensions `.bin` and `.txt` added automatically depending on      `-indexfileformat`.
  * Several minor improvements to integration tests, particularly improving some previsou Windows-related bugs.

### Version 3.2 ###

Fixed support for LSA with real typed vectors. (LSA is still not supported with complex and binary vectors.)

Several other small consistency fixes and progress with vector binding. Thanks to several contributors here.

### Version 3.0 ###

The major version update is TypedVectors. Set `-vectortype` to real, complex, or binary to build vector stores with vectors taking values over different ground fields. Search processes automatically parse the vector type and dimension from the header line in the vector store file. Vector store files without header will no longer be supported.

Introduced flags `-stdev` to report result scores as standard deviations from mean result, and `-searchresultsminscore` to reduce results to only those with scores above a certain threshold.

Introduced a verbatim logger class, which reduces a lot of logging verbosity while still using the java logging framework rather than printing lof messages directly to stdout or stderr.

### Version 2.4 ###

Support for FilteredSearchResults.

Support for Porter Stemming in the pitt.search.Lucene package (thanks particularly to Siddhartha Jonnalagadda).

Set minimum term weights to be 1 rather than 0 for strings not recognized by Lucene index.

### Version 2.2 ###

Bug fixes and refactoring improvements.

Fixed VectorStoreTranslater bug.

Fixed docid parsing bug in TRRI implementation.

Fixed missing includes of thirdparty files.

Plenty of small refactorings to pass dimensions as arguments, and doing work in clearer factory methods more consistently.

### Version 2.0 ###

Updated to Lucene 3.0.3. (Fixes [issue 34](https://code.google.com/p/semanticvectors/issues/detail?id=34).)

Moved javadocs to latest-stable directory.

Removed lowercasing of flag-values.

Fixed out-of-bounds error in BuildPositionalIndex. (Fixes [issue 28](https://code.google.com/p/semanticvectors/issues/detail?id=28).)

Using Java logging instead of printing logs to stderr. Search results still printed to stdout.

Several internal, procedural and testing improvements.
- Better type safety.
- Tests refactored into unit tests and integration tests; unit tests work in Eclipse.
- Some better exception handling instead of crashing!
- Removed redundant JAMA dependency.
- Build file gives more thorough options for managing dependencies and classpaths.

### Version 1.30 ###

Updated to Lucene 3.0 and 3.0.1. No attempt to maintain backwards compatibility with earlier Lucene versions. (Fixes [issue 18](https://code.google.com/p/semanticvectors/issues/detail?id=18).)

Added thread safety for reading. (Fixes [issue 21](https://code.google.com/p/semanticvectors/issues/detail?id=21).)

Better support for command line flags for setting Lucene fields. (Fixes [issue 22](https://code.google.com/p/semanticvectors/issues/detail?id=22).)

Added LSA / SVD support, though not fully integrated into standard options yet.

### Version 1.26 ###

Small fix necessary to fix [bug 19](https://code.google.com/p/semanticvectors/issues/detail?id=9) (http://code.google.com/p/semanticvectors/issues/detail?id), another Lucene 2.9 compatibility issue.

### Version 1.24 ###

Small fix to maintain compatibility with Lucene 2.9.

### Version 1.22 ###

Added [BalancedPermutationVectorSearcher](http://semanticvectors.googlecode.com/svn/trunk/doc/pitt/search/semanticvectors/VectorSearcher.BalancedVectorSearcherPerm.html) to use maximum score from two possible
directions (random index as cue for permuted vectors or vice versa) of search.
When combined with term weighting (as occurs when -indexFilePath is used with
Search) this seems to produce reasonable results with reciprocity.

Added [CompareTermsBatch](http://semanticvectors.googlecode.com/svn/trunk/doc/pitt/search/semanticvectors/CompareTermsBatch.html) and related modifications to Flags, and the convention that normalizing the zero vector returns zero. Thanks to Andrew Mackinlay for these enhancements.

Committed implementations of TRRI and RRI as per the Reflective Random Indexing paper (to appear).

Fixed "-numsearchresults" argument which was being ignored by Search. Fixed lowercasing problem for negation. Added "-contentsfields" and "-docidfield" arguments, which should give better support for SOLR users and others who use a variety of Lucene fields.

### Version 1.20 ###

Main improvement - new Flags.java for handling all command line flags and command line argument parsing. Still could do with improvements and better documentation, but much better than our previous ad hoc parsing in each class with a main() method.

One positive side effect is that some flags that were unnecessarily specific now work more generally, e.g., regex search works for all vector store clients, not just in [CompareTerms](http://semanticvectors.googlecode.com/svn/trunk/doc/pitt/search/semanticvectors/CompareTerms.html).

Addition of balanced\_permutation search.

Fixed some bugs in bilingual indexing.

### Version 1.18 ###

Added regex query generation to [CompareTerms](http://semanticvectors.googlecode.com/svn/trunk/doc/pitt/search/semanticvectors/CompareTerms.html). Not added to main Search yet but would be easy to add here too.

Added clusterOverlapMeasure to [ClusterVectorStore](http://semanticvectors.googlecode.com/svn/trunk/doc/pitt/search/semanticvectors/ClusterVectorStore.html). Only works for Bible chapters so far, would be easy to extend to other data.

Added build support for tests and visualization utils (these do not ship with main distribution because of Jama and JUnit dependencies).

Term filter moved to [LuceneUtils](http://semanticvectors.googlecode.com/svn/trunk/doc/pitt/search/semanticvectors/LuceneUtils.html). (thanks to Vidya Vasuki). Added support for setting the number of non-alphabetic characters that can appear in a term.

Some other minor bug-fixes (see commit log, revisions 190-228).

Added some additional methods to support pre/re-training of incremental vectors.

Added cyclical retraining, and the ability to start with a set of trained term
vectors (command line option -pt) to allow for combining vector stores in
interesting ways.

Improved VectorStore implementation to close vector stores properly.

### Version 1.16 ###

Trevor found and fixed a bug in the permutation indexing code. Dominic took the opportunity to add more conformant usage statements and exception handling for [ClusterVectorStore](http://semanticvectors.googlecode.com/svn/trunk/doc/pitt/search/semanticvectors/ClusterVectorStore.html).

PermutationSearch is really cool, give it a try.

### Version 1.14 ###

Main feature addition is that of permutation and directional indexing and search (see PermutationSearch). This is a new method for encoding and decoding word order in vector space models, introduced by [Sahlgren et al (2008)](http://www.sics.se/~mange/papers/permutationsCogSci08.pdf). Thanks to Trevor Cohen for the implementation in SemanticVectors.

Other work has been to improve exception handling so that SemanticVectors can be used as a library by other Java applications, in particular [ActiveMath](http://www.activemath.org). SemanticVectors now has no hard-coded System.exit() calls, but throws standard exceptions and one new ZeroVectorException. Thanks to Dominik Jednoralski for leading this effort.

### Version 1.12 ###

**Indexing Improvements**

Added IncrementalDocVectors, which enables document vectors to be built for large collections whose document vectors won't fit in memory.

Added support for training cycles, which enables vectors to be learned in several iterations by substituting learned vectors for initial random term or document vectors.

**Search Improvements**

Added support for -results command line flag; behavior consistent with similar usage in ClusterResults.

Added much more documentation for search options.

**Infrastructure Improvements**

Added more vector store options (including VectorStoreSparseRAM) and refactored vector building classes to be able to use different kinds of basic vector stores as appropriate.

Added VectorUtils for converting between sparse (short[.md](.md)) and full (float[.md](.md)) vectors.

### Version 1.10 ###

2008-05-27

Mainly a cleanup of version 1.8 - some javadoc and classnames were improved.

Also added some extra weighting options in search to use Lucene utils to demote results from terms with low inverse document frequency.

Also added a routine to cluster all vectors in a particular datastore (rather than relying on results to have been generated by searching).

### Version 1.8 ###

2008-05-19

As well as the 1.7 changes described below, PositionalIndexes (HAL / context window) functionality was added by Trevor Cohen.

### Version 1.7 (devel) ###

Many changes have been made, initially for the Oxford presentation and SemanticVectors paper, and clustering for the UPitt collaboration.

  1. `VectorSearcher.java` and `Search.java` classes have been significantly refactored.
    1. The separate `VectorSearcher` implementations (Sum, Tensor, etc.) now do a lot more of the query parsing and initialization work.
    1. `VectorSearcher.getScore()` no longer takes a `queryVector` and `testVector`: the query statements are now built into the state of the `VectorSearcher`, and only a `testVector` is needed for `getScore()`.
    1. A command-line option `-searchtype` enables the user to state a value which tells `Search.java` which kind of VectorSearcher to create.
    1. The Tensor and Convolution options take their training relations as tilde separated pairs, e.g., `java pitt.search.semanticvectors.Search -searchtype tensor abraham~sarah joseph~mary ahab`. (Ideally this would give the top result `jezebel`, but doesn't ...)
  1. Clustering functionality is provided by `ClusterResults.java`. Clustering is done after search, there is as yet no interface for providing a list of terms to get vectors for and cluster, though this would be easy to do.
  1. Also, a `BuildModel` class has been added - I'd like to deprecate `BuildIndex` in favour of `BuildModel` to avoid confusion with Lucene indexing.

### Release 1.6 ###

2008-03-18

  1. Bilingual models implemented - see BilingualModels.
  1. Plain text interchange format and tools implemented - see VectorStoreFormats.
  1. `build.xml` changed so that the source distributions ship with just `src/` directory and build file: the rest is automatically generated and there seems to be no good reason for taking any more bandwidth.
  1. Beginnings of a testing suite have been created. Tests don't ship with the main package, and should be checked out by developers from svn.

### Release 1.4 ###

2008-02-08

Two main improvements.

  1. Setting of dimensions, seedlength and minimum term frequency all done using command line arguments (see `BuildIndex`). Number of dimensions is now stored in a small header for vector files created by `VectorStoreWriter`, and this is read by `VectorStoreReader`. If absent, `VectorStoreReader` tries the default value (200) or failing that, dies and tells you to rebuild indexes. So there is some backwards compatibility with indexes made with older versions of the software, but some indexes will need to be rebuilt. Thanks to Trevor Cohen for most of these improvements.

  1. Query building code is now equipped with negation, supporting the same functionality as Infomap-NLP. `CompoundQueryBuilder` parses queries and sees if they have a "NOT" term in them, `VectorUtils` has the function for orthogonalizing these query sets.

### Release 1.2 ###

2008-01-30

The main improvement is the redesign of `TermVectorsFromLucene` to use a sparse representation for the basic document vectors (`basicDocVectors`). These are now represented by arrays of short int values which list the non-zero +1 and -1 entries in a `basicDocVector`. The term vector building routine in the main constructor, and the `generateRandomVector` method, have been refactored accordingly.

This change gives a major reduction in memory requirements, in one practical example reducing the RAM requirement from 7.3G to 1.1G for the corpus. It also speeds things up
further and should maintain the same memory requirement regardless of
vector length as long as seed length stays constant. Many thanks to Trevor Cohen for these  improvements.

Unfortunately Dominic accidentally left an old copy of `TermVectorsFromLucene.java` in the main directory from which this version was bundled, and only noticed this inclusion after the package was shipped. Please ignore / delete this file.

### Release 1.0 ###

2007-11-29

The main new feature is the refactoring of the search code to use an abstract `VectorSearcher` class. This implements the main nearest neighbor search, but leaves the scoring function unspecified. This enables developers to quickly test new scoring methods.

Scoring methods implemented so far include cosine similarity and two product similarities, tensor and convolution. (This has led us to realize some features of tensor product similarity which should have been mathematically obvious.)

### Release 0.8.4 ###

2007-10-30

At the request of one research user, added the `CompareTerms` class and command line interface to enable term-term comparison. Also refactored query vector generation, adding the `CompoundVectorBuilder` class which both `Search` and `CompareTerms` use.

Also improved javadoc.

User reported that everything worked - again good news.

### Release 0.8.2 ###

2007-10-25

This was the first public release of the software.
There were about a dozen downloads, and so far so good - no bug reports at all!