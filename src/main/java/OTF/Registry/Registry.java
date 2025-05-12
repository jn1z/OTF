package OTF.Registry;

import java.util.BitSet;

public interface Registry {
    int MISSING_ELEMENT = -1;

    /**
     * Get representative state ID from the equivalence class.
     * @param equivClassElt element of equivalence class
     * @return representative state ID or MISSING_ELEMENT if equivalence class not found.
     */
    int get(BitSet equivClassElt);

    /**
     * Add new equivalence class, with (fixed) representative state ID.
     * @param equivClassElt equivalence class element
     * @param stateID state ID
     */
    void put(BitSet equivClassElt, int stateID);

    /**
     * Merge element of equivalence class into the equivalence class representative.
     * @param primary representative
     * @param secondary element of equivalence class
     */
    void unify(int primary, int secondary);

    /**
     * Merge elements of equivalence class into the equivalence class representative.
     * This is more efficient than doing them individually, due to lookups and so forth.
     * @param primary representative
     * @param secondaries elements of equivalence class
     */
    default void unify(int primary, BitSet secondaries) {
        for (int k = secondaries.nextSetBit(0); k >= 0; k = secondaries.nextSetBit(k + 1)) {
            unify(primary, k);
        }
    }

    /**
     * Compress the AC structures after an incremental minimization completes.
     */
    default void compress() {};

    // Only used for debugging and analysis, not part of the essential interface
    default int getMaxIntermediateCount() { return -1; }
}


