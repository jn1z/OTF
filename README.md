# OTF (On-The-Fly) Algorithms

See the paper [Deconstructing Subset Construction: Reducing While Determinizing]() for more details.

## The following new algorithms are implemented:

1. OTF-CCL (Convexity Closure Lattice)
2. OTF-CCLS (Convexity Closure Lattice with Simulation)
3. BRZ-OTF-CCL (CCL + [Brzozowski's method](https://en.wikipedia.org/wiki/DFA_minimization#Brzozowski's_algorithm), i.e., double-reversal)
4. BRZ-OTF-CCLS (CCLS + Brzozowski's method, i.e., double-reversal)

## The following algorithms are implemented for comparison:

1. SC (Subset Construction)
2. SCS (Subset Construction with Simulation equivalence reduction)
3. BRZ (Brzozowski's method)
4. BRZS (Brzozowski's method with Simulation equivalence reduction)

## What this repository contains

1. [OTF-1.0.jar](OTF-1.0.jar) : a library to run OTF algorithms
2. Source code to build [OTF-1.0.jar](OTF-1.0.jar) and OTFStandalone-1.0.jar (a command-line tool for demonstration purposes)
3. [OTF.sh](OTF.sh): a script to build the jars and run the command-line tool

## Building and running

OTF.sh both builds and runs the program.

Syntax: OTF [--sanity-check] \<algorithm\> \<BA file\>

- [--sanity-check] : Verifies specified algorithm against generic SC
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
