# Summary #

This page contains brief instructions for installing and running the Semantic Vectors package.

These sketch instructions presume that you're reasonably familiar with Java, Ant, CLASSPATHs, etc. If not, you might struggle a bit. Better documentation will hopefully follow sometime soon.

## Prerequisites (all Installations) ##

  * You will need some basic knowledge about how to use a command-prompt (such as `cmd` in Windows, a bash shell in Unix or Linux, or the terminal app in MacOS).
    * In particular, you will need to be able to manage [environment variables](http://en.wikipedia.org/wiki/Environment_variable#Getting_and_setting_environment_variables).
  * Make sure you have a Java Development Kit (JDK) installed on your system, and that the `java` and `javac` programs can be seen from you system `PATH` variable.
    * If you type `java` and `javac` at your command line, you should see options and instructions, rather than a "Command not found" error.
    * On some systems you may need to set your `JAVA_HOME` environment variable.

### Older Installations (Versions before 5.0) ###

For versions before 5.0, you have to set up the dependencies on Lucene and other packages yourself as necessary.

  * Make sure that you have the [Apache Lucene](http://lucene.apache.org/java/docs/) core and demo jar files (and the analyzers and queryparser jar files for Lucene and SemanticVectors versions 4.0 and higher).
    * Add the `lucene-analyzers-common-$VERSION.jar`, `lucene-core-$VERSION.jar`  `lucene-demo-$VERSION.jar`, and `lucene-queryparser-$VERSION.jar` to your `CLASSPATH`, where `$VERSION` is your Lucene version.
    * For Lucene pre-4.0 (SemanticVectors pre-4.0), only Lucene core and demo jars are required. See also LuceneCompatibility.
    * To test this, run `java org.apache.lucene.demo.IndexFiles` on the directory containing the corpus from which you'd like to build a SemanticVector model. You'll need to do this anyway, so might as well do it first to check your Lucene installation.

## Binary Installation from Jar Distribution ##

This is the simplest approach for just getting SemanticVectors working.

  * Download the most recent `semanticvectors-*.*.jar` distribution from this site or from [maven central](http://search.maven.org/#browse%7C1990211094).
  * Add the semanticvectors-**.**.jar file (including full path) to your `CLASSPATH`.

You won't be able to alter the programs beyond the configuration that's possible with command line flags. If you want to do that, you need to build from source.

## Compiling from Source - Package Installation ##

  * Install maven.
  * [Checkout the project](https://code.google.com/p/semanticvectors/source/checkout) from source.
  * Run `mvn clean install`.

If you might be making changes to the code, or want to try out new features that are checked in but not yet in the numbered releases, please consider checking out the most recent version from the svn repository. If you make changes that turn out to be useful, please write to us and tell us about them, and we'll probably urge you to submit them to the repository.

If this fails or is too daunting (which it may be, there's a lot going on and getting everything to work together eventually becomes cumbersome), please don't hesitate to contact the project developers and we'll try to help out or make a new numbered release with the development features you need.

## To Build and Search a Model ##

  * Create a Lucene index using the Lucene demo, by running `java org.apache.lucene.demo.IndexFiles -docs $PATH_TO_READ_YOUR_CORPUS [-index $PATH_TO_WRITE_YOUR_INDEX]`.
    * This will create a Lucene index in the new directory given by your `-index` argument (which defaults to the name `index` if you leave this flag out).
  * Create term and document vectors by running `java pitt.search.semanticvectors.BuildIndex -luceneindexpath $INDEX_MADE_ABOVE`.
    * (In SV versions before 3.8, this path is given as the remaining command-line argument after all flags have been parsed.)
  * To search the resulting model, run `java pitt.search.semanticvectors.Search QUERYTERMS`.
  * If the upper case term `NOT` appears in your `QUERYTERMS`, the query parser will add the terms preceding the `NOT` term and negate all the terms after it. See VectorNegation.
  * To compare two concepts, run `java pitt.search.semanticvectors.CompareTerms "QUERYTERMS1" "QUERYTERMS2"`.
  * For more information on searching, see SearchOptions.
  * If you want to search for relevant documents, see DocumentSearch.

### Command-Line Flags ###

For more on configuration options, see CommandLineFlags.

## Training Cycles ##

Models can be built in several phases by passing the document vectors back to rebuild new term vectors. See TrainingCycles.

## Bilingual Models ##

For instructions on building a bilingual model from a parallel corpus, see BilingualModels.

## Positional Indexes ##

For instructions on building an index based on term positions, see PositionalIndexes.

## Permutation Search ##

Indexes can now be built that encode directional relationships between words. See PermutationSearch.

## Clustering and Visualization ##

Have some fun building clusters and pictures! Instructions are on the ClusteringAndVisualization page.

## Vector Store Formats ##

The SemanticVectors package currently (as of version 1.6) supports two different vector store formats, a plain text format and an optimized format created by the Lucene I/O packages. For more information including format translation utilities, see VectorStoreFormats.

## Developer API Docs ##

See http://semanticvectors.googlecode.com/svn/javadoc/latest-stable/index.html
Some useful information may be found in the ReleaseLog.