package OTF.Compress;

import OTF.SmartBitSet;

/**
 * This represents the additive semi-lattice associated with an equivalence class (stateId).
 * acElts are the lower boundary, and acUnion is the upper boundary.
 * For efficiency, small ACPlus sets are not fully searched; represented by the searchable field.
 */
public class ACPlus {
    public final ACElts acElts; // lower boundary of the convex set
    public final SmartBitSet acUnion; // upper boundary of the convex set
    private int stateId; // state associated with this equivalence class
    public boolean searchable=false; // If the ACPlus is large enough to justify searching

    public static final int MIN_CARDINALITY_SEARCHABLE_DIFF = 8;
    // cardinality between largest and smallest element(s)
    public static final int MIN_AC_SEARCHABLE = 5;
    // minimum element count before searchable

    ACPlus(int stateId, SmartBitSet firstElt) {
        this.stateId = stateId;
        this.acElts = new ACElts(firstElt);
        this.acUnion = (SmartBitSet) firstElt.clone();
    }

    public int getStateId() {
        return stateId;
    }

    public void setStateId(int nodeId) {
        this.stateId = nodeId;
    }

    /**
     * Determine if this ACPlus is large enough to justify searching
     */
    public void determineSearchable() {
        this.searchable = this.searchable || this.acElts.getEltsSize() > MIN_AC_SEARCHABLE;
        if (this.searchable) {
            return;
        }
        // If the cardinality difference is large, then this AC already represents a lot of elements
        int unionCard = this.acUnion.cardinality();
        if (unionCard <= MIN_CARDINALITY_SEARCHABLE_DIFF) {
            return;
        }
        this.searchable = this.acElts.minCardinality(unionCard - MIN_CARDINALITY_SEARCHABLE_DIFF);
    }

    public void unionOr(SmartBitSet elt) {
        this.acUnion.or(elt);
    }

    void clear() {
        this.searchable = false;
        this.acElts.clear();
        this.acUnion.clear();
    }

    @Override
    public String toString() {
      return "Node ID " + stateId + ": " + acElts + " : " + acUnion;
    }
}
