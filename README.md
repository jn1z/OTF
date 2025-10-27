# OTF (On-The-Fly) Algorithms

See the paper [Deconstructing Subset Construction: Reducing While Determinizing](https://arxiv.org/abs/2505.10319) for more details.

## The following new algorithms are implemented:

1. OTF-CCL (Convexity Closure Lattice)
2. OTF-CCLS (Convexity Closure Lattice with Simulation)
3. BRZ-OTF-CCL (CCL + [Brzozowski's method](https://en.wikipedia.org/wiki/DFA_minimization#Brzozowski's_algorithm), i.e., double-reversal)
4. BRZ-OTF-CCLS (CCLS + Brzozowski's method, i.e., double-reversal)

## The following algorithms are implemented for comparison:

1. SC (Subset Construction)
2. SCS (Subset Construction with Simulation. Similar to Glabbeek-Ploeger's SUBSET(close c=) algorithm)
3. BRZ (Brzozowski's method)
4. BRZS (Brzozowski's method with Simulation)

## What this repository contains

1. OTF-1.1.0.jar : a library to run OTF algorithms
2. Source code to build OTF-1.1.0.jar and OTFStandalone-1.1.0.jar (a command-line tool for demonstration purposes)
3. [OTF.sh](OTF.sh): a script to build the jars and run the command-line tool

## Building and running

OTF.sh both builds and runs the program.

Syntax: OTF [--sanity-check] [--debug] \<algorithm\> \<BA file\>

- [--debug] : Additional debug/progress output
- [--writeBA \<BA output file\> : Write DFA to specified output file
- \<algorithm\> : one of:
  - CCL
  - CCLS
  - SC
  - SCS
  - BRZ
  - BRZS
  - BRZ-CCL
  - BRZ-CCLS
- \<BA file\> : finite automaton in the [BA format](https://languageinclusion.org/doku.php?id=tools)
