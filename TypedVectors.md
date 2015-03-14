# Typed Vectors #

As of version 2.5, SemanticVectors supports vectors with binary, real, and complex values. This is a very important development for us. Real vectors are used in machine learning, complex vectors in physics, binary values are really the core of modern computing. Now we can explore their properties in comparative experiments, perhaps for the first time. Typed Vectors are described quite thoroughly in the [Real, Complex and Binary Vectors](http://www.puttypeg.net/papers/typed-vectors.pdf) paper.

To experiment with the new vector types, simply use

`-vectortype binary` or `-vectortype complex`

as an argument to your index building command. (Real is the default.) This will build an index with the type of vectors you've asked for. Index searching will automatically parse the vector type from the header row.

Each vector type has required a sophisticated implementation, because of physical contraints: we need sparse representations for elemental vectors and dense representations for their superpositions / linear combinations. This is abstracted into a single Vector interface that should take care of the various important transformations for you. But be careful using this, because different operations have different costs in time and space for the different vector types. We've done our very best so far to make sure that these are natural and sensible, but if something blows up on your heap, please let us know!

Note that not all operations are supported by all vector types. For example, LSA (singular value decomposition) and basic 2d-visualization still only work with real vectors. There is a simple tension here between research and production software: we would like every option to be available in all configurations, but enforcing this in all cases would discourage interesting contributions.

Here follows some details of the specific implementations.

## Real ##

The default, and should be no different in operation from prior versions of SemanticVectors.

Elemental vectors are sparsely represented as short[.md](.md) arrays containing signed offsets.

## Complex ##

Elemental vectors are sparse (most entries are zero), and the nonzero entries are randomly allocated unit complex numbers, stores as polar angles.

Semantic vectors are in Cartesian or unit polar coordinates, depending on the application. Lookup tables are created for optimized transformation between polar and Cartesian form. The similarity function is standard Hermitian scalar product in Cartesian form, or average cosine of angular deviation in polar form. There are interesting differences between the Cartesian and polar similarity functions, to explore these you currently need to check out and modify the code slightly.

The complex vectors implementation is primarily contributed by Lance de Vine and used for encoding structure, partly due to the naturally optimized nature of the convolution operation on unit complex vectors.

See [Semantic Oscillations: Encoding Context and Structure in Complex Valued Holographic Vectors](http://www.aaai.org/ocs/index.php/FSS/FSS10/paper/download/2277/2680).
Lance De Vine, Peter Bruza. Quantum Informatics for Cognitive, Social, and Semantic Processes: Papers from the AAAI Fall Symposium, 2010.

## Binary ##

Elemental vectors are randomly allocated bit sets. Because of the bit set representation as a set of 64-bit long integers, `-dimension` must be a multiple of 64.

Unlike real and complex vectors that are mainly zeros, elemental vectors need roughly equal numbers of 1s and 0s. So seedlength is automatically set to half of dimension.

The similarity function is given by 1 - normalized Hamming distance.

The binary vectors implementation is primarily contributed by Trevor Cohen, and was used in recent experiments on knowledge discovery.

See [Finding Schizophrenia's Prozac: Emergent Relational Similarity in Predication Space](http://www.sahs.uth.tmc.edu/tcohen/drafts/cohenQI2011_ff.pdf). Cohen, T. Widdows, D. Schvaneveldt, RW. Rindflesch, T. Proceedings of the Fifth International Symposium on Quantum Interactions, Aberdeen, UK, 2011.