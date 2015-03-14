## Other Semantic Analysis Software Packages ##

The Semantic Vectors package builds upon a lot of research and experience in Latent Semantic Analysis and related technologies.

In particular, the design of the current package was partly informed by some years of working on and maintaining the [Infomap NLP package](http://infomap-nlp.sourceforge.net). The Infomap project was led by Stanley Peters, and the methods and software grew out of work by Hinrich Schütze and Stefan Kaufmann, with later contributions from Scott Cederberg, Beate Dorow and Dominic Widdows. The Infomap package was publicly released towards the end of the project, and developed a modest but active user base over a few years, but has also demonstrated two clear problems:

### Scalability ###

  * The Infomap NLP package starts by creating a large matrix (e.g., 1000 columns by 50000 rows, one for each term). It then uses Mike Berry's SVDPACKC to perform Singular Value Decomposition on this matrix. SVD is a computationally intensive algorithm which is hard to parallelize, and also hard to update incrementally.
  * The Semantic Vectors package instead uses a technique called Random Projection, which chooses a reduced representation (e.g., 200 columns) before training starts. Not only is this easier to keep in memory, parts of it can be written to disk and cached independently of one another, which makes the model much more scalable and easier to maintain incrementally. (Note that the initial package available now does not implement this paging to disk or incremental updates, because we haven't needed them yet, but it's important to know that this is possible.)

### Stability and Ease of Use ###

  * While the Infomap NLP package is all written in C in a fairly platform independent fashion, we have had a number of installation problems over the years, with compiling the core code, with integrating with SVDPACKC, and particularly with interfacing with the databases used to store the vectors.
  * The Semantic Vectors package is written entirely in Java, and its only dependency so far is Apache Lucene (and the Apache Ant build tools if you want to compile from source). Lucene is an extremely popular and well-maintained open source package, it's easy to install, and since everything's running in Java (including reading and writing the vectors from disk), we have so far had no integration problems at all. If you don't want to install Ant or compile from source, you can just download the jar distribution and provided your Java CLASSPATH points to both the Lucene and Semantic Vectors jarfiles, everything should just work.

For these reasons, we recommend that new users who would are looking for a Latent Semantic Analysis package should try using Semantic Vectors, especially if they run into any trouble with Infomap-NLP.

### Infomap Coocurrence Windows and Single-File Corpora ###

One important part of the design of the Infomap WordSpace models is that they use cooccurrence in a text window (e.g. of 15 words) rather than cooccurrence within a given document to build similarities. Partly for this reason, you can build an Infomap model from a corpus file that consists of just one long document.

In the first releases of SemanticVectors, this was not possible. However, since version 1.8 and ongoing, context window based models can be built with SemanticVectors using Lucene indexes that contain term position information. See PositionalIndexes.