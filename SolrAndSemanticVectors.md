# Some pointers on using SemanticVectors with SOLR #

(This article is a stub so far.)

Update for 2014: some users report problems using SV 5.4 with a SOLR installation using Lucene 4.6.0. This is a problem: see
https://groups.google.com/forum/#!topic/semanticvectors/DJAlN6O84h0, and please chime in if you know of an updated solution.

[Apache SOLR](http://lucene.apache.org/solr/) is an enterprise server search implementation based on the Lucene platform.

Some users have reported difficulty in using SemanticVectors in combination with SOLR. I haven't worked with SOLR directly, but one of the main problems seems to be defining which Lucene fields to index. Most SOLR indexes don't come with the `contents` field that SemanticVectors indexes by default, and the `path` field that is used as a document identification string.

The current solution for this is to use the `contentsfields` and `docidfield` command line flags, e.g., `-contentsfields contents1,contents2 -docidfield mydocid`. These options were released in SemanticVectors version 1.22 and are available in all subsequent versions.