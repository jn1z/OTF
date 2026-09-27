package OTF;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import static OTF.BitSetUtils.convertListToBitSet;
import static OTF.BitSetUtils.convertListToSmartBitSet;
import static org.junit.jupiter.api.Assertions.*;

class SmartBitSetXorRegressionTest {
    @Test
    void equalOperandsBecomeCanonicalEmptyAtWordBoundaries() {
        for (int bit : new int[]{0, 63, 64, 127, 128, 130, 255, 256, 511, 512, 4095}) {
            BitSet input = convertListToBitSet(List.of(bit));
            SmartBitSet actual = SmartBitSet.valueOf(input.toLongArray());
            SmartBitSet other = SmartBitSet.valueOf(input.toLongArray());
            actual.cardinality(); // Populate both caches before the mutation.
            actual.hashCode();

            actual.xor(other);

            assertMatches(new BitSet(), actual);
            assertMatches(input, other); // A distinct right operand must not change.
        }
    }

    @Test
    void selfXorBecomesCanonicalEmpty() {
        // Eight words exercise the unrolled loop; word 8 exercises its remainder.
        SmartBitSet actual = convertListToSmartBitSet(List.of(0, 63, 64, 130, 255, 256, 511, 512));
        actual.cardinality();
        actual.hashCode();

        actual.xor(actual);

        assertMatches(new BitSet(), actual);
    }

    @Test
    void cancellingSeveralHighWordsPreservesLowBitsAndRefreshesCaches() {
        SmartBitSet actual = convertListToSmartBitSet(List.of(0, 5, 64, 130, 256, 511));
        BitSet mask = convertListToBitSet(List.of(64, 130, 256, 511));
        SmartBitSet other = SmartBitSet.valueOf(mask.toLongArray());
        assertEquals(6, actual.cardinality());
        actual.hashCode();

        actual.xor(other);

        assertMatches(convertListToBitSet(List.of(0, 5)), actual);
        assertMatches(mask, other);
    }

    @Test
    void cancellingOnlyTheHighestWordPreservesIntermediateWords() {
        SmartBitSet actual = convertListToSmartBitSet(List.of(0, 64, 130, 256, 511));

        actual.xor(convertListToSmartBitSet(List.of(511)));

        assertMatches(convertListToBitSet(List.of(0, 64, 130, 256)), actual);
    }

    @Test
    void normalizedResultCanGrowAgainAndBeReusedAfterBecomingEmpty() {
        BitSet input = convertListToBitSet(List.of(0, 130, 511));
        SmartBitSet actual = SmartBitSet.valueOf(input.toLongArray());
        SmartBitSet mask = convertListToSmartBitSet(List.of(130, 511));

        actual.xor(mask);
        assertMatches(convertListToBitSet(List.of(0)), actual);
        actual.xor(mask);
        assertMatches(input, actual);
        actual.xor(actual);
        assertMatches(new BitSet(), actual);
        actual.set(130);
        assertMatches(convertListToBitSet(List.of(130)), actual);
        // Extend beyond both the normalized length and the original capacity.
        actual.xor(convertListToSmartBitSet(List.of(1024)));
        assertMatches(convertListToBitSet(List.of(130, 1024)), actual);
    }

    @Test
    void unequalWidthsAndEmptyOperandsAgreeWithJavaBitSet() {
        List<List<Integer>> inputs = List.of(
            List.of(), List.of(0), List.of(63), List.of(64), List.of(130), List.of(0, 130),
            List.of(0, 63, 64, 255, 256), List.of(0, 64, 130, 511), List.of(512)
        );
        for (List<Integer> left : inputs) {
            for (List<Integer> right : inputs) {
                BitSet expected = convertListToBitSet(left);
                BitSet rightBits = convertListToBitSet(right);
                SmartBitSet actual = SmartBitSet.valueOf(expected.toLongArray());
                SmartBitSet other = SmartBitSet.valueOf(rightBits.toLongArray());
                actual.cardinality();
                actual.hashCode();

                expected.xor(rightBits);
                actual.xor(other);

                assertMatches(expected, actual);
                assertMatches(rightBits, other);
            }
        }
    }

    private static void assertMatches(BitSet expected, SmartBitSet actual) {
        long[] expectedWords = expected.toLongArray();
        assertEquals(expectedWords.length, actual.wordsInUse, "Normalized logical word count");
        assertArrayEquals(expectedWords, Arrays.copyOf(actual.words, actual.wordsInUse));
        assertEquals(expected.cardinality(), actual.cardinality(), "Cardinality cache");
        assertEquals(expected.isEmpty(), actual.isEmpty());
        assertEquals(expected.toString(), actual.toString());

        SmartBitSet canonical = SmartBitSet.valueOf(expectedWords);
        assertEquals(canonical, actual);
        assertEquals(actual, canonical);
        assertEquals(canonical.hashCode(), actual.hashCode(), "Hash cache");
        assertTrue(actual.isSubset(canonical), "Subset checks must ignore cancelled high words");
        assertTrue(canonical.isSubset(actual));
    }

}
