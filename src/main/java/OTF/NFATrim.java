package OTF;

import net.automatalib.alphabet.Alphabet;
import net.automatalib.alphabet.impl.Alphabets;
import net.automatalib.automaton.AutomatonCreator;
import net.automatalib.automaton.concept.InputAlphabetHolder;
import net.automatalib.automaton.fsa.DFA;
import net.automatalib.automaton.fsa.MutableNFA;
import net.automatalib.automaton.fsa.NFA;
import net.automatalib.automaton.fsa.impl.CompactNFA;
import net.automatalib.automaton.fsa.impl.FastNFA;
import net.automatalib.automaton.fsa.impl.FastNFAState;
import net.automatalib.common.util.HashUtil;
import net.automatalib.common.util.mapping.Mapping;
import net.automatalib.common.util.mapping.MutableMapping;
import net.automatalib.util.automaton.fsa.NFAs;
import net.automatalib.util.partitionrefinement.Valmari;
import net.automatalib.util.partitionrefinement.ValmariExtractors;
import net.automatalib.util.partitionrefinement.ValmariInitializers;

import java.util.Collection;
import java.util.HashSet;
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

    public static <S, I, A extends MutableNFA<?, I>> A trim(NFA<S, I> nfa, Alphabet<I> alphabet, AutomatonCreator<A, I> creator) {
        final MutableNFA<?, I> automaton = creator.createAutomaton(alphabet);
        return (A) trim2(nfa, alphabet, automaton);
    }

    public static <SI, I, SO, A extends MutableNFA<SO, I>> A trim2(NFA<SI, I> nfa,
                                                                  Collection<? extends I> inputs,
                                                                  A out) {
        final MutableMapping<SI, SO> mapping = nfa.createStaticStateMapping();
        final Set<SI> inits = nfa.getInitialStates();

        final Set<SI> states = NFAs.accessibleStates(nfa, inputs);
        states.retainAll(coaccessibleStates(nfa, inputs));

        for (SI s : states) {
            final SO so = out.addState(nfa.isAccepting(s));
            out.setInitial(so, inits.contains(s));
            mapping.put(s, so);
        }

        for (SI s : states) {
            for (I i : inputs) {
                for (SI t : nfa.getTransitions(s, i)) {
                    if (states.contains(t)) {
                        out.addTransition(mapping.get(s), i, mapping.get(t));
                    }
                }
            }
        }

        return out;
    }

    private static <S, I> Set<S> coaccessibleStates(NFA<S, I> nfa, Collection<? extends I> inputs) {

        final FastNFA<I> out = new FastNFA<>(Alphabets.fromCollection(inputs));
        final Mapping<FastNFAState, S> mapping = NFAs.reverse(nfa, inputs, out);
        final Set<FastNFAState> states = NFAs.accessibleStates(out, inputs);

        final Set<S> result = new HashSet<>(HashUtil.capacity(states.size()));

        for (FastNFAState s : states) {
            result.add(mapping.get(s));
        }

        return result;
    }

    public static <I> CompactNFA<I> reverse(CompactNFA<I> nfa) {
        return reverse(nfa, CompactNFA::new);
    }

    public static <S1, S2, I, A1 extends NFA<S1, I> & InputAlphabetHolder<I>, A2 extends MutableNFA<S2, I>> A2 reverse(A1 nfa, AutomatonCreator <A2, I> creator) {
        final Alphabet<I> alphabet = nfa.getInputAlphabet();
        final A2 automaton = creator.createAutomaton(alphabet, nfa.size());
        NFAs.reverse(nfa, alphabet, automaton);
        return automaton;
    }

    public static <S, I, A extends MutableNFA<?, I>> A reverse(NFA<S, I> nfa, Alphabet<I> alphabet, AutomatonCreator <A, I> creator) {
        final  MutableNFA<?, I> automaton = creator.createAutomaton(alphabet, nfa.size());
        NFAs.reverse(nfa, alphabet, automaton);
        return (A) automaton;
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
        return bisim(reduced, reduced.getInputAlphabet(), CompactNFA::new);
    }

    public static <S, I, A extends MutableNFA<?, I>> A bisim(A reduced, Alphabet<I> alphabet, AutomatonCreator<A, I> creator) {
        A result = reduced;
        if (reduced.size() > 1) {
            int prevReduced = reduced.size();
            // usually faster to reduce via bisimilarity first, per EBEC paper
            result = bisimReduce(result, alphabet, creator, true);
            if (result.size() > 1) {
                result = bisimReduce(result, alphabet, creator, false);
            }
            if (result.size() != prevReduced) {
                //System.out.println("Bisimulation forward/backward reduced to:" + result.size());
            }
        }
        return result;
    }

    private static <S, I, A extends MutableNFA<?, I>> A bisimReduce(A reduced, Alphabet<I> alphabet, AutomatonCreator<A, I> creator, boolean forward) {
        MutableNFA<?, I> cnfa = reduced;
        if (!forward) {
            cnfa = reverse(cnfa, alphabet, creator);
        }
        Valmari prb = ValmariInitializers.initializeNFA(cnfa, alphabet);
        prb.computeCoarsestStablePartition();
        cnfa = ValmariExtractors.toNFA(prb, cnfa, alphabet, false, (AutomatonCreator<MutableNFA<S,I>, I>) creator);
        if (cnfa.size() < reduced.size()) {
            // There's an actual reduction
            if (!forward) {
                cnfa = reverse(cnfa, alphabet, creator);
            }
            return (A) cnfa;
        }
        return reduced;
    }

}
