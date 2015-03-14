## The Semantic Vectors Package ##

SemanticVectors creates semantic WordSpace models from free natural language text.  Such models are designed to represent words and documents in terms of underlying concepts. They can be used for many semantic (concept-aware) matching tasks such as automatic thesaurus generation, knowledge representation, and concept matching. These are described more thoroughly in the UseCases page.

## Getting Started ##

  * See GettingStarted and the maven [semanticvectors artifact](https://oss.sonatype.org/#nexus-search;quick~semanticvectors).
  * More installation help can be found in the InstallationInstructions.
  * Java API Documentation is at http://semanticvectors.googlecode.com/svn/javadoc/latest-stable/index.html.
  * There are a range of SearchOptions, and help on using SemanticVectors for DocumentSearch.
  * Some proposed research and development suggestions are on the [Ideas](Ideas.md) page.

## Algorithms and Techniques ##

The models are created by applying concept mapping algorithms to term-document matrices created using [Apache Lucene](http://lucene.apache.org/java/docs/). The concept mapping algorithms supported by the package include [Random Projection](RandomProjection.md), [Latent Semantic Analysis](LatentSemanticAnalysis.md) (LSA) and [Reflective Random Indexing](ReflectiveRandomIndexing.md).

Random Projection is the most scalable technique in practice, because it does not rely on the use of computationally intensive matrix decomposition algorithms. The application of Random Projection for Natural Language Processing (NLP) is descended from Pentti Kanerva's work on Sparse Distributed Memory, which in semantic analysis and text mining, this method has also been called Random Indexing. Singular Value Decomposition is also popular because it is better known, and has in some cases given better results on smaller datasets.

Links to more RelatedResearch and lots of other topics can be found in the Wiki pages.

## Contributors and Projects ##

The package was created as part of a project by the University of Pittsburgh Office of Technology Management, and has been developed and maintained by contributors from the University of Texas, Queensland University of Technology, the Austrian Research Institute for Artificial Intelligence, Google Inc., and other institutions and individuals.
Contributions are welcome (and reasonably frequent).

There are many ways to get involved, as an end user and a contributor.

  * **Issues and Bugs.** Issues and bugs can be posted using the Issues tab above.
  * **User Group.**  More general questions and discussions may be posted at the group webpage, http://groups.google.com/group/semanticvectors.
  * **Developer Contributions.** For development, see HowToContribute. For suggestions on _what_ to contribute, you could start with the [Ideas](Ideas.md) page.
  * **Projects Using Semantic Vectors.** There is a list of ProjectsUsingSemanticVectors. Please visit this page and leave comments if you know of any.

