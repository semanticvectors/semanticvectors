Latent Semantic Analysis, or LSA, was one of the earliest methods for compressing distributional semantic models and learning implicit semantic information in the process (see http://lsa.colorado.edu/).

LSA usually refers specifically to the use of Singular Value Decomposition (SVD) to compress a term-document matrix, though the term has sometimes been used more generally to cover more types of distributional semantic methods.

Traditional LSA is available into SemanticVectors: instead of using `java pitt.search.semanticvectors.BuildIndex`, use `java pitt.search.semanticvectors.LSA` in just the same way. This directs the learning process to use SVD instead of Random Projection.

The Singular Value Decomposition itself is computed using the SVDLIBJ package written by Adrian Kuhn and David Erni at the University of Bern. The SemanticVectors authors are very grateful for this contribution.

In ad hoc experiments, SVD and Random Projection produce an interesting variety of results. One place where SVD seems markedly better than alternatives is in finding terms related to particular documents (the "keyword generation" task). For more details see DocumentSearch.

Some caveats for using SVD include:
  * For large corpora: LSA takes much more time and memory than RandomProjection.
  * For corpora with a small number of documents: LSA cannot create a matrix whose dimension is greater than the number of documents in the corpus. Also, results are sometimes degenerate in small dimensions. This can sometimes result in NaN coordinates in your model. (At least, we believe this is the case, if it's actually something wrong with the integration into SemanticVectors we will humbly apologize and gratefully fix the error!)

SVD is also used in SemanticVectors for projecting high-dimensional data onto 2-dimensions for easy visualization. See ClusteringAndVisualization.