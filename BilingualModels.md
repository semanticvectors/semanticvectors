# Bilingual Semantic Vector Models #

## Slight Warning ##

This is new functionality and is still somewhat bleeding-edge. Expect to have to put some effort in to get this to work.

In particular, there have been problems getting the Europarl data unzipped and aligned on Windows platforms, and some unforeseen word-splitting at accented characters on Windows. In addition, the scripts for generating the Europarl data have not worked for us on Windows yet. Rather than spend more time on this at the moment, current users have switched to Unix / Linux style systems (see [this thread](http://groups.google.com/group/semanticvectors/browse_thread/thread/7a4d301913a3fc72)). If this solution doesn't work for you, please contact us.

## Background ##

If you have a corpus of parallel documents, you can use this to build a bilingual model.
See e.g., [this paper](http://infomap.stanford.edu/papers/bilingual-terms.pdf).

## Preprocessing ##

You have to do this carefully. You will need two directories, called LANG1 and LANG2, e.g., `en/` and `fr/`. The contents of these directories must be identically named, with the files being parallel to one another. E.g., `en/file1` and `fr/file1` are translations of one another. If running ls / dir on the contents of the directories doesn't produce exactly the same filenames, the indexing program will presume that something is wrong and will give up.

This design was influenced partly by the europarl corpus, see http://www.statmt.org/europarl/

To create a set of input corpora from the europarl files:
  1. Download the corpus from the above link
  1. Run the preprocessing script that ships with the corpus, e.g., `sentence-align-corpus.perl en fr` (it's a perl script, you will need perl for this).
    1. This step is currently buggy, as of September 2009. You need to edit line 16 of `sentence-align-corpus.perl` to read `my $dir = "txt";`.
  1. Now copy and run the [chapter alignment script](http://code.google.com/p/semanticvectors/source/browse/trunk/scripts/europarl/chapters-as-files-align.perl) from the SemanticVectors scripts directory.
  1. This should create a new directory for you in the `aligned-chapters` directory, e.g., `aligned-chapters/en-fr`, with the two directories `en` and `fr` with parallel contents.

### Preprocessing for the MuchMore Springer Corpus ###

There have been some questions about how to build a model from the corpus of English and German medical abstracts from the MuchMore project. Remember, your target is to get two directories called `en` and `de` with identical filenames in them. Here's how to proceed:

  1. Download the (plain versions) of the corpora from http://muchmore.dfki.de/resources1.htm
  1. Unzip the files into `en` and `de` directories.
  1. Strip the "eng" and "ger" parts of the filenames (e.g., `for i in *; do j=$i; mv $i ${j/ger./}; done`).
  1. Remove the files that are available in English but not in German.  E.g., use the following python script

```
import os

os.chdir('de')
ger_files = os.listdir('.')

os.chdir('../en')
eng_files = os.listdir('.')

os.chdir('..')

for i in eng_files:
    if i not in ger_files:
        print i
        os.remove('en/' + i)

for i in ger_files:
    if i not in eng_files:
        print i
        os.remove('de/' + i)
```

You should now be able to proceed as below.

## Building a Model ##

Once you have a corpus prepared, `cd` to the directory containing the two directories containing the parallel files.

Now (having compiled the SemanticVectors source, having this and Lucene in your `classpath`, etc.,
  1. Run `java pitt.search.lucene.IndexBilingualFiles LANG1 LANG2`
  1. Now run `java pitt.search.semanticvectors.BuildBilingualIndex index LANG1 LANG2`

This should give you a model that you can now search.

To test the basic Lucene index, try running `java pitt.search.lucene.LuceneSearch -i index -f contents_LANG1 QUERYTERMS`.

To test the bilingual semantic vector search, try running `java pitt.search.semanticvectors.Search -queryvectorfile termvectors_LANG1.bin -searchvectorfile termvectors_LANG2.bin QUERYTERMS`.

Good luck!