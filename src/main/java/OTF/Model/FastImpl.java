package OTF.Model;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;

import net.automatalib.alphabet.Alphabet;
import net.automatalib.automaton.concept.StateIDs;
import net.automatalib.automaton.fsa.impl.FastNFA;
import net.automatalib.automaton.fsa.impl.FastNFAState;
import net.automatalib.ts.AcceptorPowersetViewTS;

public class FastImpl<I> extends FastNFA<I> implements SupportsCompactPowerset<FastNFAState, I> {

    public FastImpl(Alphabet<I> inputAlphabet) {
        super(inputAlphabet);
    }

    @Override
    public AcceptorPowersetViewTS<BitSet, I, FastNFAState> compactPowersetView() {
        return new AcceptorPowersetViewTS<>() {

            final StateIDs<FastNFAState> stateIDs = FastImpl.super.stateIDs();

            @Override
            public Collection<FastNFAState> getOriginalStates(BitSet state) {
                return getOriginalTransitions(state);
            }

            @Override
            public Collection<FastNFAState> getOriginalTransitions(BitSet state) {
                final List<FastNFAState> result = new ArrayList<>(state.cardinality());

                for (int i = state.nextSetBit(0); i >= 0; i = state.nextSetBit(i + 1)) {
                    result.add(stateIDs.getState(i));
                }

                return result;
            }

            @Override
            public BitSet getTransition(BitSet state, I in) {
                final BitSet result = new BitSet();

                for (int i = state.nextSetBit(0); i >= 0; i = state.nextSetBit(i + 1)) {
                    for (FastNFAState t : FastImpl.super.getTransitions(stateIDs.getState(i), in)) {
                        result.set(stateIDs.getStateId(FastImpl.super.getSuccessor(t)));
                    }
                }

                return result;
            }

            @Override
            public boolean isAccepting(BitSet state) {

                for (int i = state.nextSetBit(0); i >= 0; i = state.nextSetBit(i + 1)) {
                    if (FastImpl.super.isAccepting(stateIDs.getState(i))) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public BitSet getInitialState() {
                final BitSet result = new BitSet();
                for (FastNFAState s : FastImpl.super.getInitialStates()) {
                    result.set(stateIDs.getStateId(s));
                }
                return result;
            }
        };
    }
}
