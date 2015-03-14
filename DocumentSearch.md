# Document Search in SemanticVectors #

## Command-Line Search ##

It's easy to search for documents in SemanticVectors, by telling the search program to use an appropriate vector store. This means that there is no special class or interface for searching documents - this functionality is provided by enabling the more general freedom to take query terms and search over a variety of different vector stores.

The query file is set using the `-queryvectorfile` option and the search file is set using the `-searchvectorfile` options.

### Searching for Documents using Terms ###

The default `BuildIndex` command build both term vectors (`termvectors.bin`) and document vectors (`docvectors.bin`). To search for document vectors closest to the vector for `Abraham`, you would therefore use the command:

`java pitt.search.semanticvectors.Search -queryvectorfile termvectors.bin -searchvectorfile docvectors.bin Abraham`

### Using Documents as Queries ###

You can also use the document file as a source of queries. For example, to find terms most closely related to Chapter 1 of Genesis, you'd use

`java pitt.search.semanticvectors.Search -queryvectorfile docvectors.bin -searchvectorfile termvectors.bin -matchcase bible_chapters/Genesis/Chapter_1`

There have been some reports of good performance in using SemanticVectors in this way for document keyword and tag recommendations. In most situations, effort is required to tune the process effectively. Known problems include:
  * With default settings (using random projection with `BuildIndex` and the King James Bible corpus, results appear to be pretty generic terms like "unto", "i", "them, "have".
  * With term weighting switched on using `-termweight idf` or `-termweight logentropy`, results appear to be very specific terms like "whales", "yielding", "firmament".
  * Results often look promising with traditional LSA and idf term weighting, e.g., using the King James Bible corpus:

```
$ java pitt.search.semanticvectors.LSA -termweight idf positional_index/
$ java pitt.search.semanticvectors.Search -queryvectorfile docvectors.bin -searchvectorfile termvectors.bin -matchcase "bible_chapters\Luke\Chapter_2"
Opening query vector store from file: docvectors.bin
Opening search vector store from file: termvectors.bin
Searching term vectors, searchtype sum
Found vector for 'bible_chapters\Luke\Chapter_2'
Search output follows ...
0.9894455515331493:anna
0.9894455515331493:cyrenius
0.9894455515331493:lineage
0.9894455515331493:phanuel
0.9894455515331493:pondered
0.9894455515331493:swaddling
0.9894455487129858:manger
```

For **Document to Document Search**, use commands just like those above but set `-searchvectorfile` to the same file as `-queryvectorfile`, that is, the path to the document vectors.

### Lucene Term Weighting ###

Any term weighting for documents is computed when the document vectors are created, as part of the index building process. So giving a `-luceneindexpath` argument when using documents as queries will not help you at all, and can cause SemanticVectors to discard your query terms (since, for example, `/files/file1.txt` isn't a term that the Lucene index recognizes).

### Document Filtering ###

Filtering search results based on some regular expression can be particularly useful for restricting document searches to specific parts of a corpus. See FilteredSearchResults.

### Comparing Two Documents ###

If you want a pairwise comparison score between two documents, Doc1 and Doc2, then you can use the CompareTerms tool. For documents that are already part of your index, use something like:

`java pitt.search.semanticvectors.CompareTerms -queryvectorfile docvectors.bin ./path/to/Doc1 ./path/to/Doc2`

See Deswick's comment below for a complete example.

For new documents that were not indexed when the model was built, you can still compare the vectors produced by summing the constituent terms, which is conceptually equivalent. (Note the 'conceptually' part: any differences in term-weighting, stemming, case-normalization, etc., will affect your results.) The terms in each document need to be assembled into query statements, surrounded by double quotes.

This could be done on Unix-like systems using something like:

``java pitt.search.semanticvectors.CompareTerms -queryvectorfile termvectors.bin "`cat Doc1`" "`cat Doc2`"``

Of course, if there are double quotes inside your documents, this will lead to escaping problems. Be prepared to do a certain amount of cutting, pasting, search and replace, and escaping of special characters using your favorite tools.

### Comparing Terms and Documents Explicitly ###

This question was raised in the group discussions. Can you explicitly compare a term vector and a document vector? The answer is yes, but it needs a workaround. See CompareTerms for more details.

## Programmatic / API-driven Search ##

For users wishing to incorporate document search into programmatic calls, the basic ideas are the same as above, but you have to translate them into API calls instead of command-line calls. To see an example of how to set up such a project, see ExampleClients.

For example, if you want to search for documents related to "my search terms", you might write something like the following:

```
FlagConfig config = FlagConfig.getFlagConfig( ... appropriate command-line string arguments ... );
CloseableVectorStore queryVecReader = VectorStoreReader.openVectorStore(config.termvectorsfile(), config), 
CloseableVectorStore resultsVecReader = VectorStoreReader.openVectorStore(config.docvectorsfile(), config);
LuceneUtils luceneUtils = new LuceneUtils(config.luceneindexpath()); 
VectorSearcher  vecSearcher = new VectorSearcher.VectorSearcherCosine( 
                queryVecReader, resultsVecReader, luceneUtils, config, new String[] {"my", "search", "terms"}); 
LinkedList<SearchResult> results = vecSearcher.getNearestNeighbors(maxResults);

for (SearchResult result: results) {
  System.out.println(String.format(
      "%f:%s",
      result.getScore(),
      result.getObjectVector().getObject().toString()));
}
```

In this case, this will print the paths (extracted from the index field and configured with the -docidfield flag) to the resulting documents to STDOUT.

(The -docidfield defaults to "path", which is also the default in Lucene indexes built by `org.apache.lucene.demo.IndexFiles` and `pitt.search.lucene.IndexFilePositions`, but this field name is not invariant and can easily be changed by other index-building tools.)