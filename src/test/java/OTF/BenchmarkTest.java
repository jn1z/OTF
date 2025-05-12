package OTF;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import OTF.Model.Threshold;
import OTF.Registry.AntichainForestRegistry;
import OTF.Simulation.ParallelSimulation;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.automaton.fsa.DFA;
import net.automatalib.automaton.fsa.impl.CompactDFA;
import net.automatalib.automaton.fsa.impl.CompactNFA;
import net.automatalib.util.automaton.Automata;
import net.automatalib.util.automaton.fsa.NFAs;
import net.automatalib.util.automaton.minimizer.HopcroftMinimizer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("IntegTest")
public class BenchmarkTest {
    private static final List<CompactNFA<Integer>> AUTOMATA;
    private static final Threshold MAX_THRESHOLD = Threshold.maxSteps(100);

    static {
        final int size = 30;
        final int amount = 1000;
        AUTOMATA = new ArrayList<>(amount);
        for (int randomSeed = 0; randomSeed < amount; randomSeed++) {
            AUTOMATA.add(TabakovVardiRandomNFA.getRandomTrimAutomaton(randomSeed, size, CompactNFA::new));
        }
    }

    @Test
    void testSC() {
        for (CompactNFA<Integer> automaton : AUTOMATA) {
            final Alphabet<Integer> alphabet = automaton.getInputAlphabet();
            final CompactDFA<Integer> dfa = NFAs.determinize(automaton, alphabet);
            final CompactDFA<Integer> result = PowersetDeterminizer.determinize(automaton, alphabet);

            Assertions.assertEquals(dfa.size(), result.size());
            Assertions.assertTrue(Automata.testEquivalence(dfa, result, alphabet));
        }
    }

    @Test
    void testBRZ() {
        for (CompactNFA<Integer> automaton : AUTOMATA) {
            final Alphabet<Integer> alphabet = automaton.getInputAlphabet();
            final CompactDFA<Integer> dfa = NFAs.determinize(automaton, alphabet);
            final CompactNFA<Integer> rev = new CompactNFA<>(alphabet);
            NFAs.reverse(automaton, alphabet, rev);
            final CompactDFA<Integer> revDet = PowersetDeterminizer.determinize(rev, alphabet, false);
            final CompactNFA<Integer> revrev = new CompactNFA<>(alphabet);
            NFAs.reverse(revDet, alphabet, revrev);
            final CompactDFA<Integer> result = PowersetDeterminizer.determinize(revrev, alphabet, false);

            Assertions.assertEquals(dfa.size(), result.size());
            Assertions.assertTrue(Automata.testEquivalence(dfa, result, alphabet));
        }
    }

    @Test
    void testOTF_CCL() {
        for (CompactNFA<Integer> automaton : AUTOMATA) {
            final Alphabet<Integer> alphabet = automaton.getInputAlphabet();
            final CompactDFA<Integer> dfa = NFAs.determinize(automaton, alphabet);
            final DFA<?, Integer> det = OTFDeterminization.doOTF(
                automaton.powersetView(),
                alphabet,
                MAX_THRESHOLD,
                new AntichainForestRegistry<>(automaton));
            final CompactDFA<Integer> result = HopcroftMinimizer.minimizeDFA(det, alphabet);

            Assertions.assertEquals(dfa.size(), result.size());
            Assertions.assertTrue(Automata.testEquivalence(dfa, result, alphabet));
        }
    }

    @Test
    void testOTF_CCLS() {
        for (CompactNFA<Integer> automaton : AUTOMATA) {
            final Alphabet<Integer> alphabet = automaton.getInputAlphabet();
            final CompactDFA<Integer> dfa = NFAs.determinize(automaton, alphabet);
            final ArrayList<BitSet> simRels = new ArrayList<>();
            final CompactNFA<Integer> nfa = ParallelSimulation.fullyComputeRels(automaton, simRels, true);
            final DFA<?, Integer> det = OTFDeterminization.doOTF(
                nfa.powersetView(),
                alphabet,
                MAX_THRESHOLD,
                new AntichainForestRegistry<>(nfa, simRels.toArray(new BitSet[0])));
            final CompactDFA<Integer> result = HopcroftMinimizer.minimizeDFA(det, alphabet);

            Assertions.assertEquals(dfa.size(), result.size());
            Assertions.assertTrue(Automata.testEquivalence(dfa, result, alphabet));
        }
    }

    @Test
    void testOTF_BRZ_CCL() {
        for (CompactNFA<Integer> automaton : AUTOMATA) {
            final Alphabet<Integer> alphabet = automaton.getInputAlphabet();
            final CompactDFA<Integer> dfa = NFAs.determinize(automaton, alphabet);
            final CompactNFA<Integer> rev = new CompactNFA<>(alphabet);
            NFAs.reverse(automaton, alphabet, rev);
            final DFA<?, Integer> revDet = OTFDeterminization.doOTF(
                rev.powersetView(),
                alphabet,
                MAX_THRESHOLD,
                new AntichainForestRegistry<>(rev));
            final CompactNFA<Integer> revrev = new CompactNFA<>(alphabet);
            NFAs.reverse(revDet, alphabet, revrev);
            final CompactDFA<Integer> result = PowersetDeterminizer.determinize(revrev, alphabet, false);

            Assertions.assertEquals(dfa.size(), result.size());
            Assertions.assertTrue(Automata.testEquivalence(dfa, result, alphabet));
        }
    }

    @Test
    void testOTF_BRZ_CCLS() {
        for (CompactNFA<Integer> automaton : AUTOMATA) {
            final Alphabet<Integer> alphabet = automaton.getInputAlphabet();
            final CompactDFA<Integer> dfa = NFAs.determinize(automaton, alphabet);
            final CompactNFA<Integer> rev = new CompactNFA<>(alphabet);
            NFAs.reverse(automaton, alphabet, rev);
            final ArrayList<BitSet> simRels = new ArrayList<>();
            final CompactNFA<Integer> nfa = ParallelSimulation.fullyComputeRels(rev, simRels, true);
            final DFA<?, Integer> revDet = OTFDeterminization.doOTF(
                nfa.powersetView(),
                alphabet,
                MAX_THRESHOLD,
                new AntichainForestRegistry<>(nfa, simRels.toArray(new BitSet[0])));
            final CompactNFA<Integer> revrev = new CompactNFA<>(alphabet);
            NFAs.reverse(revDet, alphabet, revrev);
            final CompactDFA<Integer> result = PowersetDeterminizer.determinize(revrev, alphabet, false);

            Assertions.assertEquals(dfa.size(), result.size());
            Assertions.assertTrue(Automata.testEquivalence(dfa, result, alphabet));
        }
    }
}
