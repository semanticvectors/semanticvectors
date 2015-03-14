## Vector Store Formats ##

As of version 1.6, the SemanticVectors package has the option of storing vectors on disk in plain text and optimized Lucene formats.

As of version 3.0, the package supports serialization of real, complex, and binary vectors (see TypedVectors). Thus there is no single "vector format", instead each vector type is responsible for implementing serialization methods for writing and reading vectors. (See the [Vector](https://code.google.com/p/semanticvectors/source/browse/trunk/src/pitt/search/semanticvectors/vectors/Vector.java) interface.)

Which format to use when building new indexes or reading existing ones is configured using the `-indexfileformat` flag, e.g.,`java pitt.search.semanticvectors.BuildIndex -vectortype complex -indexfileformat text positional_index/`.

### Optimized Lucene Format ###

Created by the [VectorStoreWriter](http://semanticvectors.googlecode.com/svn/javadoc/latest-stable/pitt/search/semanticvectors/VectorStoreWriter.html) class, this is the default used by the SemanticVectors package. Lucene's I/O routines are very fast, and the indexes are quite small (e.g., the termvectors.bin index for the King James Bible corpus is ~2.8M).

The format is read by the [VectorStoreReader](http://semanticvectors.googlecode.com/svn/javadoc/latest-stable/pitt/search/semanticvectors/VectorStoreReader.html) class

### Plain Text Format ###

Also created by the [VectorStoreWriter](http://semanticvectors.googlecode.com/svn/javadoc/latest-stable/pitt/search/semanticvectors/VectorStoreWriter.html) class, this was implemented mainly to enable vector stores to be input from and output to a plain text format for exchanging data with other environments (e.g., sending SemanticVectors data out for analysis in Matlab).

For real-valued vectors, each vector is output on a single line, of the form
`string_object_name|num_1|num_2|...|num_n`
where `n` is the number of dimensions stored in `ObjectVector.vecLength`.

The plain text indexes are bigger (e.g., the index for the bible termvectors is ~6.7M) and noticeably slower for searching. Nonetheless, they are read using the [VectorStoreReaderText](http://semanticvectors.googlecode.com/svn/javadoc/latest-stable/pitt/search/semanticvectors/VectorStoreReaderText.html) class which implements the [VectorStore](http://semanticvectors.googlecode.com/svn/javadoc/latest-stable/pitt/search/semanticvectors/VectorStore.html) interface, so they can be used just like the optimized Lucene indexes.
However, if you care about space and time, you'll probably want to translate such indexes using the translation tool described below.

### Header Information ###

Both formats support a header line (in the appropriate format) that expresses the number of dimensions used by the vectors in the store, e.g., the plain text format contains a header row saying
`-dimensions $n -vectortype $TYPE`
(the flag `-` is used in front of the words `dimensions` and `vectortype` as an escape character, so that the header lines are parsed in exactly the same way as CommandLineFlags).

As of version 3.0, SemanticVectors cannot read files without this header information.

### Translating Between Formats ###

SemanticVectors version 1.6 shipped with a [VectorStoreTranslater](http://semanticvectors.googlecode.com/svn/javadoc/latest-stable/pitt/search/semanticvectors/VectorStoreTranslater.html) which enables users to translate between the two formats above.
This is invoked with the command
`java pitt.search.semanticvectors.VectorStoreTranslater -OPTION infile outfile`
where -OPTION is currently one of `-lucenetotext` and `-texttolucene`.