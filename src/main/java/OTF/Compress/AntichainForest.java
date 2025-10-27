package OTF.Compress;

import java.util.*;

import OTF.*;
import OTF.Registry.Registry;
import net.automatalib.automaton.fsa.NFA;

/**
 * Antichain Disjoint-Set Forest, representing equivalence classes.
 * 1-element equivalence classes are simply represented as BitSets <-> ints.
 * Larger equivalence classes are represented by ACPlus elements.
 */
public final class AntichainForest {
    public ACGlobals acG;
    public static final int MISSING_ELEMENT = Registry.MISSING_ELEMENT;
    public int curIntermediateCount = 0; // used for metrics
    public int maxIntermediateCount = 0; // used for metrics

    public AntichainForest(NFA<?, Integer> nfa, BitSet[] simSupers) {
        this.acG = new ACGlobals(nfa.size(), simSupers);
    }

    public void compress() {
        // Rebuild an inverted index for the AC Unions.

        // first, save peak memory
        acG.searchableACsList = null;
        acG.searchableACsUnions.clear();
        acG.ACsInvertedIndex.clear();

        final List<ACPlus> nextACs = new ArrayList<>(acG.getAllACs().size());
        // inverted-index ACs first
        for (ACPlus acPlus : acG.getAllACs()) {
            if (acPlus.searchable) {
                nextACs.add(acPlus);
            }
        }
        // Note: here we don't ignore dead elts. They're useful to indicate complexity of InvertedIndex.
        // TODO: Another option would be adding a "hit count", and sort by that (also an extra searchable condition)
        nextACs.sort(Comparator.<ACPlus>comparingInt(a -> a.acElts.elts.size()).reversed()
                .thenComparingInt(ACPlus::getStateId));  // stable tiebreak

        int index = 0;
        acG.searchableACsList = new ACPlus[nextACs.size()];
        for (ACPlus acPlus: nextACs) {
            acG.searchableACsUnions.add(acPlus.acUnion);
            acG.searchableACsList[index++] = acPlus;
        }
        nextACs.clear(); // hint to save peak memory
        acG.ACsInvertedIndex = new InvertedIndex(acG.nNFA, acG.searchableACsUnions);
    }

    /**
     * Put BitSet at state.
     */
    public void put(BitSet newEltFull, int newState) {
        // It might make sense to just merge this with "get", e.g., "getOrPut"
        // rather than doing a separate find to determine where the new ACPlus belongs.

        // This creates a new equivalence class, so increment intermediate count
        this.curIntermediateCount++;
        if (this.curIntermediateCount > this.maxIntermediateCount) {
            this.maxIntermediateCount = this.curIntermediateCount;
        }
        final SmartBitSet smartEltFull = SmartBitSet.valueOf(newEltFull.toLongArray());
        final SmartBitSet newElt = acG.simAccelerate.shouldAccelerate ?
            acG.simAccelerate.pruneEltWithSims(smartEltFull) : smartEltFull;

        // Add a 1-element equivalence class. Larger equivalence classes are added in unify.
        acG.stateIdToSingleEquiv.put(newState, newElt);
        acG.singleEquivToStateId.put(newElt, newState);
    }

    /**
     * Get BitSet equivalence class (state), otherwise return MISSING_ELEMENT.
     */
    public int get(BitSet eltFull) {
        final SmartBitSet smartEltFull = SmartBitSet.valueOf(eltFull.toLongArray());
        final SmartBitSet prunedElt = acG.simAccelerate.shouldAccelerate ?
            acG.simAccelerate.pruneEltWithSims(smartEltFull) : smartEltFull;

        // Check 1-element equivalence classes first
        final int singleEquivState = acG.singleEquivToStateId.getInt(prunedElt);
        if (singleEquivState != MISSING_ELEMENT) {
            return singleEquivState;
        }

        // Check AC equivalence classes
        // First, we search the foundSets
        final ACPlus foundAC = acG.foundSets.get(prunedElt);
        if (foundAC != null) {
            return foundAC.getStateId();
        }

        return find(prunedElt);
    }

