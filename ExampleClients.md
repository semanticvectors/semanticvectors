## Example Client Code that Uses SemanticVectors ##

As the package has matured, there have been an increasing number of requests for examples on how to use SemanticVectors in client applications.

An example project is now available at https://code.google.com/p/semanticvectors/source/browse/trunk/exampleclient. Note that this is example is implemented as an entirely distinct package, with its own maven file and a `pom.xml` file which included the dependency on SemanticVectors.

Other examples of client code for searching can be found in the public `main` methods of [CompareTerms](https://code.google.com/p/semanticvectors/source/browse/trunk/src/main/java/pitt/search/semanticvectors/CompareTerms.java), [CompareTermsBatch](https://code.google.com/p/semanticvectors/source/browse/trunk/src/main/java/pitt/search/semanticvectors/CompareTermsBatch.java),
and the [Search](https://code.google.com/p/semanticvectors/source/browse/trunk/src/main/java/pitt/search/semanticvectors/Search.java) class itself.

Note that these are all programmatic clients for searching and exploring vector models. They assume that you have already used the command-line tools to build your vector model.
