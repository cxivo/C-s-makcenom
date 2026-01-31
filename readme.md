To compile the grammar, use 

```mvn clean compile```

then, compile and run `src/main/java/eo/cxivo/Main.java` (usually just with a play button in your favourite IDE)

Then, run 

```llc output/program.ll ; gcc output/program.s output/library.c -o output/program```

and 

```./output/program```

For documentation (in Slovak) see `Č.md`.