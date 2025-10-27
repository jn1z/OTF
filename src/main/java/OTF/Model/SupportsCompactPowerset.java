package OTF.Model;

import java.util.BitSet;

import net.automatalib.automaton.fsa.MutableNFA;
import net.automatalib.ts.AcceptorPowersetViewTS;

public interface SupportsCompactPowerset<S, I> extends MutableNFA<S, I> {

    AcceptorPowersetViewTS<BitSet, I, S> compactPowersetView();
}
