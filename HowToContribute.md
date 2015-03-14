# How to Contribute to SemanticVectors #

## What to Contribute ##

New ideas are the most welcome! But if you're looking for suggestions, or want to check if something has already been discussed, see the [Ideas](Ideas.md) page.

## Bug Reports and Feature Requests ##

Use the "Issues" tab above, which takes you to https://code.google.com/p/semanticvectors/issues/list

## New Code and Features ##

The best way to contribute new code is:

  * Write to the [project owners](https://code.google.com/p/semanticvectors/people/list) saying what you're planning to do.
    * For people familiar with the codebase, it's usually a good idea and the answer is probably "yes please".
  * [Checkout](https://code.google.com/p/semanticvectors/source/checkout) the code from svn, make your change, test as appropriate.
  * Commit into the main branch.
    * For larger projects with prearranged launch cycles this is intractable, but it's worked fine for us so far.
  * Use the review tools to make comments / suggestions.

Don't be afraid to commit changes. The worst that can happen is that we rollback the changes and try again!

### Small Changes / Fixes ###

For minor bug fixes and nits, for people familiar with the project: skip the first few steps above and just commit your fix. Review can happen thereafter.

### Really Big Changes ###

If you have a really big change / feature suggestion, please post a new thread in the [Semantic Vectors Group](https://groups.google.com/forum/?fromgroups#!forum/semanticvectors).

## Unit Tests and Integration Tests ##

Ideally, you should run the package tests using `mvn install -DskipTests=false` before you start coding. Sometimes the tests raise false alarms because you're the first to run the tests in a new environment (e.g., we had to make changes to accommodate the way Java handles files on Windows). If you do see problems, please email the project owners.

Please test your code and changes using `mvn install -DskipTests=false` before submitting.

## Coding Conventions ##

The coding style on the project tries to converge roughly to the old [Sun Microsystems conventions](http://www.oracle.com/technetwork/java/javase/documentation/codeconvtoc-136057.html), now hosted by Oracle. In particular, opening curly braces tend to go at the end of the previous line, not the beginning of a new line. However, most code uses 2 spaces for indenting new code blocks, and 4 spaces for indenting continued lines. The basic idea is that if code is well-organized, preserving whitespace is good because you can see more in your editor at-a-glance.

The important thing is consistency overall: so the deal is that nobody complains if authors submit code using a different style, and authors never complain if their code is edited slightly to converge to the rules-of-thumb above.

## Development Environments ##

To participate in active development, you will need at least maven and svn, and of course a text editor. It's just about possible to get by with a text editor like emacs or vim, and command-line svn, but this is increasingly "doing it the hard way.", and most developers use one of the following IDEs (integrated development environments):

  * [IntelliJ IDEA](http://www.jetbrains.com/idea/) from JetBrains. There is a free community edition. IntelliJ is increasingly preferred, partly because SVN and Maven are already installed.

  * [Eclipse](http://eclipse.org). Eclipse is also free, and popular especially with Java developers. You will have to install plugins for svn (try subclipse) and maven (try m2eclipse).