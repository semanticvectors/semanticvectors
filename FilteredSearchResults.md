# Filtered Search Results #

Sometimes users want to filter search results based on some string matching criteria, for example, to find terms or documents with a given prefix.

As of release 2.4, this can be done using the `-vectorsearchfilterregex` command line flag.

For example, if you want to find the chapters in the Bible that SemanticVectors associates most closely with the term `abraham`, just use

```
$ java pitt.search.semanticvectors.Search -searchvectorfile docvectors.bin abraham

... 

0.40109703:bible_chapters/Genesis/Chapter_22
0.3834591:bible_chapters/Genesis/Chapter_23
0.3830521:bible_chapters/Genesis/Chapter_21
0.3555606:bible_chapters/Genesis/Chapter_25
0.33219722:bible_chapters/Genesis/Chapter_28
0.328413:bible_chapters/Genesis/Chapter_26
0.32454836:bible_chapters/Romans/Chapter_4
```

However, if you want to see only those chapters in books with `John` in the title, instead use

```
$ java pitt.search.semanticvectors.Search -vectorsearchfilterregex John -searchvectorfile docvectors.bin abraham

... 

0.2395311:bible_chapters/John/Chapter_8
0.22098213:bible_chapters/John/Chapter_10
0.21926682:bible_chapters/John/Chapter_13
0.21511698:bible_chapters/1_John/Chapter_5
0.21168056:bible_chapters/John/Chapter_7
0.20802344:bible_chapters/John/Chapter_17
0.20745404:bible_chapters/John/Chapter_3
0.20599832:bible_chapters/John/Chapter_14
0.20426038:bible_chapters/John/Chapter_11
```