    /**
     * Find equivalence class in ACs.
     * @param prunedElt - pruned element
     * @return - equivalence class (state number) if it exists, otherwise MISSING_ELEMENT
     */
    int find(SmartBitSet prunedElt) {
        // Now we search the ACs.
        // For performance, we only search ACs above a given size -- "visible" ACs
        // We may miss some matching ACs, but this is better performance-wise.

        // Find all unions that are supersets of this element
        // This is just an approximate filter -- we might find extras
        final SmartBitSet potentialSupersets = acG.ACsInvertedIndex.findSupersetIndices(
            acG.searchableACsUnions, SmartBitSet.EMPTY_SMART_BITSET, prunedElt, true);

        if (!potentialSupersets.isEmpty()) {
            // Saturate sElt to use in step 2
            final SmartBitSet saturatedElt = acG.simAccelerate.shouldAccelerate ?
                acG.simAccelerate.saturateEltWithSims(prunedElt) : prunedElt;

            // For each AC found above:
            // 1. validate its union is an actual superset
            // 2. validate that saturatedElt is a (proper) superset of one of the elts of the AC
            //    (if it was equal, it would have been found in caches earlier)
            for (int i = potentialSupersets.nextSetBit(0); i >= 0; i = potentialSupersets.nextSetBit(i + 1)) {
                // Step 1
                final SmartBitSet acUnion = acG.searchableACsUnions.get(i);
                if (!prunedElt.isSubset(acUnion)) {
                    continue;
                }

                // Step 2
                final ACPlus acPlus = acG.searchableACsList[i];
                if (acPlus.acElts.properSubsetExists(saturatedElt)) {
                    acG.addToFoundSets(acPlus, prunedElt); // cache for the next search
                    return acPlus.getStateId();
                }
            }
        }

        return MISSING_ELEMENT;
    }

    /**
     * Unify primary with list of secondaries.
     * This looks complex, but mostly it's just handling 1-element equivalence classes differently.
     */
    public void unify(int primary, BitSet secondaries) {
        // TODO: consider unifying small ACs first, rather than testing everything against the largest AC.
        //   (Testing seems to show that small unifies are slower, though.)
        // Find out if these represent 1-element classes or ACs.
        final SmartBitSet primaryElt = acG.stateIdToSingleEquiv.remove(primary);
        ACPlus primaryAC = acG.getACPlus(primary, primaryElt);

        int tempIntermediateCount = -(primaryAC == null ? 1 : primaryAC.acElts.getEltsSize());

        final int maxSize = secondaries.cardinality();
        final List<ACPlus> secondaryACs = new ArrayList<>(maxSize); // ACs to be unified
        final List<SmartBitSet> secondaryEltsWithoutAC = new ArrayList<>(maxSize); // 1-elts to be unified
        tempIntermediateCount = determineSecondaryACsandElts(
            secondaries, tempIntermediateCount, secondaryEltsWithoutAC, secondaryACs);

        int eltsToUnifySize = secondaryEltsWithoutAC.size();
        for (ACPlus secondaryAC: secondaryACs) {
            eltsToUnifySize += secondaryAC.acElts.getEltsSize();
        }

        // determine this before we clear all of the secondary AC unions
        SmartBitSet newUnion = newUnion(secondaryACs, secondaryEltsWithoutAC);

        final List<SmartBitSet> eltsToUnify = new ArrayList<>(eltsToUnifySize + 1);
        eltsToUnify.addAll(secondaryEltsWithoutAC);
        primaryAC = determineOrCreatePrimaryAC(primary, primaryAC, secondaryACs, primaryElt, eltsToUnify);

        acG.addToFoundSets(primaryAC, secondaryEltsWithoutAC);

        unifyEltsIntoPrimaryAC(eltsToUnify, primaryAC);

        updateACUnion(primaryAC, newUnion);

        tempIntermediateCount += primaryAC.acElts.getEltsSize();

        this.curIntermediateCount += tempIntermediateCount; // no need to check max value
    }

    private void updateACUnion(ACPlus primaryAC, SmartBitSet newUnion) {
        final int previousUnionCardinality = primaryAC.acUnion.cardinality();
        primaryAC.unionOr(newUnion);
        // saturate is relatively slow; only do once per unify and only on change
        if (acG.simAccelerate.shouldAccelerate && primaryAC.acUnion.cardinality() > previousUnionCardinality) {
            primaryAC.unionOr(acG.simAccelerate.saturateEltWithSims(primaryAC.acUnion));
        }
    }
    private SmartBitSet newUnion(List<ACPlus> secondaryACs, List<SmartBitSet> secondaryEltsWithoutAC) {
        final SmartBitSet newUnion = new SmartBitSet();
        for (ACPlus secondaryAC: secondaryACs) {
            newUnion.or(secondaryAC.acUnion);
        }
        for (SmartBitSet secondaryEltWithoutAC : secondaryEltsWithoutAC) {
            newUnion.or(secondaryEltWithoutAC);
        }
        return newUnion;
    }

