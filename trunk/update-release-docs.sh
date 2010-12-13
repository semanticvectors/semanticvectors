#!/bin/bash

CURDIR=`pwd`
DEST=../javadoc/latest-stable
TARFILE=javadocarchive.tgz
ant doc
svn rm --force "$DEST"
svn ci "$DEST" -m "Removing old stuff for the new release javadocs"
rm -f $TARFILE &>/dev/null
cd doc
tar zcvf ../$TARFILE --exclude='.svn' *
cd ..
mkdir "$DEST"
cd "$DEST"
tar xvf "$CURDIR"/$TARFILE
cd "$CURDIR"
svn add --force "$DEST"
find "$DEST" -name "*.html" | xargs -n 1 svn propset svn:mime-type text/html
find "$DEST" -name "*.css" | xargs -n 1 svn propset svn:mime-type text/css
find "$DEST" -name "*.gif" | xargs -n 1 svn propset svn:mime-type image/gif
svn ci "$DEST" -m "Adding the new release javadoc files"
rm -f $TARFILE &>/dev/null




