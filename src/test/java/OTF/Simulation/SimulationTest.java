package OTF.Simulation;

import OTF.NFATrim;
import OTF.TabakovVardiRandomNFA;
import it.unimi.dsi.fastutil.ints.IntIntImmutablePair;
import it.unimi.dsi.fastutil.ints.IntIntPair;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.alphabet.impl.Alphabets;
import net.automatalib.automaton.fsa.impl.CompactDFA;
import net.automatalib.automaton.fsa.impl.CompactNFA;
import net.automatalib.util.automaton.Automata;
import net.automatalib.util.automaton.fsa.NFAs;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;

public class SimulationTest {
  @Test
  void testBasicSimulation() {
    CompactNFA<Integer> nfa = new CompactNFA<>(Alphabets.integers(0, 1));
    nfa.addInitialState(false);
    nfa.addState(false);
    nfa.addState(true);
    nfa.addTransition(0, 0, 1);
    nfa.addTransition(0, 0, 2);
    nfa.addTransition(1, 0, 2);
    Assertions.assertEquals(3, nfa.size());

    assertBasicSimulation(nfa, false);
    assertBasicSimulation(nfa, true);
  }

  private static void assertBasicSimulation(CompactNFA<Integer> nfa, boolean parallel) {
    Set<IntIntPair> rel = NaiveSimulation.computeDirectSimulation(nfa, true, parallel);
    Assertions.assertEquals(4, rel.size()); // trivial + "1 <= 0"
    Assertions.assertTrue(rel.contains(new IntIntImmutablePair(1, 0)));

    Set<IntIntPair> relEq = new HashSet<>();
    Set<IntIntPair> relNeq = new HashSet<>();
    ParallelSimulation.determineRelEqualities(rel, relEq, relNeq);
    Assertions.assertTrue(relEq.isEmpty());
    Assertions.assertEquals(1, relNeq.size());
    Assertions.assertTrue(rel.contains(new IntIntImmutablePair(1, 0)));

    ArrayList<BitSet> simSuperSets = new ArrayList<>();
    int totalRels = ParallelSimulation.simSupersets(relEq, relNeq, simSuperSets, nfa.size());
    Assertions.assertEquals(1, totalRels);
    BitSet b = new BitSet();
    b.set(1);
    Assertions.assertArrayEquals(new BitSet[]{b, null, null}, simSuperSets.toArray(new BitSet[0]));
    // {{1}, null, null} -- since 0 is a subset of 1.
  }

  @Test
  void testSimulationEquiv() {
    CompactNFA<Integer> nfa = new CompactNFA<>(Alphabets.integers(0, 1));
    nfa.addInitialState(false);
    nfa.addState(false);
    nfa.addState(true);
    nfa.addState(false);
    nfa.addTransition(0, 0, 1);
    nfa.addTransition(0, 0, 2);
    nfa.addTransition(0, 0, 3);
    nfa.addTransition(1, 0, 2);
    nfa.addTransition(3, 0, 2);
    // states 1 and 3 are equivalent. state 0 <= state 1.
    Assertions.assertEquals(4, nfa.size());

    assertSimulationEquiv(nfa, false);
    assertSimulationEquiv(nfa, true);
  }

  private static void assertSimulationEquiv(CompactNFA<Integer> nfa, boolean parallel) {
    Set<IntIntPair> rel = NaiveSimulation.computeDirectSimulation(nfa, true, parallel);
    Assertions.assertEquals(4 + 2 + 2, rel.size());
    // trivial + "1 <= 0" + " 3 <= 0" + " 1 <= 3" + "3 <= 1"
    Assertions.assertTrue(rel.contains(new IntIntImmutablePair(1, 0)));

    Set<IntIntPair> relEq = new HashSet<>();
    Set<IntIntPair> relNeq = new HashSet<>();
    ParallelSimulation.determineRelEqualities(rel, relEq, relNeq);
    Assertions.assertEquals(1, relEq.size());
    Assertions.assertTrue(relEq.contains(new IntIntImmutablePair(1, 3)));
    Assertions.assertEquals(2, relNeq.size());
    Assertions.assertTrue(rel.contains(new IntIntImmutablePair(1, 0)));
    Assertions.assertTrue(rel.contains(new IntIntImmutablePair(3, 0)));

    ArrayList<BitSet> simSuperSets = new ArrayList<>();
    int totalRels = ParallelSimulation.simSupersets(relEq, relNeq, simSuperSets, nfa.size());
    Assertions.assertEquals(2, totalRels);
    BitSet b = new BitSet();
    b.set(1);
    b.set(3);
    Assertions.assertArrayEquals(new BitSet[]{b, null, null, null}, simSuperSets.toArray(new BitSet[0]));
    // {{1,3}, null, null, null} -- since 0 is a subset of 1 and 3.
  }

