/**
Semantic Vector indexes, created by applying a Random Projection
algorithm to term-document matrices created using Apache Lucene.

<p> This Semantic Vecotors package implements a Random Projection
algorithm, a form of automatic semantic analysis, similar to Latent
Semantic Analysis (LSA) and its variants like Probabilistic Latent
Semantic Analysis (PLSA). However, unlike these methods, Random
Projection does not rely on the use of computationally intensive
matrix decomposition algorithms like Singular Value
Decomposition (SVD). This makes Random Projection a much more scalable
technique in practice.

<p> Our application of Random Projection for Natural Language
Processing (NLP) is descended from Pentti Kanerva's work on Sparse
Distributed Memory, which in semantic analysis and text mining, this
method has also been called Random Indexing. A growing number of
researchers have applied Random Projection to NLP tasks, demonstrating:

<ol>

<li>Performance comparable with other forms of Latent Semantic
Analysis.</li>

<li>Significant computational advantages in creating and incrementally
maintaining models.<li>

</ol>

<p> The current package was created as part of a project by the
University of Pittsburgh Office of Technology Management, to explore
the potential for automatically matching related concepts in the
technology management domain, e.g., mapping new technologies to
potentatially interested licensors. This project can be found at
<a href="http://real.hsls.pitt.edu">http://real.hsls.pitt.edu</a>.

<p> The package requires Apache Ant and Apache Lucene to have been
installed, and the Lucene classes must be available in your CLASSPATH.

<p> Further documentation and links to articles on Random Projection
and related techniques can be found at the package download site,
<a href="http://code.google.com/p/semanticvectors">
http://code.google.com/p/semanticvectors</a>.

@author Dominic Widdows, in collaboration with Kathleen Ferraro and
the University of Pittsburgh.

*/

package pitt.search.semanticvectors;
