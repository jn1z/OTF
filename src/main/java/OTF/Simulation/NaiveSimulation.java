package OTF.Simulation;

import it.unimi.dsi.fastutil.ints.IntIntImmutablePair;
import it.unimi.dsi.fastutil.ints.IntIntPair;
import net.automatalib.automaton.fsa.impl.CompactNFA;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;

public class NaiveSimulation {
  /**
   * Compute direct/forward simulation (with acceptance criteria) of an NFA.
   * Adapted from RABIT, see https://languageinclusion.org/doku.php?id=tools
   * @param nfa - NFA
   * @param parallel - whether to run in parallel for potentially faster execution.
   * @return A set of pairs of states that are simulated.
   */
  public static <I> Set<IntIntPair> computeDirectSimulation(
      CompactNFA<I> nfa, boolean withAcceptance, boolean parallel) {
    final int nStates = nfa.size();
    final int nSymbols = nfa.getInputAlphabet().size();

    // For performance, we store final states in a boolean[], rather than querying from a FixedBitSet
    final boolean[] isFinalArray = buildFinalArr(nfa, nStates);

    // Populate succ[][][] for quick transitions -- this is faster than using FixedBitSet[] internals
    final int[][][] succ = createSuccArr(nfa, nSymbols, nStates);

    // If relation[p].get(q) is true, then (p,q) is in the current relation.
    FixedBitSet[] relation = new FixedBitSet[nStates];
    for (int p = 0; p < nStates; p++) {
      relation[p] = new FixedBitSet(nStates);
    }

    initializeRelation(isFinalArray, relation, nSymbols, succ, withAcceptance);

    refineRelation(nSymbols, succ, relation, parallel);

    return collectRelation(relation);
  }

  // Create a boolean array of final states
  private static <I> boolean[] buildFinalArr(CompactNFA<I> nfa, int nStates) {
    boolean[] isFinal = new boolean[nStates];
    for (int i = 0; i < nStates; i++) {
      if (nfa.isAccepting(i)) {
        isFinal[i] = true;
      }
    }
    return isFinal;
  }

  // Create int[][][] succ, where succ[s][p] is an array of successor states for state p on symbol s.
  // As opposed to FixedBitSet[] trans, where trans[p*symbolLen + s] = FixedBitSet of successor states for state p on symbol s
  private static <I> int[][][] createSuccArr(
      CompactNFA<I> nfa,
      int nSymbols,
      int nStates) {
    final int[][][] succ = new int[nSymbols][nStates][];

    for (int p = 0; p < nStates; p++) {
      for (int a = 0; a < nSymbols; a++) {
        Set<Integer> transitions = nfa.getTransitions(p, a);
        if (transitions != null && !transitions.isEmpty()) {
          succ[a][p] = new int[transitions.size()];
          int idx = 0;
          for(int nxt: transitions) {
            succ[a][p][idx++] = nxt;
          }
        } else {
          // No transitions for this state/symbol => empty array
          succ[a][p] = new int[0];
        }
      }
    }
    return succ;
  }

  /*
  Initialize the direct-simulation relation.
  This is a coarse version of the final relation.
   */
  private static void initializeRelation(
      boolean[] isFinal,
      FixedBitSet[] relation,
      int nSymbols,
      int[][][] succ,
      boolean withAcceptance) {
    final int nStates = relation.length;
    for (int p = 0; p < nStates; p++) {
      boolean finalP = isFinal[p];
      FixedBitSet relationP = relation[p];
      for (int q = 0; q < nStates; q++) {
        // With acceptance criteria: if q simulates p and p is final, q must be final
        if (withAcceptance && (finalP && !isFinal[q])) {
          continue;
        }

        boolean setPQ = true;
        // If p has transitions on symbol s, but q has none, then q does not simulate p; remove
        for (int s = 0; s < nSymbols; s++) {
          if (succ[s][p].length > 0 && succ[s][q].length == 0) {
            setPQ = false;
            break;
          }
        }
        if (setPQ) {
          relationP.set(q);
        }
      }
    }
  }
  // Iteratively refine the relation until fixpoint
  private static void refineRelation(
      int nSymbols,
      int[][][] succ,
      FixedBitSet[] relation,
      boolean parallel) {
    final int nStates = relation.length;
    boolean changed = true;
    while (changed) {
      if (parallel) {
        changed = ForkJoinPool.commonPool().invoke(
            new ParSimTask(0, nStates, 0, nStates, nSymbols, succ, relation));
      } else {
        changed = singleRefine(0, nStates, 0, nStates, nSymbols, succ, relation);
      }
    }
  }

  // ---------------------------------------------------------------------
  // Single-thread refinement of direct-simulation
  // NOTE: this does get called as well in a multi-threaded case;
  //   however the FixedBitSet elements are carefully partitioned to avoid threading issues
  public static boolean singleRefine(int pStart, int pEnd, int qStart, int qEnd,
                                 int nSymbols, int[][][] succ, FixedBitSet[] relation) {
    boolean changed = false;
    for (int p = pStart; p < pEnd; p++) {
      FixedBitSet relationP = relation[p];
      for (int q = relationP.nextSetBit(qStart); q >= 0 && q < qEnd; q = relationP.nextSetBit(q + 1)) {
        // If (p,q) is in relation, check if it fails
        if (failsSimulation(p, q, nSymbols, succ, relation)) {
          relationP.clear(q); // not threadsafe in general, but due to partition boundaries, it is
          changed = true;
        }
      }
    }
    return changed;
  }

  // Check if direct simulation fails for (p,q)
  private static boolean failsSimulation(
      int p, int q, int nSymbols, int[][][] succ, FixedBitSet[] relation) {
    for (int a = 0; a < nSymbols; a++) {
      int[] nextP = succ[a][p];
      if (nextP.length > 0) {
        // If p has transitions on a, q must also have transitions on a
        int[] nextQ = succ[a][q];
        if (nextQ.length == 0) {
          return true;
        }
        // For each next state of p, we need a matching next state in q
        if (noMatchingTransition(nextP, nextQ, relation)) {
          return true;
        }
      }
    }
    return false;
  }

  // For each p -> r, must find some q -> t such that (r,t) in relation.
  private static boolean noMatchingTransition(int[] nextP, int[] nextQ, FixedBitSet[] relation) {
    for (int r : nextP) {
      boolean foundMatch = false;
      FixedBitSet relationR = relation[r];
      for (int t : nextQ) {
        if (relationR.get(t)) {
          foundMatch = true;
          break;
        }
      }
      if (!foundMatch) {
        // Found a transition p->r with no matching q->t
        return true;
      }
    }
    return false;
  }

  // Collect final (p,q) pairs
  private static Set<IntIntPair> collectRelation(FixedBitSet[] relation) {
    Set<IntIntPair> pairs = new HashSet<>();
    for (int p = 0; p < relation.length; p++) {
      FixedBitSet relationP = relation[p];
      for (int q = relationP.nextSetBit(0); q >= 0; q = relationP.nextSetBit(q + 1)) {
        pairs.add(new IntIntImmutablePair(p, q));
      }
    }
    return pairs;
  }
}
