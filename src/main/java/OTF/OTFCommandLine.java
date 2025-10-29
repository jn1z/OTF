package OTF;

import OTF.Model.Threshold;
import OTF.Registry.AntichainForestRegistry;
import OTF.Registry.Registry;
import OTF.Simulation.ParallelSimulation;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.alphabet.impl.Alphabets;
import net.automatalib.automaton.fsa.DFA;
import net.automatalib.automaton.fsa.impl.CompactDFA;
import net.automatalib.automaton.fsa.impl.CompactNFA;
import net.automatalib.serialization.ba.BAWriter;
import net.automatalib.util.automaton.Automata;
import net.automatalib.util.automaton.fsa.NFAs;
import net.automatalib.util.automaton.minimizer.HopcroftMinimizer;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

public class OTFCommandLine {
  public static void main(String[] args) {
    String filename = null;
    List<String> positional = new ArrayList<>(2);

    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if ("--debug".equalsIgnoreCase(arg)) {
        OTFDeterminization.DEBUG = true;
      } else if ("--writeBA".equalsIgnoreCase(arg)) {
        // Require a value that isn't another flag
        if (i + 1 >= args.length || args[i + 1].startsWith("-")) {
          System.err.println("Missing value for --filename");
          printUsageAndExit(); // exits
        }
        filename = args[++i]; // consume the value
      } else if (arg.startsWith("-")) {
        // Unknown flag
        printUsageAndExit();
      } else {
        positional.add(arg);
      }
    }

    boolean validInvocation = (positional.size() == 2);
    if (!validInvocation) {
      printUsageAndExit();
    }

    String algorithm = positional.get(0);
    String filePath  = positional.get(1);

    final CompactNFA<Integer> origNFA = BAFormat.getBAFile(filePath);
    System.out.println("Original NFA size: " + origNFA.size());
    System.out.println("Alphabet size:" + origNFA.getInputAlphabet().size());

    long before = System.currentTimeMillis();
    CompactDFA<Integer> returnedDFA = allAlgorithms(algorithm, origNFA);
    long after = System.currentTimeMillis();
    System.out.println(algorithm + " minimized DFA size: " + returnedDFA.size());
    System.out.println(algorithm + " duration: " + ((after - before) / 1000f) + "s");

