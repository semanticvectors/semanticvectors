# Positional Indexes #

The basic SemanticVectors indexes do not take into account word positions within a document: they are just created from a basic term document matrix.

Positional indexes, by contrast, iterated over the contents of a document and index terms according to which other terms they occur near. This "sliding context window" approach was pioneered by Hinrich Schutze in the Infomap Project, and by Lund and Burgess in developing the [HAL model](http://hal.ucr.edu/Papers.html) (Hyperspace Analogue to Language).

Methods of positional indexing that take word-order and direction into account have become more sophisticated and successful in recent years. See PermutationSearch for some more options.

## Building Positional Indexes ##

The process is similar to that for building the basic semantic vector indexes (see InstallationInstructions). There are two stages: building a Lucene index, and building a SemanticVector index from this. However, each stage is slightly different from the basic approach.

  1. To build the initial Lucene index, arrange your files in a directory as before and use the command `java pitt.search.lucene.IndexFilePositions [PATH_TO_FILES]`. This will (by default) create a Lucene index _with positional offset data_ in the directory `index`.
  1. To build a semantic vectors index from this, use the command `java pitt.search.semanticvectors.BuildPositionalIndex` just as you would use the command `java pitt.search.semanticvectors.BuildIndex`. The only difference is that you can give an extra `-windowradius` argument giving the size of the window to consider, and that the program by default outputs a file called `termtermvectors.bin`, to distinguish this from the standard `termvectors.bin`. You can change this location using the `-termtermvectorsfile` argument.

  * The `-windowradius` argument specifies the size of a symmetrical sliding context window, including the focus term at its center. For example, `-windowradius 1` will use as a context a sliding window of radius 1, i.e., including the focus term and the terms immediately to the left and right of it.

## Pros and Cons ##

So far, both positional and bag-of-words indexes have produced different and interesting results (some of which we may get round to documenting here).

The process for building positional vector indexes is longer than that for building basic indexes: the time complexity for basic indexes scales in the length of the corpus, whereas the complexity for positional indexes scales in the length of the corpus and the size of the context window.

Lucene positional indexes (as built using `IndexFilePositions` can also be used to build basic SemanticVector indexes, so they are more flexible in general. However, they do take more space on disk.
