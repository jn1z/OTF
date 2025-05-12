package OTF.Compress;

import OTF.SmartBitSet;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.*;

/**
Global state fields and methods, e.g., maps and inverse maps.
 */
public class ACGlobals {
    final Map<SmartBitSet,SmartBitSet> prunedMap;
    private static final int INITIAL_PRUNE_CAPACITY = 10_000;
    final Int2ObjectOpenHashMap<SmartBitSet> stateIdToSingleEquiv;
    final Object2IntMap<SmartBitSet> singleEquivToStateId;
    final Int2ObjectOpenHashMap<ACPlus> stateIdToAC;
    final Set<ACPlus> allACs; // So we don't have to rebuild this every time we do a find
    final List<SmartBitSet> searchableACsUnions;
    ACPlus[] searchableACsList;
    InvertedIndex ACsInvertedIndex;

    // BitSets that have been discovered as part of ACs.
    // NOTE: these have to match what's sent to OTFDeterminization. We can't "find" more sets.
    final Map<SmartBitSet, ACPlus> foundSets;
    final Map<ACPlus, Set<SmartBitSet>> foundSetsInv;
    int nNFA; // number of states in the original NFA

    SmartBitSet[] redundantStates; // redundant states associated with state i, null iff supersetState bit not set

    SmartBitSet supersetStates; // states that are supersets of other elements

    SmartBitSet subsetStates; // states that are subsets of other states
    // thus, they might be added during saturation, or removed during pruning

    public ACGlobals(int nNFA, BitSet[] simSupersetRels) {
        this.nNFA = nNFA;

        if (simSupersetRels.length == 0) {
            prunedMap = null;
        } else {
            if (nNFA < 1000) {
                prunedMap = new HashMap<>(INITIAL_PRUNE_CAPACITY);
            } else {
                // Upper bound on cache size, should keep us from overflowing memory for large nNFA.
                Cache<SmartBitSet, SmartBitSet> prunedCache = Caffeine.newBuilder()
                    .initialCapacity(INITIAL_PRUNE_CAPACITY)
                    .maximumSize(1_000_000)
                    .build();
                prunedMap = prunedCache.asMap();
            }
        }
        stateIdToAC = new Int2ObjectOpenHashMap<>();
        foundSets = new HashMap<>();
        foundSetsInv = new HashMap<>();
        allACs = new HashSet<>();
        searchableACsUnions = new ArrayList<>();
        ACsInvertedIndex = new InvertedIndex(nNFA, List.of());
        stateIdToSingleEquiv = new Int2ObjectOpenHashMap<>();
        singleEquivToStateId = new Object2IntOpenHashMap<>();
        singleEquivToStateId.defaultReturnValue(AntichainForest.MISSING_ELEMENT);

        initSimRels(simSupersetRels);
    }

    public void initSimRels(BitSet[] simSupersetRels) {
        redundantStates = new SmartBitSet[simSupersetRels.length];
        supersetStates = new SmartBitSet(nNFA);
        subsetStates = new SmartBitSet(nNFA);
        for(int i=0;i<simSupersetRels.length;i++) {
            BitSet b = simSupersetRels[i];
            if (b != null) {
                redundantStates[i] = SmartBitSet.valueOf(b.toLongArray());
                supersetStates.set(i);
                subsetStates.or(redundantStates[i]);
            }
        }
    }

    Set<ACPlus> getAllACs() {
        return allACs;
    }

    /**
     * Add a 1-element equivalence class.
     * Larger equivalence classes are added in unify.
     */
    public void put(SmartBitSet newElt, int newState) {
        stateIdToSingleEquiv.put(newState, newElt);
        singleEquivToStateId.put(newElt, newState);
    }

    /**
     * Cache found bitsets for quicker searches.
     */
    void addToFoundSets(ACPlus ACPlus, List<SmartBitSet> newElts) {
        for(SmartBitSet newElt: newElts) {
            foundSets.put(newElt, ACPlus);
        }
        Set<SmartBitSet> bitSets = foundSetsInv.computeIfAbsent(ACPlus, k -> new HashSet<>());
        bitSets.addAll(newElts);
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
        Set<SmartBitSet> secondaryFoundSets = foundSetsInv.remove(secondaryAC);
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
        StringBuilder sb = new StringBuilder();
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
