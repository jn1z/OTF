package OTF.Compress;

import java.util.*;

import OTF.*;
import OTF.Registry.Registry;
import net.automatalib.automaton.fsa.impl.CompactNFA;

/**
 * Antichain Disjoint-Set Forest, representing equivalence classes.
 * 1-element equivalence classes are simply represented as BitSets <-> ints.
 * Larger equivalence classes are represented by ACPlus elements.
 */
public class AntichainForest {
    public ACGlobals acG;
    public static final int MISSING_ELEMENT = Registry.MISSING_ELEMENT;
    public int curIntermediateCount = 0; // used for metrics
    public int maxIntermediateCount = 0; // used for metrics

    public AntichainForest(CompactNFA<Integer> nfa, BitSet[] simSupers) {
        this.acG = new ACGlobals(nfa.size(), simSupers);
    }

    public void compress() {
        // Rebuild an inverted index for the AC Unions.
        // This could be more efficient, but compress() is called relatively rarely
        acG.searchableACsList = new ACPlus[acG.getAllACs().size()];
        acG.searchableACsUnions.clear();
        int index = 0;
        for(ACPlus acPlus: acG.getAllACs()) {
            if (acPlus.searchable) {
                acG.searchableACsUnions.add(acPlus.acUnion);
                acG.searchableACsList[index++] = acPlus;
            }
        }
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
        SmartBitSet newElt = pruneEltWithSims(SmartBitSet.valueOf(newEltFull.toLongArray()));
        acG.put(newElt, newState);
    }

