package OTF;

import java.util.BitSet;
import java.util.Collection;

public class BitSetUtils {
    // Only for tests, not meant to be performant
    public static BitSet convertListToBitSet(Collection<Integer> list) {
        BitSet b = new BitSet();
        for(int i: list) {
            b.set(i);
        }
        return b;
    }
    public static SmartBitSet convertListToSmartBitSet(Collection<Integer> list) {
        SmartBitSet b = new SmartBitSet();
        for(int i: list) {
            b.set(i);
        }
        return b;
    }
}
