# Use Cases and Applications #

## Finding Related Terms ##

SemanticVectors can be used to find related terms and concepts to a target term. For example, with the index built from the King James Bible, a search for related terms to "Asher", one of the sons of Jacob, returns the names of other members of the family, with later results mixed with terms related to kinship more generally:

```
$ java pitt.search.semanticvectors.Search asher
Opening query vector store from file: termvectors
Searching term vectors, searchtype sum
Found vector for 'asher'
Search output follows ...
1.0:asher
0.9178473865092276:issachar
0.8340573512939335:naphtali
0.8094583807136287:zebulun
0.5997927054131444:simeon
0.5840854434731123:families
0.5117024109690984:tribe
0.5102583475778316:numbered
0.49488405594692364:dan
0.47865258230814256:gathrimmon
0.46671084991480544:names
...
```

Applications for such term-similarity models include Automatic Thesaurus Generation and Query Expansion.

SemanticVectors has many different term-weighting and [search options](SearchOptions.md) that enable you to find related terms using a range of features and structural relationships.

## Finding Related Documents ##

See DocumentSearch.

## Clustering and Visualization ##

Clustering and visualization can help to see groups of related concepts, and general themes across entire datasets.
[Project Thorngat](http://lab.cisti-icist.nrc-cnrc.gc.ca/cistilabswiki/index.php/Torngat1) is a great example, creating concept maps to support the search experience for users of digital libraries.

Some basic tools to get started are described on the ClusteringAndVisualization page.

## Reasoning over a Knowledge Base ##

See PredicationBasedSemanticIndexing.

### More to come ... ###