package OTF.Registry;

import java.util.BitSet;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.automatalib.common.util.array.ArrayStorage;

public class AddressRegistry implements Registry {
    private final Object2IntMap<BitSet> key2Address;
    private final IntList representatives;
    private final ArrayStorage<IntList> rep2Address;

    public AddressRegistry() {
        this.key2Address = new Object2IntOpenHashMap<>();
        this.key2Address.defaultReturnValue(MISSING_ELEMENT); // if missing, return MISSING_ELEMENT
        this.representatives = new IntArrayList();
        this.rep2Address = new ArrayStorage<>();
    }

    @Override
    public int get(BitSet equivClassElt) {
        int address = key2Address.getInt(equivClassElt);
        return address < 0 ? address : this.representatives.getInt(address);
    }

    @Override
    public void put(BitSet equivClassElt, int stateID) {
        int address = this.representatives.size();
        this.key2Address.put(equivClassElt, address);
        this.representatives.add(stateID);

        final IntList value = new IntArrayList();
        value.add(address);
        this.rep2Address.ensureCapacity(stateID + 1);
        this.rep2Address.set(stateID, value);
    }

    @Override
    public void unify(int primary, int secondary) {
        for (int address : this.rep2Address.get(secondary)) {
            this.representatives.set(address, primary);
            this.rep2Address.get(primary).add(address);
        }
    }

    @Override
    public String toString() {
        return "OTF1";
    }
}
