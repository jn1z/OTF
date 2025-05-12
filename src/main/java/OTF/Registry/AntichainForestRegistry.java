package OTF.Registry;

import OTF.Compress.AntichainForest;
import net.automatalib.automaton.fsa.impl.CompactNFA;

import java.util.BitSet;

public class AntichainForestRegistry<I> implements Registry {
    public final AntichainForest acf;
    private final boolean simulation;

    /*
    Note: we do not use the actual NFA, only its size (to bound BitSets and inverted indices)
     */
    public AntichainForestRegistry(CompactNFA<Integer> nfa, BitSet[] simRelsArr) {
        this.acf = new AntichainForest(nfa, simRelsArr);
        this.simulation = simRelsArr.length > 0;
    }
    public AntichainForestRegistry(CompactNFA<Integer> nfa) {
        this(nfa, new BitSet[0]);
    }

    public void compress() {
        this.acf.compress();
    }

    @Override
    public int get(BitSet equivClassElt) {
        return this.acf.get(equivClassElt);
    }

    @Override
    public void put(BitSet equivClassElt, int stateID) {
        this.acf.put(equivClassElt, stateID);
    }

    @Override
    public void unify(int primary, int secondary) {
        throw new RuntimeException("Shouldn't be executed, use other unify()");
    }

    @Override
    public void unify(int primary, BitSet secondaries) {
        this.acf.unify(primary, secondaries);
    }

    @Override
    public int getMaxIntermediateCount() {
        return this.acf.maxIntermediateCount;
    }

    @Override
    public String toString() {
        return simulation ? "CCLS" : "CCL";
    }
}
