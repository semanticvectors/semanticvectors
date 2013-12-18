#!/bin/bash

# Script for updating online javadoc when making a new release.
#
# Must be run from this directory, but uses higher directories in the
# svn tree as well.  Therefore you need to run on a copy that is checked
# out all the way from the trunk, e.g.,
#
# svn checkout https://semanticvectors.googlecode.com/svn/ semanticvectors-all --username ...
# cd semanticvectors-all
# ./update-release-docs.sh.


VERSION=5.3  # Should be kept in sync with pom.xml
DEST=../javadoc/latest-stable
mvn javadoc:jar
#svn rm --force "$DEST"
#svn ci "$DEST" -m "Removing old stuff for the new release javadocs"
#mkdir "$DEST"
#svn add "$DEST"
cd "$DEST"
jar xvf ../../trunk/target/semanticvectors-$VERSION-javadoc.jar
svn add --force .
find . -name "*.html" | xargs -n 1 svn propset svn:mime-type text/html
find . -name "*.css" | xargs -n 1 svn propset svn:mime-type text/css
find . -name "*.gif" | xargs -n 1 svn propset svn:mime-type image/gif
svn commit -m "Adding new javadoc for release version $VERSION"
