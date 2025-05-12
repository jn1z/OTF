package OTF;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import OTF.Model.Cancellation;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.automaton.fsa.MutableDFA;
import net.automatalib.automaton.fsa.impl.CompactDFA;
import net.automatalib.automaton.fsa.impl.CompactNFA;
import net.automatalib.ts.AcceptorPowersetViewTS;
import net.automatalib.util.automaton.minimizer.HopcroftMinimizer;

public class PowersetDeterminizer {
    private final Cancellation cancellation;

    public PowersetDeterminizer(Cancellation cancellation) {
        this.cancellation = cancellation;
    }

    public <I> CompactDFA<I> benchmark(CompactNFA<I> nfa, Alphabet<I> alphabet) {
        final CompactDFA<I> out = new CompactDFA<>(alphabet);
        doDeterminize(nfa.powersetView(), alphabet, out, this.cancellation);

        return out;
    }

    public static <I> CompactDFA<I> determinize(CompactNFA<I> nfa, Alphabet<I> alphabet) {
        return determinize(nfa, alphabet, true);
    }

    public static <I> CompactDFA<I> determinize(CompactNFA<I> nfa, Alphabet<I> alphabet, boolean minimize) {
        final CompactDFA<I> out = new CompactDFA<>(alphabet);
        doDeterminize(nfa.powersetView(), alphabet, out);
        if (minimize) {
            return HopcroftMinimizer.minimizeDFA(out, alphabet);
        }
        return out;
    }

    public static <I> MutableDFA<?, I> determinize(CompactNFA<I> nfa,
                                                   Alphabet<I> alphabet,
                                                   MutableDFA<?, I> out,
                                                   boolean minimize) {
        doDeterminize(nfa.powersetView(), alphabet, out);
        if (minimize) {
            return HopcroftMinimizer.minimizeDFA(out, alphabet);
        }
        return out;
    }

    private static <I, SI, SO> void doDeterminize(AcceptorPowersetViewTS<SI, I, ?> powerset,
                                                  Collection<? extends I> inputs,
                                                  MutableDFA<SO, I> out) {
        doDeterminize(powerset, inputs, out, new Cancellation());
    }

    private static <I, SI, SO> void doDeterminize(AcceptorPowersetViewTS<SI, I, ?> powerset,
                                                  Collection<? extends I> inputs,
                                                  MutableDFA<SO, I> out,
                                                  Cancellation cancellation) {

        Map<SI, SO> outStateMap = new HashMap<>();
        Deque<DeterminizeRecord<SI, SO>> stack = new ArrayDeque<>();

        SI init = powerset.getInitialState();
        boolean initAcc = powerset.isAccepting(init);
        SO initOut = out.addInitialState(initAcc);

        outStateMap.put(init, initOut);

        stack.push(new DeterminizeRecord<>(init, initOut));

        while (!stack.isEmpty() && !cancellation.isInterrupted() && !cancellation.isAboveThreshold(out.size())) {
            DeterminizeRecord<SI, SO> curr = stack.pop();

            SI inState = curr.inputState;
            SO outState = curr.outputState;

            for (I sym : inputs) {
                SI succ = powerset.getSuccessor(inState, sym);

                if (succ != null) {
                    SO outSucc = outStateMap.get(succ);
                    if (outSucc == null) {
                        // add new state to DFA and to stack
                        outSucc = out.addState(powerset.isAccepting(succ));
                        outStateMap.put(succ, outSucc);
                        stack.push(new DeterminizeRecord<>(succ, outSucc));
                    }
                    out.setTransition(outState, sym, outSucc);
                }
            }
        }
    }

    private record DeterminizeRecord<SI, SO>(SI inputState, SO outputState) { }
}
