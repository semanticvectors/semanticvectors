# Vector Negation #

SemanticVectors has a vector negation feature as part of the suite of quantum logical connectives, which enables the user to remove as well as adding areas of meaning from a query vector. To try queries with vector negation, put the string `~NOT` (case-sensitive!) in the middle of your query terms.

E.g., with LSA and idf term weighting, the term Pharaoh has the following associations, many from the story of Joseph:

```
$ java pitt.search.semanticvectors.Search pharaoh
Opening query vector store from file: termvectors
Searching term vectors, searchtype sum
Found vector for 'pharaoh'
Search output follows ...
1.0:pharaoh
0.7340662933781189:egypt
0.6215162795875712:goshen
0.6005383489443102:pilgrimage
0.5896688764021908:egyptians
0.5858469900625858:joseph
0.58261456455918:activity
0.58261456455918:morever
0.5524420395543727:rameses
0.5354480091655884:famine
0.5217664763064731:land
```

And now with the term Joseph removed:

```
$ java pitt.search.semanticvectors.Search pharaoh ~NOT joseph
Opening query vector store from file: termvectors
Searching term vectors, searchtype sum
Found vector for 'joseph'
Found vector for 'pharaoh'
Search output follows ...
0.8104216557985685:pharaoh
0.5735466585389005:egypt
0.5204530686588099:egyptians
0.500296576464425:hardened
0.4860553081261236:magicians
0.46746076151866195:pilgrimage
0.4524470105000824:enchantments
0.44658235635986004:rameses
0.4376470971801214:ponds
```

Vector negation works by projecting the positive query vector onto the subspace orthogonal to all the negative query vectors. The same negation operator is used in quantum logic, as described in the 1930's by Garrett Birkhoff and John von Neumann.

A more thorough description can be found in Chapter 7 of [Geometry and Meaning](http://www.puttypeg.net/book/chapters/chapter7.html).