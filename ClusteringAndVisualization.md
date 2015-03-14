## Clustering ##

A basic k-means clustering algorithm is built into SemanticVectors and can be used at the command line using the ClusterResults class. The `-numclusters` flag controls the number of clusters produced, and the `-numsearchresults` flag controls the number of search results that are clustered.

For example, clustering the results for the term "pharaoh" often leads to some clusters that are clearly about the Pharaoh in the Joseph story in Genesis. and some clusters that are more clearly about the Pharaoh in the Moses story in Exodus.

```
java pitt.search.semanticvectors.ClusterResults -numclusters 2 -numsearchresults 30 pharaoh
Cluster 0
thin vestures badness fatfleshed zaphnathpaaneah meadow leanfleshed potipherah asenath favoured blasted kine stalk discreet interpret ill famine dearth awoke plenteousness joseph 
Cluster 1
pharaoh egypt magicians activity morever pilgrimage hardened ponds egyptians 
```

You can also cluster an entire vector store using the ClusterVectorStore class, for example in the Bible demo, the command `java pitt.search.semanticvectors.ClusterVectorStore -numclusters 20 docvectors.bin` gave several clusters including the following:

```
Cluster 14
bible_chapters/Ezra/Chapter_8
bible_chapters/Ezra/Chapter_3
bible_chapters/Nehemiah/Chapter_12
bible_chapters/Nehemiah/Chapter_10
bible_chapters/Ezra/Chapter_10
bible_chapters/1_Chronicles/Chapter_26
bible_chapters/1_Chronicles/Chapter_25
bible_chapters/1_Chronicles/Chapter_24
bible_chapters/1_Chronicles/Chapter_23
bible_chapters/1_Chronicles/Chapter_15
bible_chapters/2_Chronicles/Chapter_5
bible_chapters/2_Chronicles/Chapter_35
bible_chapters/2_Chronicles/Chapter_31
bible_chapters/2_Chronicles/Chapter_29
```

This is an interesting result since many scholars believe that the books of Chronicles, Ezra and Nehemiah were written by the same author or community.

Clustering a large vector store could take a long time, so use with care.

Note that the clustering algorithm is randomly initialized. It terminates when it has reached a stable state, but this state will not always be the same in each clustering run, so your results may vary.

---

## Visualization ##

There is also a basic visualization routine, implementing some of the work described in [Visualisation Techniques for Analysing Meaning](http://www.springerlink.com/content/148mwqt898j1vue9/) and elsewhere.

  1. Build some vector indexes using `BuildIndex` or an alternative.
  1. In the directory with your vector indexes: `java pitt.search.semanticvectors.viz.PrincipalComponents $ARGS`.
    * `$ARGS` includes first regular semantic vectors flags, e.g., `-queryvectorfile` and `-numsearchresults`, followed by query terms.

Results should look something like the following:

<img src='https://semanticvectors.googlecode.com/svn/wiki/images/pharaoh-wordplot.jpg' title='Pharoah and related words' />

This routine can also be used to generate TeX output (currently this needs to be done by setting PRINT\_TEX\_OUTPUT to true in Plot2dVectors.java and recompiling).

Note that some queries have been found to give degenerate results, all in the top left hand corner or down the left hand side. See [Issue 39](http://code.google.com/p/semanticvectors/issues/detail?id=39).