    /**
     * Unify elements into primary AC
     */
    private void unifyEltsIntoPrimaryAC(List<SmartBitSet> eltsToUnify, ACPlus primaryAC) {
        eltsToUnify.sort(SmartBitSet.SMART_CARDINALITY_COMPARATOR);
        // sorted, so smallest (most impactful) elements are unified first

        int sizeHint = eltsToUnify.size(); // performance hint
        if (primaryAC.acElts.invertedIndex != null) {
            sizeHint += primaryAC.acElts.invertedIndex.maxElts;
        }
        for (SmartBitSet eltToUnify: eltsToUnify) {
            primaryAC.acElts.unifyEltIntoAC(eltToUnify, acG.nNFA, sizeHint);
        }

        primaryAC.determineSearchable();
    }

    // 3 cases based on primary being 1-elt or AC, and secondaries being 1-elt or AC
    private ACPlus determineOrCreatePrimaryAC(
        int primary, ACPlus primaryAC, List<ACPlus> secondaryACs, SmartBitSet primaryElt, List<SmartBitSet> eltsToUnify) {
        if (primaryAC == null) {
            // primary is 1-elt
            if (secondaryACs.isEmpty()) {
                // Everything is a 1-elt set. Create a new AC
                primaryAC = new ACPlus(primary, primaryElt);
                acG.allACs.add(primaryAC);
            } else {
                // point to secondary AC
                primaryAC = secondaryACs.get(0);
                primaryAC.setStateId(primary);
                eltsToUnify.add(primaryElt);
                // determine secondary AC elts to unify (except the first one, which is now the primary)
                determineSecondaryACEltsToUnify(primary, secondaryACs, eltsToUnify, primaryAC, 1);
            }
            acG.stateIdToAC.put(primary, primaryAC);
            acG.addToFoundSets(primaryAC, primaryElt);
        } else {
            // determine secondary AC elts to unify
            determineSecondaryACEltsToUnify(primary, secondaryACs, eltsToUnify, primaryAC, 0);
        }
        return primaryAC;
    }

    private int determineSecondaryACsandElts(
        BitSet secondaries, int tempIntermediateCount, List<SmartBitSet> secondaryEltsWithoutAC, List<ACPlus> secondaryACs) {
        for (int k = secondaries.nextSetBit(0); k >= 0; k = secondaries.nextSetBit(k + 1)) {
            final SmartBitSet secondaryElt = acG.stateIdToSingleEquiv.remove(k);
            final ACPlus secondaryAC = acG.getACPlus(k, secondaryElt);
            final int secondaryEltsSize = secondaryAC == null ? 0 : secondaryAC.acElts.getEltsSize();
            tempIntermediateCount -= (secondaryAC == null ? 1 :secondaryEltsSize);
            if (secondaryAC == null) {
                secondaryEltsWithoutAC.add(secondaryElt);
            } else {
                secondaryACs.add(secondaryAC);
            }
        }
        return tempIntermediateCount;
    }

    private void determineSecondaryACEltsToUnify(
        int primary, List<ACPlus> secondaryACs, List<SmartBitSet> eltsToUnify, ACPlus primaryAC, int start) {
        int secondarySize = secondaryACs.size();
        for (int i = start; i < secondarySize; i++) {
            final ACPlus secondaryAC = secondaryACs.get(i);
            eltsToUnify.addAll(secondaryAC.acElts.getLiveElts());
            // Clear or point to primary AC
            acG.pointToPrimary(primaryAC, secondaryAC);
            secondaryAC.setStateId(primary); // just in case it gets accessed
            secondaryAC.clear();
        }
    }

    public int size() {
        return acG.singleEquivToStateId.size() + acG.foundSets.size();
    }

    @Override
    public String toString() {
      return "AC Forest\r\n" + acG + "\r\n-----------";
    }
}
