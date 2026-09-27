package OTF.Compress;

import OTF.SmartBitSet;
import net.automatalib.alphabet.impl.Alphabets;
import net.automatalib.automaton.fsa.impl.CompactNFA;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.List;

import static OTF.BitSetUtils.convertListToBitSet;
import static org.junit.jupiter.api.Assertions.*;

class AntichainForestRegressionTest {
    @Test
    void newAntichainBecomesSearchableAfterItsUnionGrows() {
        BitSet upper = new BitSet();
        upper.set(0, 10);
        AntichainForest forest = forest(new BitSet[0]);
        forest.put(convertListToBitSet(List.of(0)), 0);
        forest.put(upper, 1);

        forest.unify(0, convertListToBitSet(List.of(1)));
        ACPlus merged = forest.acG.stateIdToAC.get(0);
        assertEquals(1, merged.acElts.getEltsSize());
        assertEquals(SmartBitSet.valueOf(upper.toLongArray()), merged.acUnion);
        assertTrue(merged.searchable, "Searchability must use the updated upper boundary");

        forest.compress();
        // Never inserted: an exact cache hit cannot satisfy this lookup.
        assertEquals(0, forest.get(convertListToBitSet(List.of(0, 1))));
        assertEquals(0, forest.get(convertListToBitSet(List.of(0, 1)))); // Cached lookup must agree.
        assertEquals(AntichainForest.MISSING_ELEMENT, forest.get(convertListToBitSet(List.of(0, 10))));
        assertEquals(AntichainForest.MISSING_ELEMENT, forest.get(convertListToBitSet(List.of(1, 2))));
    }

    @Test
    void existingAntichainBecomesSearchableAfterItsUnionGrows() {
        AntichainForest forest = forest(new BitSet[0]);
        forest.put(convertListToBitSet(List.of(0)), 0);
        forest.put(convertListToBitSet(List.of(0, 1)), 1);
        forest.unify(0, convertListToBitSet(List.of(1)));
        assertFalse(forest.acG.stateIdToAC.get(0).searchable);
        forest.compress();
        assertEquals(AntichainForest.MISSING_ELEMENT, forest.get(convertListToBitSet(List.of(0, 2))));

        BitSet upper = new BitSet();
        upper.set(0, 10);
        forest.put(upper, 2);
        forest.unify(0, convertListToBitSet(List.of(2)));
        assertTrue(forest.acG.stateIdToAC.get(0).searchable);

        forest.compress();
        assertEquals(0, forest.get(convertListToBitSet(List.of(0, 2))));
        assertEquals(0, forest.get(upper)); // Original singleton now has representative 0.
    }

    @Test
    void searchabilityIncludesSimulationSaturation() {
        BitSet[] simulations = new BitSet[16];
        simulations[1] = new BitSet();
        simulations[1].set(2, 10);
        AntichainForest forest = forest(simulations);
        forest.put(convertListToBitSet(List.of(0)), 0);
        forest.put(convertListToBitSet(List.of(0, 1)), 1);

        forest.unify(0, convertListToBitSet(List.of(1)));
        ACPlus merged = forest.acG.stateIdToAC.get(0);
        // The unsaturated union {0,1} is too small; saturation adds bits 2..9.
        SmartBitSet expectedUnion = new SmartBitSet();
        expectedUnion.set(0, 10);
        assertEquals(expectedUnion, merged.acUnion);
        assertTrue(merged.searchable, "Searchability must be checked after saturation");

        forest.compress();
        // There is no bit 1 here, so pruning cannot turn this into cached {0}.
        assertEquals(0, forest.get(convertListToBitSet(List.of(0, 2))));
        assertEquals(AntichainForest.MISSING_ELEMENT, forest.get(convertListToBitSet(List.of(0, 10))));
    }

    @Test
    void singletonTakeoverPreservesPrimaryBitsEvenWhenItsLowerBoundaryIsRedundant() {
        BitSet upper = new BitSet();
        upper.set(0, 10);
        AntichainForest forest = forest(new BitSet[0]);
        // Start with the larger primary so this setup is searchable even without
        // the separate searchability-order fix.
        forest.put(upper, 0);
        forest.put(convertListToBitSet(List.of(0)), 1);
        forest.unify(0, convertListToBitSet(List.of(1)));
        assertTrue(forest.acG.stateIdToAC.get(0).searchable);
        forest.compress();
        assertEquals(AntichainForest.MISSING_ELEMENT, forest.get(convertListToBitSet(List.of(0, 10))));

        forest.put(convertListToBitSet(List.of(0, 10)), 2);
        forest.unify(2, convertListToBitSet(List.of(0)));
        ACPlus merged = forest.acG.stateIdToAC.get(2);
        assertEquals(1, merged.acElts.getEltsSize()); // {0} subsumes {0,10} below.
        BitSet expandedUpper = new BitSet();
        expandedUpper.set(0, 11);
        assertEquals(SmartBitSet.valueOf(expandedUpper.toLongArray()), merged.acUnion);

        forest.compress();
        assertEquals(2, forest.get(expandedUpper)); // Fresh query requires bit 10 above.
        assertEquals(2, forest.get(convertListToBitSet(List.of(0, 10))));
        assertEquals(2, forest.get(upper));
        assertEquals(2, forest.get(convertListToBitSet(List.of(0))));
        assertEquals(AntichainForest.MISSING_ELEMENT, forest.get(convertListToBitSet(List.of(0, 11))));
        assertEquals(AntichainForest.MISSING_ELEMENT, forest.get(convertListToBitSet(List.of(1, 10))));
    }

    @Test
    void singletonTakeoverSaturatesNewPrimaryBits() {
        BitSet upper = new BitSet();
        upper.set(0, 10);
        BitSet[] simulations = new BitSet[16];
        simulations[10] = convertListToBitSet(List.of(11, 12));
        AntichainForest forest = forest(simulations);
        forest.put(upper, 0);
        forest.put(convertListToBitSet(List.of(0)), 1);
        forest.unify(0, convertListToBitSet(List.of(1)));
        assertTrue(forest.acG.stateIdToAC.get(0).searchable);

        forest.put(convertListToBitSet(List.of(0, 10)), 2);
        forest.unify(2, convertListToBitSet(List.of(0)));
        ACPlus merged = forest.acG.stateIdToAC.get(2);
        SmartBitSet expectedUnion = new SmartBitSet();
        expectedUnion.set(0, 13);
        assertEquals(expectedUnion, merged.acUnion);

        forest.compress();
        // Omitting bit 10 prevents pruning back to an already-cached subset.
        assertEquals(2, forest.get(convertListToBitSet(List.of(0, 11))));
        assertEquals(2, forest.get(convertListToBitSet(List.of(0, 12))));
        assertEquals(AntichainForest.MISSING_ELEMENT, forest.get(convertListToBitSet(List.of(0, 13))));
    }

    private static AntichainForest forest(BitSet[] simulations) {
        CompactNFA<Integer> nfa = new CompactNFA<>(Alphabets.integers(0, 1));
        // The registry constructor needs only the number of NFA states.
        for (int i = 0; i < 16; i++) {
            nfa.addState(true);
        }
        return new AntichainForest(nfa, simulations);
    }
}
