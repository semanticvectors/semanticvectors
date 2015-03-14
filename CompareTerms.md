# CompareTerms Tool #

This is a simple tool for comparing two vectors, which could be individual terms, query statements or documents. The javadoc for the API is at http://semanticvectors.googlecode.com/svn/javadoc/latest-stable/pitt/search/semanticvectors/CompareTerms.html

For terms, use something like:

`java pitt.search.semanticvectors.CompareTerms -queryvectorfile termvectors.bin term1 term2`

For several terms, use something like:

{{{java pitt.search.semanticvectors.CompareTerms -queryvectorfile termvectors.bin "term1 term2" "term3 term4"}}

This compares the sum of term1 and term2 to the sum of term3 and term4.

For document vectors, use something like:

`java pitt.search.semanticvectors.CompareTerms -queryvectorfile docvectors.bin ./path/to/Doc1 ./path/to/Doc2`

See also DocumentSearch.

## Batch Comparisons ##

If you want to assemble scores for large numbers of pairs, consider using [CompareTermsBatch](http://semanticvectors.googlecode.com/svn/javadoc/latest-stable/pitt/search/semanticvectors/CompareTermsBatch.html). This tool caches your vector store in memory, so it's much faster for large numbers of comparisons.

## Comparing Terms with Documents ##

Sometimes you may want to compare two vectors from different vector store files with the same vector type (real, complex, or binary) and the same dimension. The most common case is where you want to compare a term and a document explicitly.

CompareTerms does not support this feature explicitly at the time of writing, but here is a workaround:

  1. Build text versions of your models, e.g., using `java pitt.search.semanticvectors.BuildIndex -indexfileformat text positional_index/`
  1. Copy the `termvectors.txt` file to a new `allvectors.txt` file.
  1. Remove the header line from `docvectors.txt`, e.g., using a text editor.
  1. Concatenate this edited `docvectors.txt` file to your `allvectors.txt` file, e.g., using `cat docvectors.txt >> allvectors.txt`.
  1. Run `CompareTerms` on this `allvectors.txt` file.
    * Remembering to quote your document pathnames if necessary, and to set `-indexfileformat text`.

This should give you something like the following:

```
> java pitt.search.semanticvectors.CompareTerms -queryvectorfile allvectors.txt -indexfileformat text "bible-chapters\bible_chapters\2_Samuel\Chapter_24" david
Opening vector store from file: allvectors.txt
Found vector for 'bible-chapters\bible_chapters\2_Samuel\Chapter_24'
Found vector for 'david'
Jun 13, 2012 9:16:42 AM pitt.search.semanticvectors.CompareTerms main
INFO: Outputting similarity of "bible-chapters\bible_chapters\2_Samuel\Chapter_24" with "david" ...
0.3601597231735099
```

