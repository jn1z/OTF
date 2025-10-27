package OTF.Compress;

import OTF.SmartBitSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.*;

/**
Global state fields and methods, e.g., maps and inverse maps.
 */
public final class ACGlobals {

    final Int2ObjectOpenHashMap<SmartBitSet> stateIdToSingleEquiv;
    final Object2IntMap<SmartBitSet> singleEquivToStateId;
    final Int2ObjectOpenHashMap<ACPlus> stateIdToAC;
    final Set<ACPlus> allACs; // So we don't have to rebuild this every time we do a find
    final List<SmartBitSet> searchableACsUnions;
    ACPlus[] searchableACsList;
    InvertedIndex ACsInvertedIndex;
    final SimAccelerate simAccelerate;

    // BitSets that have been discovered as part of ACs.
    // NOTE: these have to match what's sent to OTFDeterminization. We can't "find" more sets.
    final Map<SmartBitSet, ACPlus> foundSets;
    final Map<ACPlus, List<SmartBitSet>> foundSetsInv;
    int nNFA; // number of states in the original NFA

    public ACGlobals(int nNFA, BitSet[] simSupersetRels) {
        this.nNFA = nNFA;

        stateIdToAC = new Int2ObjectOpenHashMap<>();
        foundSets = new HashMap<>();
        foundSetsInv = new HashMap<>();
        allACs = new HashSet<>();
        searchableACsUnions = new ArrayList<>();
        ACsInvertedIndex = new InvertedIndex(nNFA, List.of());
        stateIdToSingleEquiv = new Int2ObjectOpenHashMap<>();
        singleEquivToStateId = new Object2IntOpenHashMap<>();
        singleEquivToStateId.defaultReturnValue(AntichainForest.MISSING_ELEMENT);
        simAccelerate = new SimAccelerate(simSupersetRels, nNFA);
    }


    Set<ACPlus> getAllACs() {
        return allACs;
    }

    /**
     * Cache found bitsets for quicker searches.
     */
    void addToFoundSets(ACPlus ACPlus, List<SmartBitSet> newElts) {
        for(SmartBitSet newElt: newElts) {
            foundSets.put(newElt, ACPlus);
        }
        final List<SmartBitSet> bitSets = foundSetsInv.computeIfAbsent(ACPlus, k -> new ArrayList<>(newElts.size()));
        bitSets.addAll(newElts);
    }
    void addToFoundSets(ACPlus ACPlus, SmartBitSet newElt) {
        foundSets.put(newElt, ACPlus);
        final List<SmartBitSet> bitSets = foundSetsInv.computeIfAbsent(ACPlus, k -> new ArrayList<>());
        bitSets.add(newElt);
    }

    /**
     * Get AntichainPlus from primaryStateId, or null if it doesn't exist.
     * Side effect: clear out the primaryElt from the single equivalences, since we're about to unify it.
     */
    ACPlus getACPlus(int primaryStateId, SmartBitSet primaryElt) {
        ACPlus primaryAC = null;
        if (primaryElt != null) {
            singleEquivToStateId.remove(primaryElt, primaryStateId);
        } else {
            primaryAC = stateIdToAC.get(primaryStateId);
            if (primaryAC == null) {
                throw new RuntimeException("ERROR: unexpected null antichain!");
            }
        }
        return primaryAC;
    }

    /**
     * Point secondary AntichainPlus to primary AntichainPlus.
     */
    void pointToPrimary(ACPlus primaryAC, ACPlus secondaryAC) {
        stateIdToAC.put(secondaryAC.getStateId(), primaryAC);
        allACs.remove(secondaryAC);
        secondaryAC.searchable = false;
        final List<SmartBitSet> secondaryFoundSets = foundSetsInv.remove(secondaryAC);
        if (secondaryFoundSets != null) {
            // "compress the paths", i.e., point appropriate bitsets to the primary antichain
            // rather than indirect.
            for (SmartBitSet b : secondaryFoundSets) {
                foundSets.put(b, primaryAC);
            }
            foundSetsInv.get(primaryAC).addAll(secondaryFoundSets);
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("AC sizes:\r\n");
        sb.append("searchable:");
        for(ACPlus ACPlus : allACs) {
            if (ACPlus.searchable) {
                sb.append(ACPlus.acElts.getEltsSize())
                        //.append(":")
                        //.append(ACPlus.searchable)
                        .append(",");
            }
        }
        sb.append("NOT searchable count:");
        int notSearchCount = 0;
        for(ACPlus ACPlus : allACs) {
            if (!ACPlus.searchable) {
                notSearchCount++;
            }
        }
        sb.append(notSearchCount);
        sb.append("\r\n-----------");
        for(ACPlus ACPlus : allACs) {
            if (ACPlus.searchable && ACPlus.acElts.getEltsSize() < 4) {
                sb.append(ACPlus);
                sb.append("\r\n");
            }
        }
        sb.append("\r\n-----------");
        sb.append("\r\n");
        sb.append("Foundsets: ").append(foundSets.size());
        return sb.toString();
    }
}