    if (filename != null) {
      writeBAFile(filename, returnedDFA);
    }
  }

  private static void printUsageAndExit() {
    System.out.println(
        "OTF [--debug] [--writeBA <BA output file>] <algorithm> <BA input file>");
    System.out.println("[--debug] : Additional debug/progress output");
    System.out.println("[--writeBA <BA output file> : Write DFA to specified output file");
    System.out.println();
    System.out.println("<algorithm> : one of the choices below:");
    System.out.println("  CCL: OTF's Convexity Closure Lattice algorithm.");
    System.out.println("  CCLS: OTF's Convexity Closure Lattice algorithm, with simulation enhancement.");

    System.out.println("  SC: Subset Construction.");
    System.out.println("  SCS: Subset Construction with simulation enhancement. Similar to Glabbeek-Ploeger's SUBSET(close c=).");

    System.out.println("  BRZ: Brzozowski's double-reversal algorithm.");
    System.out.println("  BRZS: Brzozowski's double-reversal algorithm with simulation enhancement.");

    System.out.println("  BRZ-CCL: Brzozowski's double-reversal algorithm, using CCL in step 1.");
    System.out.println("  BRZ-CCLS: Brzozowski's double-reversal algorithm, using CCLS in step 1.");

    System.out.println();
    System.out.println("<BA file> : finite automaton (in the BA format).");
    System.out.println("  BA format described here: https://languageinclusion.org/doku.php?id=tools");
    System.exit(0);
  }

  /**
   * Choose algorithm to run.
   * @param algorithm - algorithm passed in from command-line
   * @param origNFA - original NFA
   * @return - determinized and minimized DFA.
   */
  static CompactDFA<Integer> allAlgorithms(String algorithm, CompactNFA<Integer> origNFA) {
    System.out.println();
    System.out.println("Invoking algorithm:" + algorithm);
    String basicAlgorithm = algorithm.toLowerCase();
    boolean simulate = basicAlgorithm.endsWith("s");
    if (simulate) {
      basicAlgorithm = basicAlgorithm.substring(0,basicAlgorithm.length()-1); // strip final "s"
    }
    return switch (basicAlgorithm) {
      case "ccl" -> CCL(origNFA, simulate);
      case "sc" -> SC(origNFA, true, simulate);
      case "brz" -> Brz(origNFA, false, simulate);
      case "brz-ccl" -> Brz(origNFA, true, simulate);
      default -> throw new IllegalStateException("Unexpected algorithm choice: " + algorithm);
    };
  }

    /**
     * OTF-CCL or OTF-CCLS, with trim and bisim.
     * @param nfa - original NFA
     * @param simulate - whether to simulate. If false, uses OTF-CCL. If true, simulates and uses OTF-CCLS.
     * @return minimized DFA
     */
  public static CompactDFA<Integer> CCL(CompactNFA<Integer> nfa, boolean simulate) {
    final Threshold threshold = Threshold.adaptiveSteps(Threshold.DEFAULT_THRESHOLD_SIZE);

    nfa = trimAndBisim(nfa);
    final Alphabet<Integer> alphabet = nfa.getInputAlphabet();

    ArrayList<BitSet> simRels = new ArrayList<>();
    nfa = generateSimRels(simulate, nfa, simRels);

    Registry registry = new AntichainForestRegistry<>(nfa, simRels.toArray(new BitSet[0]));
    simRels.clear(); // GC

    final DFA<?, Integer> otfDFA = OTFDeterminization.doOTF(nfa.powersetView(), alphabet, threshold, registry);
    final CompactDFA<Integer> minimizedDFA = HopcroftMinimizer.minimizeDFA(otfDFA, alphabet);

    System.out.println("CCL max intermediate count: " + registry.getMaxIntermediateCount());
    System.out.println("CCL threshold crossings: " + threshold.getCrossings());

    return minimizedDFA;
  }

  /**
   * BRZ, BRZ-S, BRZ-OTF-CCL, BRZ-OTF-CCLS, with trim and bisim
   * BRZ-S is similar to Glabbeek-Ploeger's SUBSET(close c=) algorithm, if applied to Brzozowski step 1.
   * @param nfa - original NFA
   * @param OTF - if false, uses SC for step 1 -- i.e., BRZ or BRZ-S algorithms.
   * @param simulate - whether to simulate -- i.e., if false, BRZ or BRZ-OTF-CCL, otherwise BRZ-S or BRZ-OTF-CCLS
   * @return - minimized DFA
   */
  private static CompactDFA<Integer> Brz(CompactNFA<Integer> nfa, boolean OTF, boolean simulate) {
    long before = System.currentTimeMillis();
    nfa = NFATrim.reverse(nfa, CompactNFA::new);
    long after = System.currentTimeMillis();
    System.out.println("reverse time: " + ((after - before) / 1000f) + "s");

    Alphabet<Integer> reversedNFAAlphabet = nfa.getInputAlphabet();
    Alphabet<Integer> newAlphabet = Alphabets.integers(0,reversedNFAAlphabet.size()-1);
    // this allows reversedNFA to be GC'ed earlier

    final CompactDFA<Integer> brz1DFA = BrzStep1(OTF, nfa, simulate);
    nfa.clear(); // GC hint
    return BrzStep2(brz1DFA, newAlphabet);
  }

  /**
   * Step 1 of Brzozowski's double-reversal algorithm.
   */
  private static CompactDFA<Integer> BrzStep1(boolean OTF, CompactNFA<Integer> reversedNFA, boolean simulate) {
    long before = System.currentTimeMillis();

    CompactDFA<Integer> brz1DFA;
    if (OTF) {
      brz1DFA = CCL(reversedNFA, simulate);
    } else {
      reversedNFA = trimAndBisim(reversedNFA);
      brz1DFA = doSCInternal(simulate, reversedNFA, reversedNFA.getInputAlphabet(), "powerset DFA (BRZ step 1):");
    }
    long after = System.currentTimeMillis();
    System.out.println("Minimized BRZ step 1 size: " + brz1DFA.size());
    System.out.println("BRZ step 1 duration: " + ((after - before) / 1000f) + "s");
    return brz1DFA;
  }

  /**
   * Step 2 of Brzozowski's double-reversal algorithm.
   */
  private static CompactDFA<Integer> BrzStep2(CompactDFA<Integer> brz1, Alphabet<Integer> alphabet) {
    long before = System.currentTimeMillis();
    final CompactNFA<Integer> rev2 = NFATrim.reverse(brz1, alphabet);
    final CompactDFA<Integer> brz2DFA = NFAs.determinize(rev2, alphabet, false, false);
    long after = System.currentTimeMillis();
    System.out.println("BRZ step 2 duration: " + ((after - before) / 1000f) + "s");
    return brz2DFA;
  }

  /**
   * SC or SCS. SCS is similar to Glabbeek-Ploeger's SUBSET(close c=) algorithm.
   * Note we have the option to not run trim and bisimulation. This is for the sanity check.
   * @param nfa - original NFA
   * @param trimAndBisim - whether to trim and bisimulation reduce
   * @param simulate - whether to simulation reduce and use simulation relations.
   * @return - minimized DFA
   */
  private static CompactDFA<Integer> SC(CompactNFA<Integer> nfa, boolean trimAndBisim, boolean simulate) {
    if (trimAndBisim) {
      nfa = trimAndBisim(nfa);
    }
    return doSCInternal(simulate, nfa, nfa.getInputAlphabet(), "Unminimized SC DFA size:");
  }

  private static CompactDFA<Integer> doSCInternal(boolean simulate, CompactNFA<Integer> nfa, Alphabet<Integer> alphabet, String powersetOutput) {
    CompactDFA<Integer> dfa;
    if (simulate) {
      ArrayList<BitSet> simRels = new ArrayList<>();
      nfa = generateSimRels(true, nfa, simRels);
      Registry registry = new AntichainForestRegistry<>(nfa, simRels.toArray(new BitSet[0]));
      simRels.clear(); // GC
      final DFA<?, Integer> otfDFA = OTFDeterminization.doOTF(nfa.powersetView(), alphabet, Threshold.noop(), registry);
      System.out.println(powersetOutput + otfDFA.size());
      dfa = HopcroftMinimizer.minimizeDFA(otfDFA, alphabet);
    } else {
      dfa = NFAs.determinize(nfa, alphabet, false, false);
      System.out.println(powersetOutput + dfa.size());
      dfa = HopcroftMinimizer.minimizeDFA(dfa, alphabet);
    }
    return dfa;
  }

  /**
   * Trim and bisimulation reductions.
   * @param nfa - original NFA
   * @return reduced NFA
   */
  private static CompactNFA<Integer> trimAndBisim(CompactNFA<Integer> nfa) {
      int prevSize = nfa.size();
      long before = System.currentTimeMillis();

      nfa = NFATrim.trim(nfa);
      if (nfa.size() < prevSize) {
          System.out.println("Trimmed to: " + nfa.size());
          prevSize = nfa.size();
      }

      nfa = NFATrim.bisim(nfa);
      if (nfa.size() < prevSize) {
          System.out.println("Bisim forward/backward reduced to: " + nfa.size());
      }

      long after = System.currentTimeMillis();
      System.out.println("trim/bisim time: " +  ((after - before) / 1000f) + "s");;
      return nfa;
  }

  private static CompactNFA<Integer> generateSimRels(boolean simulate, CompactNFA<Integer> reducedNFA, ArrayList<BitSet> simRels) {
    if (simulate) {
      reducedNFA = simulate(reducedNFA, simRels);
      int simRelCount = 0;
      for (BitSet simRel : simRels) {
        if (simRel != null) {
          simRelCount++;
        }
      }
      System.out.println("Found " + simRelCount + " simulation relations");
    }
    return reducedNFA;
  }

  /**
   * Simulate, alter, and reduce.
   * Alterations are described in ParallelSimulation class.
   * @param reducedNFA - previously reduced NFA
   * @param simRels - simulation relations. These are mutated.
   * @return - altered NFA
   */
  private static CompactNFA<Integer> simulate(CompactNFA<Integer> reducedNFA, ArrayList<BitSet> simRels) {
      int prevSize = reducedNFA.size();
      long before = System.currentTimeMillis();
      reducedNFA = ParallelSimulation.fullyComputeRels(reducedNFA, simRels, true);
      if (reducedNFA.size() != prevSize) {
          System.out.println("Sim altered to: " + reducedNFA.size() + " states");
      }
      long after = System.currentTimeMillis();
      System.out.println("sim time: " + ((after - before) / 1000f) + "s");
      return reducedNFA;
  }

  private static void writeBAFile(String filename, CompactDFA<Integer> dfa) {
    System.out.println("Writing to file: " + filename);
    BAWriter<Integer> baWriter = new BAWriter<>();
    try (OutputStream os = new FileOutputStream(filename)) {
      baWriter.writeModel(os, dfa, dfa.getInputAlphabet());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
