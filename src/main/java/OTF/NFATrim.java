package OTF;

import net.automatalib.alphabet.Alphabet;
import net.automatalib.automaton.AutomatonCreator;
import net.automatalib.automaton.concept.InputAlphabetHolder;
import net.automatalib.automaton.fsa.DFA;
import net.automatalib.automaton.fsa.MutableNFA;
import net.automatalib.automaton.fsa.impl.CompactNFA;
import net.automatalib.util.automaton.fsa.NFAs;
import net.automatalib.util.partitionrefinement.Valmari;
import net.automatalib.util.partitionrefinement.ValmariExtractors;
import net.automatalib.util.partitionrefinement.ValmariInitializers;

import java.util.Set;

public class NFATrim {
    public static <I> CompactNFA<I> trim(CompactNFA<I> nfa) {
        return trim(nfa, CompactNFA::new);
    }

    public static <I, A extends MutableNFA<Integer, I> & InputAlphabetHolder<I>> A trim(A nfa, AutomatonCreator<A, I> creator) {
        final Alphabet<I> alphabet = nfa.getInputAlphabet();
        final A automaton = creator.createAutomaton(alphabet);
        return NFAs.trim(nfa, alphabet, automaton);
    }

    public static <I> CompactNFA<I> reverse(CompactNFA<I> nfa) {
        return reverse(nfa, CompactNFA::new);
    }

    public static <I, A extends MutableNFA<Integer, I> & InputAlphabetHolder<I>> A reverse(A nfa, AutomatonCreator < A, I > creator) {
        final Alphabet<I> alphabet = nfa.getInputAlphabet();
        final A automaton = creator.createAutomaton(alphabet, nfa.size());
        NFAs.reverse(nfa, alphabet, automaton);
        return automaton;
    }

    public static <I> CompactNFA<I> reverse(DFA<Integer, I> dfa, Alphabet<I> alphabet) {
        return reverse(dfa, alphabet, new CompactNFA.Creator<>());
    }

    public static <I, A extends MutableNFA<Integer, I>> A reverse(DFA<Integer, I> dfa, Alphabet<I> alphabet, AutomatonCreator<A, I> creator) {
        A rNFA = creator.createAutomaton(alphabet, dfa.size());
        Set<Integer> initialStates = dfa.getInitialStates();

        // Accepting are initial states and vice versa
        for(int i: dfa.getStates()) {
            rNFA.addState(initialStates.contains(i));
            if (dfa.isAccepting(i)) {
                rNFA.setInitial(i, true);
            }
        }
        // reverse transitions
        for(int q: dfa.getStates()) {
            for(I a: alphabet) {
                Integer trans = dfa.getTransition(q, a);
                if (trans != null) {
                    rNFA.addTransition((int)trans, a, q);
                }
            }
        }
        return rNFA;
    }



    public static <I> CompactNFA<I> bisim(CompactNFA<I> reduced) {
        CompactNFA<I> result = reduced;
        if (reduced.size() > 1) {
            int prevReduced = reduced.size();
            // usually faster to reduce via bisimilarity first, per EBEC paper
            result = bisimReduce(result,true);
            if (result.size() > 1) {
                result = bisimReduce(result, false);
            }
            if (result.size() != prevReduced) {
                //System.out.println("Bisimulation forward/backward reduced to:" + result.size());
            }
        }
        return result;
    }

    private static <I> CompactNFA<I> bisimReduce(CompactNFA<I> reduced, boolean forward) {
        CompactNFA<I> cnfa = reduced;
        if (!forward) {
            cnfa = NFATrim.reverse(cnfa);
        }
        Valmari prb = ValmariInitializers.initializeNFA(cnfa);
        prb.computeCoarsestStablePartition();
        cnfa = ValmariExtractors.toNFA(prb, cnfa, cnfa.getInputAlphabet(), false, CompactNFA::new);
        if (cnfa.size() < reduced.size()) {
            // There's an actual reduction
            if (!forward) {
                cnfa = NFATrim.reverse(cnfa);
            }
            return cnfa;
        }
        return reduced;
    }

}
