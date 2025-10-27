package OTF.Model;

import java.util.BitSet;

import net.automatalib.alphabet.Alphabet;
import net.automatalib.automaton.AutomatonCreator;
import net.automatalib.automaton.fsa.impl.CompactNFA;
import net.automatalib.ts.AcceptorPowersetViewTS;

public class CompactImpl<I> extends CompactNFA<I> implements SupportsCompactPowerset<Integer, I> {

    public CompactImpl(Alphabet<I> alphabet, int stateCapacity) {
        super(alphabet, stateCapacity);
    }

    public CompactImpl(Alphabet<I> alphabet) {
        super(alphabet);
    }

    public CompactImpl(CompactNFA<I> other) {
        super(other);
    }

    protected CompactImpl(Alphabet<I> alphabet, CompactNFA<?> other) {
        super(alphabet, other);
    }

    @Override
    public AcceptorPowersetViewTS<BitSet, I, Integer> compactPowersetView() {
        return powersetView();
    }

    public static final class Creator<I> implements AutomatonCreator<SupportsCompactPowerset<?, I>, I> {
        public CompactImpl<I> createAutomaton(Alphabet<I> alphabet, int numStates) {
            return new CompactImpl<>(alphabet, numStates);
        }

        public CompactImpl<I> createAutomaton(Alphabet<I> alphabet) {
            return new CompactImpl<>(alphabet);
        }
    }
}
