package OTF;

import java.util.BitSet;
import java.util.Collection;

public class BitSetUtils {
    /**
     * Determine is sub is a subset of sup.
     */
    public static boolean isSubset(BitSet sub, BitSet sup) {
        for(int i=sub.nextSetBit(0);i>=0;i=sub.nextSetBit(i+1)) {
            if(!sup.get(i)) {
                return false;
            }
        }
        return true;
    }
}
