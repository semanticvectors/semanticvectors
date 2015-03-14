## Introduction ##

For those seeking an in-depth discussion of PSI, see [Reasoning with vectors: a continuous model for fast robust inference](http://jigpal.oxfordjournals.org/content/early/2014/12/03/jigpal.jzu028.short).

Predication-based Semantic Indexing, or PSI, is an application of distributional semantic techniques to reasoning and inference. This is exciting because traditionally the areas of empirical learning and deductive inference have been treated quite separately in linguistics and artificial intelligence, but in the last few years we have seen techniques from both areas working together. PSI is one such technique.

PSI starts with a collection of known facts or observations, and combines them into a single semantic vector model in which concepts and relationships are both represented. Then the usual tools for constructing query vectors and searching for results in SemanticVectors can be used to suggest new facts that have not been observed or recorded before. In some ways this is like using SemanticVectors as a kind of automated theorem prover: but it is much faster and noise-tolerant than traditional symbolic theorem provers. (Of course, one could reverse "noise-tolerant" and say that symbolic theorem provers are more "exact" than PSI: so PSI complements, it does not replace, traditional theorem proving.)

## Subject-Predicate-Object Triples or Predications ##

Start by taking a look in the small file of test data, [nationalfacts.txt](https://code.google.com/p/semanticvectors/source/browse/trunk/test/testdata/nationalfacts/nationalfacts.txt). This file contains a collection of facts about different countries, such as:

```
...
Algiers	CAPITAL_OF	 Algeria
...
Afghanistan	HAS_CURRENCY	Afghan afghani
...
Armenia	HAS_NATIONAL_ANIMAL	Eagle
...
etc.
```

This is an example of a "triple store", a way of representing individual facts that has become increasingly prominent in recent years. Each fact contains a pair of items (such as "Sukhumi" and "Abkhazia"), and a relationship that holds between them, such as "CAPITAL\_OF". The first item is sometimes called the subject, the last item is sometimes called the object, and the relation that holds between them is sometimes called the predicate. And the whole triple may be referred to as a "predication", hence "Predication Semantic Indexing".

## Semantic Vectors from Predications ##

The process for building a semantic vector model from a triple store is conceptually as follows:

  * Create an [elemental vector](ElementalVector.md) _E(A)_ for each item _A_ in the input triple store file.
    * Also create a semantic vector _S(A)_ for the item, initially zero.
  * Create a operation on vectors _O(R)_ for each type of predicate or relation _R_ in the input.
  * Go through the training data. When the predication _A R B_ (also written _R(A, B)_ is encountered, increment the semantic vector for _A_ with the operation for _R_ applied to the elemental vector for _B_. In symbols:
```
R(A, B) => S(A) += O(R)(E(B)).
```

In practice, we have experimented with two kinds of operators for relations. The first version worked with permutations, in much the same way as PermutationSearch. The current implementation uses the [Vector.bind()](http://semanticvectors.googlecode.com/svn/javadoc/latest-stable/pitt/search/semanticvectors/vectors/Vector.html#bind(pitt.search.semanticvectors.vectors.Vector)) operation, as in the theory of Vector Symbolic Architectures. In this version, no extra representation of "operators" is necessary: instead, we create an elemental vector _E(R)_ for each type of relation _R_, and the training process is implemented as
```
R(A, B) => S(A) += E(R)*(E(B)),
```
where "`*`" is the bind operation of the VSA in question.

## Distribution and Worked Examples ##

PSI is integrated and available in SemanticVectors as of version 3.4, and works with real, binary, and complex vectors.

To get an example working, get the [nationalfacts.txt](https://code.google.com/p/semanticvectors/source/browse/trunk/test/testdata/nationalfacts/nationalfacts.txt) file from the test directory, and run

```
$ java pitt.search.lucene.LuceneIndexFromTriples nationalfacts.txt
$ java pitt.search.semanticvectors.PSI -vectortype binary -dimension 4096 -luceneindexpath predication_index/
```

This builds a PSI model for the national facts dataset. The model is represented by three files, containing the elemental vectors, semantic vectors, and predicate vectors.

There are several ways to query and explore this model. The simplest way to begin is to try to recover some of the individual relationships that went into constructing the model. For example, to ask "What is the currency of Mexico?", the following query works:

```
$ java pitt.search.semanticvectors.Search -searchtype boundproduct -queryvectorfile semanticvectors.bin -boundvectorfile predicatevectors.bin -searchvectorfile elementalvectors.bin -matchcase mexico HAS_CURRENCY
...
Search output follows ...
0.500977:mexican_peso
0.056641:indian_elephant
0.052734:guinea-bissau
0.044922:moroccan_dirham
...
```

Note that the big jump in similarity between the first and second result shows that the model is pretty confident that the currency of Mexico is the Mexican Peso, not the Indian Elephant!

Of course, the input model represented this fact already. However, you can also use the same model immediately for a similarity search, as follows.

```
~/Data/PSI: java pitt.search.semanticvectors.Search -queryvectorfile semanticvectors.bin mexico
...
Search output follows ...
1.000000:mexico
0.261230:albania
0.060547:dodo
0.049805:adamstown
0.046875:colombian_peso
...
```

Apart from Mexico itself, there is one other "similar" country with a significant similarity score - Albania! This is because both Mexico and Albania share the Golden Eagle as their national animal. (The national facts example dataset is very sparse. If we updated it to include languages and continents, for example, we would find that Mexico is considered to be similar to other North American countries that speak Spanish.)

As well as searching explicit relations and similarities, PSI can be used for analogical search. For example, suppose you were unaware of the formal "HAS\_CURRENCY" relation, and just wanted to know "If I use dollars in the USA, what do I use in Mexico?"

For queries like this, the "Search" command also implements a simple "formal query parse" using the `E(foo) * S(bar)` kind of notation for elemental and semantic vectors, developed in the papers cited below. For example, once you have the above example working on the test data, the "dollar of mexico" analogy can be recovered using the following query expression (all on one line):

```
java pitt.search.semanticvectors.Search -searchtype boundproduct -queryvectorfile semanticvectors.bin -boundvectorfile elementalvectors.bin -searchvectorfile elementalvectors.bin -matchcase "E(united_states_dollar)*S(united_states)*S(mexico)"
...
Search output follows ...
0.268555:mexican_peso
0.047852:moroccan_dirham
0.047852:hargeisa
...
```

## Why is this important? ##

PSI supplements traditional tools for artificial inference by giving "nearby" results. In cases where there is a single clear winner, this recovers the behavior of giving "one right answer". But in cases where there are several possible plausible answers, it can be a great benefit to have robust approximate answers. This has been applied to questions like "Which drugs that have already been developed and approved might help to inhibit the growth of prostate cancer cells?"

PSI is also fast. Every query expression gets resolved to a vector (so far usually a single vector), so once a query expression has been constructed, the search phase is just the same speed as any other search for related terms or documents using SemanticVectors. This is a big deal for computationally hard problems: an inference engine that runs as fast as a search engine could have many practical uses that were previously prohibitively slow or computationally intensive.

## References / Further Reading ##

[Predication-based Semantic Indexing: Permutations as a Means to Encode Predications in Semantic Space.](http://www.ncbi.nlm.nih.gov/pmc/articles/PMC2815384/pdf/amia-f2009-114.pdf) Cohen T, Schvaneveldt, R. Rindlesch, T. AMIA Annu Symp Proc. 2009. 114-118.

[Logical Leaps and Quantum Connectives: Forging Paths through Predication Space.](http://www.aaai.org/ocs/index.php/FSS/FSS10/paper/view/2272/2682) Cohen, T. Widdows, D. Schvaneveldt, R. Rindflesch, T. AAAI Fall 2010 symposium on Quantum Informatics for cognitive, social and semantic processes (QI-2010).

[Finding Schizophrenia's Prozac: Emergent Relational Similarity in Predication Space.](http://www.puttypeg.net/papers/schizophrenias-prozac.pdf) Cohen, T. Widdows, D. Schvaneveldt, RW. Rindflesch, T. Proceedings of the Fifth International Symposium on Quantum Interaction, Aberdeen, UK, 2011.

[Reasoning with vectors: a continuous model for fast robust inference](http://jigpal.oxfordjournals.org/content/early/2014/12/03/jigpal.jzu028.short). Widdows D, Cohen T, Logic Jnl IGPL, 2014.