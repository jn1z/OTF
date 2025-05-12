package OTF;

import net.automatalib.alphabet.Alphabet;
import net.automatalib.automaton.fsa.impl.CompactDFA;
import net.automatalib.automaton.fsa.impl.CompactNFA;
import net.automatalib.util.automaton.Automata;
import net.automatalib.util.automaton.fsa.NFAs;
import net.automatalib.util.partitionrefinement.Valmari;
import net.automatalib.util.partitionrefinement.ValmariExtractors;
import net.automatalib.util.partitionrefinement.ValmariInitializers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("IntegTest")
public class PaigeTarjanNFAIntegTest {
    @Test
    void testNFAReduction() {
        for (int size = 5; size < 15; size++) {
            for (int randomSeed = 0; randomSeed < 1000; randomSeed++) {
                CompactNFA<Integer> automaton = TabakovVardiRandomNFA.getRandomAutomaton(randomSeed, size, CompactNFA::new);
                CompactNFA<Integer> trim = NFATrim.trim(automaton);
                assertDFAAndPreMinDFA(trim, size, randomSeed);
            }
        }
    }

    private static void assertDFAAndPreMinDFA(CompactNFA<Integer> nfa, int size, int seed) {
        String debug = seed + "; " + size;
        Alphabet<Integer> alphabet = nfa.getInputAlphabet();
        CompactDFA<Integer> dfa = NFAs.determinize(nfa, alphabet);

        final Valmari valmari = ValmariInitializers.initializeNFA(nfa, alphabet);
        valmari.computeCoarsestStablePartition();
        final CompactNFA<Integer> nfaPT = ValmariExtractors.toNFA(valmari, nfa, alphabet);
        CompactDFA<Integer> nfaPTDfa = NFAs.determinize(nfaPT, alphabet);
        Assertions.assertTrue(Automata.testEquivalence(dfa, nfaPTDfa, dfa.getInputAlphabet()), debug);
    }
}
