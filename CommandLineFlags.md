# Command-Line Flags in SemanticVectors #

## Overview ##

Flags can be added to the FlagConfig class, they are automatically parsed, and can then be accessed from an appropriate FlagConfig instance. Many classes take a reference to such a FlagConfig instance as part of their state, so this information is usually accessible.

The benefit of the system is that it's very easy to add and use new flags. This encourages people to experiment with semantic vectors. It can lead to consistency and reliability issues, but so far (over some years) these have not been a problem as far as we know.

## List of Flags ##

See the [Javadoc](http://semanticvectors.googlecode.com/svn/javadoc/latest-stable/pitt/search/semanticvectors/FlagConfig.html).

## Current Status (Instance Flag Variables) ##

The code checked in version 3.8 makes a flag config instance rather than using global variables. This provides some more encapsulation, so that (for example) opening two vector stores with different dimension settings doesn't lead to a conflict. (Provided, of course, that the callers don't try to share the same flag config instance.)