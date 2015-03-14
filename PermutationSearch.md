# Permutation Indexing and Search #

The technique of using vector coordinate permutations (also interpreted as rotations) to investigate the effect of word order on vector semantics has recently been introduced by [Sahlgren, Holst & Kanerva (2008)](http://www.sics.se/~mange/papers/permutationsCogSci08.pdf).

To build a permutation or directional index (using SemanticVectors v1.16 or later), follow the instructions for building PositionalIndexes, but using the additional command line option `-indextype permutation` or `-indextype directional`. A permutation index encodes the position of each term relative to each other term within a sliding window, while a directional index encodes whether a term occurs before or after each other term in this sliding window. The directional approach was first used in Burgess and Lund's HAL model.

To search, use the normal Search program with the option `-searchtype PERMUTATION`, and use the options '-q randomvectors.bin' and  `-s permtermvectors.bin` to tell the search to use the default output of BuildPositionalIndex.

Query syntax uses a question mark "?" character to denote the target location, so that (e.g.) a search for "Martin ? King" would ideally return the result "Luther". The question mark must be used once and only once in the current implementation. If anyone has a good idea for figuring out the mathematics for two or more question marks, please [send us a message](http://groups.google.com/group/semanticvectors), if only to let us know how to contact you!

An example query (using the Bible corpus) then goes as follows:

```
~/Data/Corpora/bible_indexes/positional widdows$ java pitt.search.semanticvectors.Search -searchtype permutation -queryvectorfile elementalvectors.bin -searchvectorfile permtermvectors.bin king ? 
Opening query vector store from file: randomvectors.bin
Dimensions = 200
Opening search vector store from file: permtermvectors.bin
Dimensions = 200
Lowercasing term: king
Lowercasing term: ?
Searching term vectors, searchtype PERMUTATION ... Search output follows ...
0.72752255:assyria
0.72466135:babylon
0.6896844:ahasuerus
0.6166289:syria
0.51723355:persia
0.4362294:solomon
0.4360716:zobah
0.41100603:judah
0.3905197:agrippa
0.30784437:eglon
0.30432403:ahaz
0.3025093:tyre
```

Compare this with the standard query:

```
dom-laptop:~/Data/Corpora/bible_indexes widdows$ java pitt.search.semanticvectors.Search king
Opening query vector store from file: termvectors.bin
Dimensions = 200
Lowercasing term: king
Searching term vectors, searchtype SUM ... Search output follows ...
1.0000001:king
0.4411627:province
0.40771794:reign
0.3804317:had
0.37756744:did
0.3773844:came
0.375291:servants
0.3745748:adonijah
0.369213:nor
0.3688996:princes
0.3642981:so
0.36391386:jehoiada
```

Quite a difference! The permutational index seems to have used the order of words to represent two particular relationhips from "king" - not synonymy or taxonomy, or a mixture of vaguely defined associates, but places that had kings and people who were (or at least may have been) kings.

Here are a few more examples, drawn from Jones and Mewhort (2007) and Sahlgren (2008) generated using the TASA corpus, which was kindly provided by Tom Landauer.

```
(1) martin ? king

top match: 0.78586185:luther

(2) king ?

0.6118218:aegeus
0.6102768:minos
0.5221031:midas
0.52174014:lear
0.48346457:jr

(3) although ? cannot fly

top match: 0.13623515:ostriches

(4) ? sea

0.57990074:sargasso
0.5242979:caspian
0.47394556:aegean
0.41262993:baltic
0.3766794:mediterranean
```

The use of directional information has transformed research in distributional semantics in very recent years.