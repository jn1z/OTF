package OTF;

import OTF.Model.Threshold;
import OTF.Registry.AntichainForestRegistry;
import OTF.Registry.Registry;
import OTF.Simulation.ParallelSimulation;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.automaton.fsa.DFA;
import net.automatalib.automaton.fsa.impl.CompactDFA;
import net.automatalib.automaton.fsa.impl.CompactNFA;
import net.automatalib.util.automaton.Automata;
import net.automatalib.util.automaton.minimizer.HopcroftMinimizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;

public class OTFCommandLine {
  public static void main(String[] args) {
    boolean testAgainstSC = args.length == 3 && "--sanity-check".equalsIgnoreCase(args[0]);
    boolean validInvocation = args.length == 2 || (args.length == 3 && testAgainstSC);
    if (!validInvocation) {
      System.out.println(
          "OTF [--sanity-check] <algorithm> <BA file>");
      System.out.println("[--sanity-check] : Verifies algorithm against generic SC algorithm.");
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

    int baseIndex = testAgainstSC ? 1 : 0;

    String filePath = args[1 + baseIndex];
    CompactNFA<Integer> origNFA = BAFormat.getBAFile(filePath);
    System.out.println("Original NFA size: " + origNFA.size());
    System.out.println("Alphabet size:" + origNFA.getInputAlphabet().size());

    String algorithm = args[baseIndex];
    long before = System.currentTimeMillis();
    DFA<?, Integer> returnedDFA = allAlgorithms(algorithm, origNFA);
    long after = System.currentTimeMillis();
    System.out.println(algorithm + " minimized DFA size: " + returnedDFA.size());
    System.out.println(algorithm + " duration: " + ((after - before) / 1000f) + "s");

    if (testAgainstSC) {
      testAgainstSC(origNFA, returnedDFA);
    }
  }

  /**
   * Choose algorithm to run.
   * @param algorithm - algorithm passed in from command-line
   * @param origNFA - original NFA
   * @return - determinized and minimized DFA.
   */
  public static CompactDFA<Integer> allAlgorithms(String algorithm, CompactNFA<Integer> origNFA) {
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
     * @param origNFA - original NFA
     * @param simulate - whether to simulate. If false, uses OTF-CCL. If true, simulates and uses OTF-CCLS.
     * @return minimized DFA
     */
  public static CompactDFA<Integer> CCL(CompactNFA<Integer> origNFA, boolean simulate) {
    final Alphabet<Integer> alphabet = origNFA.getInputAlphabet();

    final Threshold threshold = Threshold.adaptiveSteps(4000);

    CompactNFA<Integer> reducedNFA = trimAndBisim(origNFA);

    ArrayList<BitSet> simRels = new ArrayList<>();
    reducedNFA = generateSimRels(simulate, reducedNFA, simRels);

    Registry registry = new AntichainForestRegistry<>(reducedNFA, simRels.toArray(new BitSet[0]));

    DFA<?, Integer> otfDFA = OTFDeterminization.doOTF(reducedNFA.powersetView(), alphabet, threshold, registry);
    CompactDFA<Integer> minimizedDFA = HopcroftMinimizer.minimizeDFA(otfDFA, alphabet);

    System.out.println("CCL max intermediate count: " + registry.getMaxIntermediateCount());
    System.out.println("CCL threshold crossings: " + threshold.getCrossings());

    return minimizedDFA;
  }

  /**
   * BRZ, BRZ-S, BRZ-OTF-CCL, BRZ-OTF-CCLS, with trim and bisim
   * BRZ-S is similar to Glabbeek-Ploeger's SUBSET(close c=) algorithm, if applied to Brzozowski step 1.
   * @param origNFA - original NFA
   * @param OTF - if false, uses SC for step 1 -- i.e., BRZ or BRZ-S algorithms.
   * @param simulate - whether to simulate -- i.e., if false, BRZ or BRZ-OTF-CCL, otherwise BRZ-S or BRZ-OTF-CCLS
   * @return - minimized DFA
   */
  private static CompactDFA<Integer> Brz(CompactNFA<Integer> origNFA, boolean OTF, boolean simulate) {
    long before = System.currentTimeMillis();
    CompactNFA<Integer> reversedNFA = NFATrim.reverse(origNFA, CompactNFA::new);
    long after = System.currentTimeMillis();
    System.out.println("reverse time: " + ((after - before) / 1000f) + "s");

    CompactDFA<Integer> brz1DFA = BrzStep1(OTF, reversedNFA, simulate);
    CompactDFA<Integer> brz2DFA = BrzStep2(brz1DFA, reversedNFA.getInputAlphabet());

    return brz2DFA;
  }

  /**
   * Step 1 of Brzozowski's double-reversal algorithm.
   */
  private static CompactDFA<Integer> BrzStep1(boolean OTF, CompactNFA<Integer> reversedNFA, boolean simulate) {
    long before = System.currentTimeMillis();
    Alphabet<Integer> alphabet = reversedNFA.getInputAlphabet();

    CompactDFA<Integer> brz1DFA;
    if (OTF) {
      brz1DFA = CCL(reversedNFA, simulate);
    } else {
      CompactNFA<Integer> reducedNFA = trimAndBisim(reversedNFA);
      brz1DFA = doSCInternal(simulate, reducedNFA, alphabet, "powerset DFA (BRZ step 1):");
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
    CompactDFA<Integer> brz2DFA;
    CompactNFA<Integer> rev2 = NFATrim.reverse(brz1, alphabet);
    brz2DFA = PowersetDeterminizer.determinize(rev2, alphabet, false);
    long after = System.currentTimeMillis();
    System.out.println("BRZ step 2 duration: " + ((after - before) / 1000f) + "s");
    return brz2DFA;
  }

  /**
   * SC or SCS. SCS is similar to Glabbeek-Ploeger's SUBSET(close c=) algorithm.
   * Note we have the option to not run trim and bisimulation. This is for the sanity check.
   * @param origNFA - original NFA
   * @param trimAndBisim - whether to trim and bisimulation reduce
   * @param simulate - whether to simulation reduce and use simulation relations.
   * @return - minimized DFA
   */
  private static CompactDFA<Integer> SC(CompactNFA<Integer> origNFA, boolean trimAndBisim, boolean simulate) {
    Alphabet<Integer> alphabet = origNFA.getInputAlphabet();
    CompactNFA<Integer> newNFA = origNFA;
    if (trimAndBisim) {
      newNFA = trimAndBisim(origNFA);
    }
    return doSCInternal(simulate, newNFA, alphabet, "Unminimized SC DFA size:");
  }

  private static CompactDFA<Integer> doSCInternal(boolean simulate, CompactNFA<Integer> nfa, Alphabet<Integer> alphabet, String powersetOutput) {
    CompactDFA<Integer> dfa;
    if (simulate) {
      ArrayList<BitSet> simRels = new ArrayList<>();
      nfa = generateSimRels(true, nfa, simRels);
      Registry registry = new AntichainForestRegistry<>(nfa, simRels.toArray(new BitSet[0]));
      DFA<?, Integer> otfDFA = OTFDeterminization.doOTF(nfa.powersetView(), alphabet, Threshold.noop(), registry);
      System.out.println(powersetOutput + otfDFA.size());
      dfa = HopcroftMinimizer.minimizeDFA(otfDFA, alphabet);
    } else {
      dfa = PowersetDeterminizer.determinize(nfa, alphabet, false);
      System.out.println(powersetOutput + dfa.size());
      dfa = HopcroftMinimizer.minimizeDFA(dfa, alphabet);
    }
    return dfa;
  }

  /**
   * Trim and bisimulation reductions.
   * @param origNFA - original NFA
   * @return reduced NFA
   */
  private static CompactNFA<Integer> trimAndBisim(CompactNFA<Integer> origNFA) {
      int prevSize = origNFA.size();
      long before = System.currentTimeMillis();

      CompactNFA<Integer> reducedNFA = NFATrim.trim(origNFA);
      if (reducedNFA.size() < prevSize) {
          System.out.println("Trimmed to: " + reducedNFA.size());
          prevSize = reducedNFA.size();
      }

      reducedNFA = NFATrim.bisim(reducedNFA);
      if (reducedNFA.size() < prevSize) {
          System.out.println("Bisim forward/backward reduced to: " + reducedNFA.size());
      }

      long after = System.currentTimeMillis();
      System.out.println("trim/bisim time: " +  ((after - before) / 1000f) + "s");;
      return reducedNFA;
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
      System.out.println("sim time: " + ((after - before) / 1000f) + "s");;
      return reducedNFA;
  }

  /**
   * Sanity check -- verify that algorithm matches what's generated by SC.
   * @param origNFA - original NFA
   * @param newDFA - minimized DFA generated by other method
   */
  private static void testAgainstSC(CompactNFA<Integer> origNFA, DFA<?, Integer> newDFA) {
    System.out.println();
    System.out.println("Running sanity check against SC");
    CompactDFA<Integer> scDFA = SC(origNFA, false, false);
    System.out.println("SC minimized size:" + scDFA.size());
    if (scDFA.size() != newDFA.size()) {
      if (Math.abs(scDFA.size() - newDFA.size()) != 1) {
        throw new IllegalStateException("Sizes not equal.");
      } else {
        System.out.println("WARNING: Sizes off by one: might be a totalization issue, fixed with full canonicalization.");
      }
    }
    if (!Automata.testEquivalence(scDFA, newDFA, origNFA.getInputAlphabet())) {
      throw new IllegalStateException("Determinized automaton not canonical.");
    } else {
      System.out.println("Automata verified as equivalent.");
    }
  }
}
