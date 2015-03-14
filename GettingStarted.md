This page is intended as just the barebones, and assumes that you are already somewhat familiar with using java at a command prompt. More help can be found at the InstallationInstructions page.

Please note that our deployment strategy changed in December 2014, to use the semanticvectors artifact in the [Maven Central Repository](https://oss.sonatype.org/#nexus-search;quick~semanticvectors).

# Installation #

All users should begin by installing java and checking that you can run java at a command-prompt.

## For End-Users ##

  * Download the latest Semantic Vectors jar file from the [Maven Central Repository](https://oss.sonatype.org/#nexus-search;quick~semanticvectors). This is currently [semanticvectors-5.6.jar](https://oss.sonatype.org/content/repositories/releases/pitt/search/semanticvectors/5.6/semanticvectors-5.6.jar).
  * This jar file contains the Semantic Vectors binaries and dependencies. Make sure you run java with this file in your classpath, e.g., using `java -cp $PATH/TO/semanticvectors-$VERSION.jar`.

## For Developers ##

  * Install maven and svn (or a development environment that includes these: see [Development Enviroments](https://code.google.com/p/semanticvectors/wiki/HowToContribute#Development_Environments)).
    * To build the project from source, checkout the project from svn.
    * To use the project as a dependency in another project, use the following dependency in your `pom.xml` file:

```
<dependency>
    <groupId>pitt.search</groupId>
    <artifactId>semanticvectors</artifactId>
    <version>$VERSION</version>
</dependency>
```

  * Build as usual using `mvn install` or using your IDE.

Developers will almost certainly want to read InstallationInstructions, HowToContribute, and perhaps ExampleClients as well as this page.

# Building and Searching Models #

See [To\_Build\_and\_Search\_a\_Model](https://code.google.com/p/semanticvectors/wiki/InstallationInstructions#To_Build_and_Search_a_Model).