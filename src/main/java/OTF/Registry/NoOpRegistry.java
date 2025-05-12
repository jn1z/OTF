package OTF.Registry;

import java.util.BitSet;

public class NoOpRegistry implements Registry {
    @Override
    public int get(BitSet equivClassElt) {
        return 0;
    }

    @Override
    public void put(BitSet equivClassElt, int stateID) {
    }

    @Override
    public void unify(int primary, int secondary) {
    }

    @Override
    public String toString() {
        return "NoOp";
    }
}
