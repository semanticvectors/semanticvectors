# Ideas #

There are many natural ideas and suggestions for things to try with SemanticVectors. This page is meant to be a starting point. Feel free to add new ideas or comment on existing ones. Many ideas start as discussions in the [mailing list](https://groups.google.com/forum/?fromgroups#!forum/semanticvectors). Some ideas turn into engineering tasks and may eventually be tracked at [Issues](https://code.google.com/p/semanticvectors/issues/list).

## Engineering TODOs and Suggestions ##

  * Make the Maven build and deployment provide both full jars (including dependencies) and reduced jars (not containing dependencies). More details on the reasons for this can be found in [this thread](https://groups.google.com/forum/#!topic/semanticvectors/zoF5VGmBKo0).

  * Make a couple of small user interfaces for demos (Swing client, simple Web client).

These are things Dominic hopes to get round to. Any other engineer who jumps in before me and tackles any of these successfully could be paid in [LinkedIn](http://linkedin.com) endorsements, letters of reference, drinks, chocolate - you choose.

## Incremental Indexing ##

This has been requested many times for practical reasons, and is a natural scientific question to ask of many of the algorithms used to reduce dimensions, and for learning in general.

Implementing incremental index updates for semantic vectors should be relatively easy: the algorithms would be fast, but issues related to file-ownership and thread-safety could be a pain.

So far, SV indexing is fast enough for most users, and much faster than Lucene indexing (in fairness, this is because Lucene does most of the actual work!). So incremental indexing has not been prioritized yet.

On purely engineering grounds, incremental indexing should only be prioritized when rebuilding a SemanticVectors model is prohibitively time-consuming in the workflow "add incrementally to Lucene index, rebuild SemanticVectors model". If you're tempted to write to the mailing list requesting support for incremental indexing, please include these times and some information about how often you need to rebuild your indexes - this will give empirical as well as logical motivation.

Other pressing scientific motivation might arise from:
  * Dated / time varying corpora (e.g., analyzing the evolution of a news story over time).
  * Modelling narrative and discourse (e.g., a user session, events in a novel).
  * _Suggestions welcome ..._

## Ontology Modelling and Mapping ##

There are many reasons for investigating the relationships between semantic vector models and formal ontology models including taxonomies, conceptual graphs, Cyc, RDF, Wikipedia relationships, etc.

PredicationBasedSemanticIndexing results on Medline have been very promising, so extending these investigations beyond the biomedical domain is a natural step. Are the semantically valid generalizations that would enable us to model standard relationships like "is a" and "part of" as vector operators in some relatively comprehensive framework?

## Composition with Real, Complex and Binary Vectors ##

SV supports TypedVectors, but many questions about how best to implement Vector Symbolic Architectures (VSAs) over each ground field remain.

This manifests itself in several ways, including:
  * What does it mean to "scale" a binary vector by a given real number "weight"?
  * Do we need Fast Fourier Transforms (FFTs) for binding with real vectors, or is there a way to make [permutation](PermutationSearch.md) operators work appropriately?
  * Should complex vectors in cartesian and circular [mode](http://semanticvectors.googlecode.com/svn/javadoc/latest-stable/pitt/search/semanticvectors/vectors/ComplexVector.Mode.html) be separated into separate classes?

The work OrthographicVectors has exposed some of the frailties behind our existing assumptions in a very positive way, because orthographic word vectors can be combined in VSAs over different ground fields and produce markedly different results.

## Modeling Continuous Variables ##

The distributional models used in semantic vector models are by nature continuous, but are learned from discrete signals (e.g., how many times did a particular term occur in a particular document?). It should be natural to express continuous signals as well (e.g., the date, and even temperature and humidity when a document was written).

More generally, there are many areas where "records" naturally include continuous variables rather than discrete textual symbols.

Are the positional techniques used in orthographic vectors useful here?

## Tabular data ##

It's possible to create a vector for a record in a tabular dataset by binding vectors for the cell values with vectors for the column headings, and superposing these for each cell in the row. See the initial demo [here](https://code.google.com/p/semanticvectors/source/browse/#svn%2Ftrunk%2Fsrc%2Fmain%2Fjava%2Fpitt%2Fsearch%2Fsemanticvectors%2Ftables). Extending with a good model for numerical values (see continuous variables above) is a logical next step.

## Negation in PSI ##

Use orthogonal negation in PredicationBasedSemanticIndexing and evaluate the results.

## Semantic Type Inference ##

It's possible to add or infer a semantic type for concepts in PSI. See the demo in the [PSITypeLister](https://code.google.com/p/semanticvectors/source/browse/trunk/src/main/java/pitt/search/semanticvectors/experiments/PSITypeLister.java). How can these types be used for reasoning and inference?

## Introduce more decomposition, clustering, classification algorithms ##

Many algorithms could be incorporated into SV, including Latent Dirichlet Analysis (LDA), SVD for complex vectors, SVMs for binary classification.

In some cases it's unclear what this means with binary vectors, which again poses interesting mathematical research challenges.

## Compare and contrast directional / positional vector indexing with n-gram models ##

Compare results from n-gram modelling with vector positional indexing, for smoothing, computational cost, etc.

## Spelling Correction, Autosuggest, Autocomplete ##

This would be a natural application for OrthographicVectors and positional term vectors. Can these techniques be used to supplement / improve existing text suggestion / correction algorithms?