    /**
     * Get BitSet equivalence class (state), otherwise return MISSING_ELEMENT.
     */
    public int get(BitSet eltFull) {
        SmartBitSet prunedElt = pruneEltWithSims(SmartBitSet.valueOf(eltFull.toLongArray()));

        // Check 1-element equivalence classes first
        int singleEquivState = acG.singleEquivToStateId.getInt(prunedElt);
        if (singleEquivState != MISSING_ELEMENT) {
            return singleEquivState;
        }

        // Check AC equivalence classes
        // First, we search the foundSets
        ACPlus foundAC = acG.foundSets.get(prunedElt);
        if (foundAC != null) {
            return foundAC.getStateId();
        }

        return find(SmartBitSet.valueOf(prunedElt.words));
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
        SmartBitSet potentialSupersets = acG.ACsInvertedIndex.findSupersetIndices(
            acG.searchableACsUnions, SmartBitSet.EMPTY_SMART_BITSET, prunedElt, true);

        if (!potentialSupersets.isEmpty()) {
            // Saturate sElt to use in step 2
            SmartBitSet saturatedElt = saturateEltWithSims(prunedElt);

            // For each AC found above:
            // 1. validate its union is an actual superset
            // 2. validate that saturatedElt is a (proper) superset of one of the elts of the AC
            //    (if it was equal, it would have been found in caches earlier)
            for (int i = potentialSupersets.nextSetBit(0); i >= 0; i = potentialSupersets.nextSetBit(i + 1)) {
                // Step 1
                SmartBitSet acUnion = acG.searchableACsUnions.get(i);
                if (!prunedElt.isSubset(acUnion)) {
                    continue;
                }

                // Step 2
                ACPlus acPlus = acG.searchableACsList[i];
                if (acPlus.acElts.properSubsetExists(saturatedElt)) {
                    acG.addToFoundSets(acPlus, List.of(prunedElt)); // cache for the next search
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
        // Find out if these represent 1-element classes or ACs.
        SmartBitSet primaryElt = acG.stateIdToSingleEquiv.remove(primary);
        ACPlus primaryAC = acG.getACPlus(primary, primaryElt);

        int tempIntermediateCount = -(primaryAC == null ? 1 : primaryAC.acElts.getEltsSize());

        int maxSize = secondaries.cardinality();
        List<ACPlus> secondaryACs = new ArrayList<>(maxSize); // ACs to be unified
        List<SmartBitSet> secondaryEltsWithoutAC = new ArrayList<>(maxSize); // 1-elts to be unified
        tempIntermediateCount = determineSecondaryACsandElts(
            secondaries, tempIntermediateCount, secondaryEltsWithoutAC, secondaryACs);

        int eltsToUnifySize = secondaryEltsWithoutAC.size();
        for (ACPlus secondaryAC: secondaryACs) {
            eltsToUnifySize += secondaryAC.acElts.getEltsSize();
        }
        List<SmartBitSet> eltsToUnify = new ArrayList<>(eltsToUnifySize + 1);
        eltsToUnify.addAll(secondaryEltsWithoutAC);
        primaryAC = determineOrCreatePrimaryAC(primary, primaryAC, secondaryACs, primaryElt, eltsToUnify);

        acG.addToFoundSets(primaryAC, secondaryEltsWithoutAC);

        unifyEltsIntoPrimaryAC(eltsToUnify, primaryAC);

        tempIntermediateCount += primaryAC.acElts.getEltsSize();

        this.curIntermediateCount += tempIntermediateCount; // no need to check max value
    }

    /**
     * Unify elements into primary AC
     */
    private void unifyEltsIntoPrimaryAC(List<SmartBitSet> eltsToUnify, ACPlus primaryAC) {
        eltsToUnify.sort(SmartBitSet.SMART_CARDINALITY_COMPARATOR);
        // sorted, so smallest (most impactful) elements are unified first

        int previousUnionCardinality = primaryAC.acUnion.cardinality();
        for (SmartBitSet eltToUnify: eltsToUnify) {
            if (primaryAC.acElts.unifyEltIntoAC(eltToUnify, acG.nNFA)) {
                primaryAC.unionOr(eltToUnify); // update the union
            }
        }
        // saturate is relatively slow; only do once per unify and only on change
        if (primaryAC.acUnion.cardinality() > previousUnionCardinality) {
            primaryAC.unionOr(this.saturateEltWithSims(primaryAC.acUnion));
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
            acG.addToFoundSets(primaryAC, List.of(primaryElt));
        } else {
            // determine secondary AC elts to unify
            determineSecondaryACEltsToUnify(primary, secondaryACs, eltsToUnify, primaryAC, 0);
        }
        return primaryAC;
    }

    private int determineSecondaryACsandElts(
        BitSet secondaries, int tempIntermediateCount, List<SmartBitSet> secondaryEltsWithoutAC, List<ACPlus> secondaryACs) {
        for (int k = secondaries.nextSetBit(0); k >= 0; k = secondaries.nextSetBit(k + 1)) {
            SmartBitSet secondaryElt = acG.stateIdToSingleEquiv.remove(k);
            ACPlus secondaryAC = acG.getACPlus(k, secondaryElt);
            int secondaryEltsSize = secondaryAC == null ? 0 : secondaryAC.acElts.getEltsSize();
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
            ACPlus secondaryAC = secondaryACs.get(i);
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

    /**
     * Remove appropriate subset elements from bitset.
     * Similar to "transition pruning".
     * E.g., if {1} == {1,2}, then if the bitset contains {1}, we can remove {2}.
     * Cached in prunedMap for performance.
     * Used in get and put.
     */
    public SmartBitSet pruneEltWithSims(SmartBitSet b) {
        if (acG.prunedMap == null) {
            return b; // simulation is turned off
        }
        // Calculate all potentially prunable elements of b
        // Check if b contains any elements that are marked as potential redundant subsets (subsetStates)
        // and also if it contains any supersets (supersetStates) that imply redundancy.
        // If b doesn't intersect both of these, then there is nothing to prune.
        if (!b.intersects(acG.subsetStates) || !b.intersects(acG.supersetStates)) {
            return b;
        }

        SmartBitSet pruned = acG.prunedMap.get(b);
        if (pruned == null) {
            pruned = determinePrunedValue(b);
            acG.prunedMap.put(b, pruned);
        }
        return pruned;
    }

    /**
     * Determine pruned value of b.
     */
    private SmartBitSet determinePrunedValue(SmartBitSet b) {
        // Calculate all supersets in b
        SmartBitSet supersetStatesInB = (SmartBitSet) b.clone();
        supersetStatesInB.and(acG.supersetStates);
        if (supersetStatesInB.equals(b)) {
            return b; // b is composed entirely of superset states: no pruning is needed.
        }
        // prune redundant subsets: for each superset, prune all subsets associated with it
        SmartBitSet prunedValue = (SmartBitSet) b.clone();
        SmartBitSet[] redundantStatesArr = acG.redundantStates;
        for (int i = supersetStatesInB.nextSetBit(0); i >= 0; i = supersetStatesInB.nextSetBit(i + 1)) {
            prunedValue.andNot(redundantStatesArr[i]);
        }
        return prunedValue;
    }

    /**
     * Add appropriate subset elements to bitset.
     * Similar to "transition saturation".
     * E.g., if {1} == {1,2}, then if the bitset contains {1}, we can add {2}.
     * Used in AC Union and find.
     * TODO: potentially could be used in unify, although the advantage there is less clear.
     */
    public SmartBitSet saturateEltWithSims(SmartBitSet b) {
        if (acG.prunedMap == null) {
            return b; // simulation is turned off
        }

        SmartBitSet supersetStatesInB = SmartBitSet.valueOf(b.words);
        supersetStatesInB.and(acG.supersetStates);
        // supersetStatesInB contains all superset states that occur in b

        if (supersetStatesInB.isEmpty()) {
            // No superset states in B, so there's nothing to saturate.
            return (SmartBitSet)b.clone();
        }

        SmartBitSet potentialSubsets = SmartBitSet.valueOf(b.words);
        potentialSubsets.and(acG.subsetStates);
        // Keep only the bits that are potential redundant subsets.

        // add redundant states, starting with a copy
        SmartBitSet saturated = SmartBitSet.valueOf(b.words);

        // Iterate over each state in b that is marked as a superset
        SmartBitSet[] redundantStatesArr = acG.redundantStates;

        for (int i = supersetStatesInB.nextSetBit(0); i >= 0; i = supersetStatesInB.nextSetBit(i + 1)) {
            SmartBitSet redundantStates = redundantStatesArr[i];
            // Note: redundantStates is non-null since i is a known supersetState
            saturated.or(redundantStates); // Add redundant states
            potentialSubsets.andNot(redundantStates); // Don't need to test them again
            if (potentialSubsets.isEmpty()) {
                // All potential subset states have been added.
                return saturated;
            }
        }
        return saturated;
    }

    @Override
    public String toString() {
      return "AC Forest\r\n" + acG + "\r\n-----------";
    }
}
