package OTF;

import net.automatalib.alphabet.Alphabet;
import net.automatalib.alphabet.impl.Alphabets;
import net.automatalib.automaton.AutomatonCreator;
import net.automatalib.automaton.concept.InputAlphabetHolder;
import net.automatalib.automaton.fsa.MutableNFA;
import net.automatalib.automaton.fsa.impl.CompactNFA;
import net.automatalib.automaton.fsa.impl.CompactNFA.Creator;
import net.automatalib.common.util.random.RandomUtil;

import java.util.Random;

public class TabakovVardiRandomNFA {
    /**
     * Generate random NFA using Tabakov and Vardi's approach, described in the paper
     * <a href="https://doi.org/10.1007/11591191_28">Experimental Evaluation of Classical Automata Constructions</a>
     * by Deian Tabakov and Moshe Y. Vardi.
     *
     * @param r
     *      random instance
     * @param size
     *      number of states
     * @param td
     *      transition density, in [0,size]
     * @param ad
     *      acceptance density, in (0,1]. 0.5 is the usual value
     * @param alphabet
     *      alphabet
     * @return
     *      a random NFA, not necessarily connected
     */
    public static <A extends MutableNFA<Integer, Integer>> A generateNFA(
            Random r, int size, float td, float ad, Alphabet<Integer> alphabet, AutomatonCreator<A, Integer> creator) {
        return generateNFA(r, size, Math.round(td * size), Math.round(ad * size), alphabet, creator);
    }

    /**
     * Generate random NFA, with fixed number of accept states and edges (per letter).
     * @param r
     *      random instance
     * @param size
     *      number of states
     * @param edgeNum
     *      number of edges (per letter)
     * @param acceptNum
     *      number of accepting states (at least one)
     * @param alphabet
     *      alphabet
     * @return
     *      a random NFA, not necessarily connected
     */
    public static <A extends MutableNFA<Integer, Integer>> A generateNFA(
            Random r, int size, int edgeNum, int acceptNum, Alphabet<Integer> alphabet, AutomatonCreator<A, Integer> creator) {
        assert acceptNum > 0 && acceptNum <= size;
        assert edgeNum >= 0 && edgeNum <= size*size;

        A result = basicNFA(size, alphabet, creator);

        // Set final states other than the initial state.
        // We want exactly acceptNum-1 of them, from the elements [1,size).
        // This works even if acceptNum == 1.
        int[] finalStates = RandomUtil.distinctIntegers(r, acceptNum - 1, 1, size);
        for (int f : finalStates) {
            result.setAccepting(f, true);
        }

        // For each letter, add edgeNum transitions.
        for (int a: alphabet) {
            for (int edgeIndex: RandomUtil.distinctIntegers(r, edgeNum, size*size)) {
                result.addTransition(edgeIndex / size, a, edgeIndex % size);
            }
        }

        return result;
    }

    static <A extends MutableNFA<Integer, Integer>> A basicNFA(int size, Alphabet<Integer> alphabet, AutomatonCreator<A, Integer> creator) {
        A result = creator.createAutomaton(alphabet);

        // Create states
        for (int i = 0; i < size; i++) {
            result.addState(false);
        }
        // per the paper, the first state is always initial and accepting
        result.setInitial(0, true);
        result.setAccepting(0, true);
        return result;
    }

    public static CompactNFA<Integer> getRandomAutomaton(int randomSeed, int size) {
        return getRandomAutomaton(randomSeed, size, new Creator<>());
    }

    public static <A extends MutableNFA<Integer, Integer>> A getRandomAutomaton(int randomSeed, int size, AutomatonCreator<A, Integer> creator) {
        final float td = 1.25f; // 1.25f
        final float ad = 0.5f;
        final Random random = new Random(randomSeed);
        final Alphabet<Integer> alphabet = Alphabets.integers(0, 1);
        return generateNFA(random, size, td, ad, alphabet, creator);
    }

    public static <A extends MutableNFA<Integer, Integer> & InputAlphabetHolder<Integer>> A getRandomTrimAutomaton(int randomSeed, int size, AutomatonCreator<A, Integer> creator) {
        A automaton = getRandomAutomaton(randomSeed, size, creator);
        return NFATrim.trim(automaton, creator);
    }

    // n_NFA:75, n_DFA:63483, n_minDFA:70 : 900x reduction
    public static <A extends MutableNFA<Integer, Integer> & InputAlphabetHolder<Integer>> A TV900XExample(AutomatonCreator<A, Integer> creator) {
        return TabakovVardiRandomNFA.getRandomTrimAutomaton(242, 100, creator);
    }

    // n_NFA:130, n_DFA:1604756, n_minDFA:1092 : 1500x reduction
    public static <A extends MutableNFA<Integer, Integer> & InputAlphabetHolder<Integer>> A TVSlow1500XExample(AutomatonCreator<A, Integer> creator) {
        return TabakovVardiRandomNFA.getRandomTrimAutomaton(2, 150, creator);
    }

    // n_NFA:142, n_DFA:3003954, n_minDFA:1901 : 1580x reduction
    public static <A extends MutableNFA<Integer, Integer> & InputAlphabetHolder<Integer>> A TVSlow1600XExample(AutomatonCreator<A, Integer> creator) {
        return TabakovVardiRandomNFA.getRandomTrimAutomaton(46, 165, creator);
    }

    // n_NFA:143, n_DFA:1852771, n_minDFA:727 : 2548x reduction
    public static <A extends MutableNFA<Integer, Integer> & InputAlphabetHolder<Integer>> A TVSlow2500XExample(AutomatonCreator<A, Integer> creator) {
        return TabakovVardiRandomNFA.getRandomTrimAutomaton(52, 165, creator);
    }
}

