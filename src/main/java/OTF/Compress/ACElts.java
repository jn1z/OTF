package OTF.Compress;

import OTF.SmartBitSet;

import java.util.*;

/**
 * This represents an AC, i.e., the lower boundary of the ACPlus.
 */
public final class ACElts {
    List<SmartBitSet> elts = new ArrayList<>(2);

    // small-elt (non-InvertedIndex) fields
    boolean isSorted = true; // on-demand sorting, used for isSubset short-circuit

    // large-elt (InvertedIndex) fields
    private final SmartBitSet deadElts = new SmartBitSet(); // re-use of elements, for performance -- only used by InvertedIndex
    InvertedIndex invertedIndex = null;
    private static final int MAX_DEAD_ELTS = 400; // maximum dead elts before we just rebuild
    private static final int MIN_INVERTED_INDEX_SIZE = 32; // size before we start using an inverted index

    public ACElts(SmartBitSet elt) {
        this.elts.add((SmartBitSet)elt.clone());
    }

    public int getEltsSize() {
        return this.elts.size() - this.deadElts.cardinality();
    }

    /**
     * Is there an element of this ACElts that's a subset of newElt ?
     */
    boolean properSubsetExists(SmartBitSet newElt) {
        if (invertedIndex != null) {
            return invertedIndex.findFirstSubset(this.elts, this.deadElts, newElt) != null;
        }
        if (!isSorted) {
            this.elts.sort(SmartBitSet.SMART_CARDINALITY_COMPARATOR);
            isSorted = true;
        }
        final int newEltCard = newElt.cardinality();
        for (SmartBitSet oldElt : this.elts) {
            if (oldElt.cardinality() >= newEltCard) {
                break; // oldElt is larger (or equal in size) to newElt, can't be a subset
                // if equal, it would have been found already
            }
            if (oldElt.isSubset(newElt)) {
                // newElt is a superset of oldElt, thus in the AC
                return true;
            }
        }
        return false;
    }

    /**
     * Get live elements.
     */
    List<SmartBitSet> getLiveElts() {
        final int eltsSize = this.elts.size();
        final List<SmartBitSet> allElts = new ArrayList<>(eltsSize - this.deadElts.cardinality());
        for (int i = deadElts.nextClearBit(0);
             i >= 0 && i < eltsSize; i = deadElts.nextClearBit(i + 1)) {
            allElts.add(this.elts.get(i));
        }
        return allElts;
    }

    /**
     * Unify single elt into this AC.
     */
    final boolean unifyEltIntoAC(SmartBitSet newElt, int nNFA, int sizeHint) {
        // unifySubsetOrReplaceElt() can alter deadElts
        if (!this.unifySubsetOrReplaceElt(newElt, nNFA)) {
            return false;
        }
        // we add elements here, to avoid re-checking if elements to add are ACs of each other
        this.isSorted = false; // most likely, this element will change sorting.
        if (this.invertedIndex == null) {
            this.elts.add((SmartBitSet) newElt.clone());
            if (this.elts.size() > MIN_INVERTED_INDEX_SIZE) {
                this.invertedIndex = new InvertedIndex(nNFA, this.elts);
            }
        } else {
            if (!this.deadElts.isEmpty()) {
                // Re-use a dead element.
                final int i = this.deadElts.nextSetBit(0);
                this.invertedIndex.overwrite(newElt, this.elts.get(i), i);
                this.elts.set(i, (SmartBitSet) newElt.clone());
                this.deadElts.clear(i);
            } else {
                // insert a new element
                this.invertedIndex.insert(newElt, sizeHint);
                this.elts.add((SmartBitSet) newElt.clone());
            }
        }
        return true;
    }

    /**
     * There are a lot of dead elements. Just rebuild the InvertedIndex.
     */
    private void rebuildInvertedIndex(int nNFA) {
        final List<SmartBitSet> rebuildElts = getLiveElts();
        this.deadElts.clear();
        this.elts = rebuildElts;
        // we don't keep elts sorted after this, but this is useful for a build/rebuild
        this.elts.sort(SmartBitSet.SMART_CARDINALITY_COMPARATOR);
        isSorted = true;
        this.invertedIndex = new InvertedIndex(nNFA, rebuildElts);
    }

    /**
     * Check newElt against current ACElts. Ignore, replace, or add to eltsToAdd.
     */
    private boolean unifySubsetOrReplaceElt(SmartBitSet newElt, int nNFA) {
        if (this.invertedIndex != null) {
            if(invertedIndex.findFirstSubset(this.elts, this.deadElts, newElt) != null) {
                return false; // already contained in an AC element; ignore
            }
            final SmartBitSet supersets = invertedIndex.findSupersetIndices(this.elts, this.deadElts, newElt, false);
            if (!supersets.isEmpty()) {
                // newElt is a subset of some current elements. Replace one; the rest are now dead.
                final int idxToReplace = supersets.nextSetBit(0);
                this.invertedIndex.overwrite(newElt, this.elts.get(idxToReplace), idxToReplace);
                this.elts.set(idxToReplace, (SmartBitSet) newElt.clone());
                for (int i = supersets.nextSetBit(idxToReplace+1); i >= 0; i = supersets.nextSetBit(i + 1)) {
                    this.deadElts.set(i);
                }
                if (this.deadElts.cardinality() > MAX_DEAD_ELTS) {
                    rebuildInvertedIndex(nNFA);
                }
                return false;
            }
        } else {
            final int eltSize = elts.size();
            for (int i = 0; i < eltSize; i++) {
                final SmartBitSet currentElt = this.elts.get(i);
                if (currentElt.isSubset(newElt)) {
                    return false; // already contained in an AC element; ignore
                }
                if (newElt.isSubset(currentElt)) {
                    // newElt is a subset of at least one AC element. Replace it.
                    this.replaceAC(newElt, i);
                    return false;
                }
            }
        }
        // not related to any AC element. Prepare to add a new element.
        return true;
    }

    /**
     * Replace all current elements that are supersets of newElt.
     */
    private void replaceAC(SmartBitSet newElt, int i) {
        // subset of the current AC element. Replace.
        this.elts.set(i, (SmartBitSet) newElt.clone());
        final ListIterator<SmartBitSet> iterator = this.elts.listIterator(i+1);
        while (iterator.hasNext()) {
            final SmartBitSet oldElt = iterator.next();
            if (newElt.isSubset(oldElt)) {
                iterator.remove();
            }
        }
        this.isSorted = false;
    }

    boolean minCardinality(int minCardinality) {
        final int eltsSize = this.elts.size();
        for (int i = deadElts.nextClearBit(0); i >= 0 && i < eltsSize;
             i = deadElts.nextClearBit(i + 1)) {
            if (this.elts.get(i).cardinality() <= minCardinality) {
                return true;
            }
        }
        return false;
    }

    void clear() {
        elts.clear();
        deadElts.clear();
        if (this.invertedIndex != null) {
            this.invertedIndex.clear();
            this.invertedIndex = null;
        }
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder();
        List<SmartBitSet> tempElts = new ArrayList<>(elts);
        tempElts.sort(SmartBitSet.SMART_CARDINALITY_COMPARATOR);
        for(SmartBitSet elt: tempElts) {
            sb.append(elt).append(" , ");
        }
        return sb.toString();
    }
}