  @Test
  void testFullyComputeRelsRegression() {
    final Alphabet<Integer> alph = Alphabets.integers(0, 1);
    CompactNFA<Integer> tv = TabakovVardiRandomNFA.generateNFA(new Random(673), 150, 1.3f, 0.5f, alph, CompactNFA::new);
    ArrayList<BitSet> simRels = new ArrayList<>();

    tv = ParallelSimulation.fullyComputeRels(tv, simRels, false);
    Assertions.assertEquals(127, tv.size());

    simRels.clear();
    tv = ParallelSimulation.fullyComputeRels(tv, simRels, true);
    Assertions.assertEquals(127, tv.size());
  }

  @Test
  public void testUnifyInitialStates() {
    CompactNFA<Integer> nfa = new CompactNFA<>(Alphabets.integers(0, 1));

    int state0 = nfa.addInitialState(true);

    // trivial case
    ParallelSimulation.unifyInitialStatesWithoutTrim(nfa);
    Assertions.assertEquals(1, nfa.size());

    int state1 = nfa.addIntState(false);
    int state2 = nfa.addInitialState(true);

    nfa.addTransition(state0, 0, state2);
    nfa.addTransition(state1, 0, state2);

    Assertions.assertEquals(3, nfa.size());
    Assertions.assertEquals(2, nfa.getInitialStates().size());

    ParallelSimulation.unifyInitialStatesWithoutTrim(nfa);
    Assertions.assertEquals(4, nfa.size());
    Assertions.assertEquals(1, nfa.getInitialStates().size());

    nfa = NFATrim.trim(nfa);

    Assertions.assertEquals(2, nfa.size());
    int unifiedState = 1;

    // new single initial state
    Set<Integer> initialStates = nfa.getInitialStates();
    Assertions.assertEquals(1, initialStates.size());
    Assertions.assertEquals(unifiedState, initialStates.iterator().next());
    Assertions.assertTrue(nfa.isAccepting(unifiedState));

    Assertions.assertTrue(nfa.isAccepting(0)); // otherwise it would have been trimmed
    Assertions.assertEquals(1, nfa.getTransitions(unifiedState, 0).size());
    Assertions.assertEquals(0, nfa.getTransitions(unifiedState, 0).iterator().next());
  }

  @Test
  void testFindRepresentatives() {
    Set<IntIntPair> relEq = new HashSet<>();
    relEq.add(new IntIntImmutablePair(1, 2));
    relEq.add(new IntIntImmutablePair(2, 3));
    relEq.add(new IntIntImmutablePair(4, 5));
    int[] origToRep = ParallelSimulation.findRepresentatives(relEq, 6);
    int[] expectedArr = new int[]{0, 1, 1, 1, 4, 4};
    Assertions.assertArrayEquals(expectedArr, origToRep);

    int[] renumbered = ParallelSimulation.renumberRepresentatives(origToRep);
    int[] expectedRenumber = new int[]{0, 1, 1, 1, 2, 2};
    Assertions.assertArrayEquals(expectedRenumber, renumbered);
  }

  @Test
  void testSimReverseRegression() {
    final Alphabet<Integer> alph = Alphabets.integers(0, 1);

    assertSimReverseRegression(alph, false);
    assertSimReverseRegression(alph, true);
  }

  private static void assertSimReverseRegression(Alphabet<Integer> alph, boolean parallel) {
    CompactNFA<Integer> tv = TabakovVardiRandomNFA.generateNFA(new Random(5), 3, 1.3f, 0.5f, alph, CompactNFA::new);

    CompactDFA<Integer> oldSCTest, newOTF;
    oldSCTest = NFAs.determinize(tv, alph);

    CompactNFA<Integer> reducedTV = ParallelSimulation.fullyComputeRels(tv, new ArrayList<>(), parallel);
    newOTF = NFAs.determinize(reducedTV, alph);
    Assertions.assertEquals(oldSCTest.size(), newOTF.size());
    Assertions.assertTrue(Automata.testEquivalence(oldSCTest, newOTF, alph));
  }
}
