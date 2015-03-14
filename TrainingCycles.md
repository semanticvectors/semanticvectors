The basic way in which SemanticVectors builds models is:
  1. Create basic random vectors for each document.
  1. Create term vectors by summing the basic document vectors the term occurs in.
  1. Create new document vectors by summing the term vectors of the terms that occur in each document.

The point of training cycles is that the output of stage 3 can be fed back into stage 2 - we can use the computed document vectors as the basic document vectors for computing term vectors.

To take advantage of this functionality, use the `-trainingcycles` option to BuildIndex, e.g., `java pitt.search.semanticvectors.BuildIndex -trainingcycles 10 <LUCENE_INDEX>`.

So far, this option only exists for the default indexes - we haven't implemented it yet for PositionalIndexes, PermutationSearch, or BilingualModels.

**Reflective Random Indexing**
Reflective Random Indexing (RRI) is a generalization of the approach detailed above. Unlike the original term-document based implementation of Random Indexing, RRI is able to find meaningful connections between terms that do not co-occur together in any document in a corpus, and consequently is of interest for information retrieval and literature-based knowledge discovery. RRI can either be term-based (TRRI) such that a set of elemental (basic random) vectors are created for each term, or document-based (DRRI), such that the starting point is a set of elemental (basic random) document vectors. In both cases, cyclical training leads to meaningful associations between terms that do not co-occur.

These models can be generated using Semantic Vectors as follows:

**TRRI:**
`java pitt.search.semanticvectors.BuildIndex -trainingcycles 2 -initialtermvectors random [Lucene index location]`

**DRRI:**
`java pitt.search.semanticvectors.BuildIndex -trainingcycles 2 [Lucene index location]`

For larger corpora, the using `-docindexing incremental` flag will avoid retaining document vectors in RAM while performing this process.

For further details on RRI, please see Reflective Random Indexing and Indirect Inference: A Scalable Method for Discovery of Implicit Connections. Cohen T, Schvaneveldt, R. Widdows D. J Biomed Inform. 2010 Apr;43(2):240-56.

