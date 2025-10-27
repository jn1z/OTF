package OTF;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SmartBitSetTest {
  private SmartBitSet bitSet1;
  private SmartBitSet bitSet2;

  @BeforeEach
  void setUp() {
    bitSet1 = new SmartBitSet();
    bitSet2 = new SmartBitSet();
  }

  @Test
  void testSetGetClear() {
    for (int i = 0; i < 512; i += 1) {
      assertFalse(bitSet1.get(i));
      bitSet1.set(i, i); // no-op
      assertFalse(bitSet1.get(i));
      bitSet1.set(i);
      assertTrue(bitSet1.get(i));
      bitSet1.clear(i);
      assertFalse(bitSet1.get(i));
    }
    bitSet1.clear(1000);
  }

  @Test
  void testFlip() {
    bitSet1.flip(0, 0); // no-op
    assertFalse(bitSet1.get(0));

    for (int i = 0; i < 512; i += 14) bitSet1.set(i);
    bitSet1.flip(0, 512); // Flip all bits

    for (int i = 0; i < 512; i++) {
      assertEquals(i % 14 != 0, bitSet1.get(i));
    }
    bitSet1.clear();
    for (int i = 0; i < 512; i++) {
      assertFalse(bitSet1.get(i));
    }
  }

  @Test
  void testCardinality() {
    assertEquals(0, bitSet1.cardinality());
    assertEquals(0, bitSet1.cardinality()); // cardinality isn't dirty
    for (int i = 0; i < 512; i += 5) {
      bitSet1.set(i);
    }
    assertEquals(103, bitSet1.cardinality()); // 512/5, rounded up
    for (int i = 0; i < 512; i += 10) {
      bitSet1.clear(i);
    }
    // There are 52 multiples of 10 between 0 (inclusive) and 512 (exclusive):
    // count = ((512 - 1) / 10) + 1 = (511 / 10) + 1 = 51 + 1 = 52.
    // Thus, expected remaining bits = 103 - 52 = 51.
    assertEquals(51, bitSet1.cardinality());
  }

  @Test
  void testIsEmpty() {
    assertTrue(bitSet1.isEmpty());
    bitSet1.set(300);
    assertFalse(bitSet1.isEmpty());
  }

  @Test
  void testLogicalOperations() {
    // bitSet1: bits set for numbers divisible by 3.
    // bitSet2: bits set for numbers divisible by 4.
    for (int i = 0; i < 512; i++) {
      if (i % 3 == 0) {
        bitSet1.set(i);
      }
      if (i % 4 == 0) {
        bitSet2.set(i);
      }
    }

    SmartBitSet orSet = (SmartBitSet) bitSet1.clone();
    orSet.or(bitSet2);
    SmartBitSet andSet = (SmartBitSet) bitSet1.clone();
    andSet.and(bitSet2);
    SmartBitSet andNotSet = (SmartBitSet) bitSet1.clone();
    andNotSet.andNot(bitSet2);
    SmartBitSet xorSet = (SmartBitSet) bitSet1.clone();
    xorSet.xor(bitSet2);

    for (int i = 0; i < 512; i++) {
      boolean inSet1 = (i % 3 == 0);
      boolean inSet2 = (i % 4 == 0);
      boolean expectedOr = inSet1 || inSet2;
      boolean expectedAnd = inSet1 && inSet2;      // Only true for multiples of 12.
      boolean expectedAndNot = inSet1 && !inSet2;    // True for multiples of 3 that are not multiples of 4.
      boolean expectedXor = inSet1 ^ inSet2;    // True for multiples of 3 that are not multiples of 4.
      assertEquals(expectedOr, orSet.get(i));
      assertEquals(expectedAnd, andSet.get(i));
      assertEquals(expectedAndNot, andNotSet.get(i));
      assertEquals(expectedXor, xorSet.get(i));
    }
  }


  @Test
  void testEquals() {
    assertNotEquals(bitSet1, "blah");
    assertEquals(bitSet1, bitSet1);
    bitSet1.set(300);
    bitSet2.set(300);
    assertEquals(bitSet1, bitSet2);
    bitSet2.set(301);
    assertNotEquals(bitSet1, bitSet2);
  }

  @Test
  void testClone() {
    bitSet1.set(300);
    SmartBitSet clone = (SmartBitSet) bitSet1.clone();
    assertEquals(bitSet1, clone);
  }

  @Test
  void testNextBitFunctions() {
    // Set bits at indices 0, 10, 20, ... up to 510.
    for (int i = 0; i < 512; i += 10) {
      bitSet1.set(i);
    }

    // Test nextSetBit: starting from 0, it should return successive set bits.
    int index = bitSet1.nextSetBit(0);
    int expected = 0;
    while (index != -1) {
      assertEquals(expected, index);
      expected += 10;
      index = bitSet1.nextSetBit(index + 1);
    }
    // After iterating, expected should be 520 (i.e., last set bit at 510, then next index 520).
    assertEquals(520, expected);

    // Test nextClearBit:
    // For any index i that is not set, nextClearBit(i) should return i.
    for (int i = 0; i < 512; i++) {
      if (!bitSet1.get(i)) {
        assertEquals(i, bitSet1.nextClearBit(i));
      }
    }
    // For index 512, since no bits are set beyond 511, nextClearBit(512) should return 512.
    assertEquals(512, bitSet1.nextClearBit(512));
  }


  @Test
  void testIsSubset() {
    SmartBitSet subset = new SmartBitSet();
    subset.set(10);
    subset.set(20);
    subset.set(30);
    SmartBitSet superset = (SmartBitSet)subset.clone();
    assertTrue(subset.isSubset(superset));
    assertTrue(superset.isSubset(subset));
    superset.set(5);
    superset.set(40);
    assertTrue(subset.isSubset(superset));
    assertFalse(superset.isSubset(subset));

    SmartBitSet a = new SmartBitSet();
    a.set(15);
    a.set(70);
    a.set(130);
    SmartBitSet b = (SmartBitSet)a.clone();
    b.set(200); // extra bit in b
    assertTrue(a.isSubset(b));
    assertFalse(b.isSubset(a));

    SmartBitSet c = new SmartBitSet();
    c.set(15);
    SmartBitSet d = (SmartBitSet)c.clone();
    c.set(70);
    d.set(80);
    assertFalse(c.isSubset(d));
    assertFalse(d.isSubset(c));
  }

  @Test
  void testIntersects() {
    bitSet1.set(100);
    bitSet1.set(200);
    bitSet2.set(200);
    assertTrue(bitSet1.intersects(bitSet2));

    SmartBitSet bs3 = new SmartBitSet();
    SmartBitSet bs4 = new SmartBitSet();
    bs3.set(150);
    bs4.set(151);
    assertFalse(bs3.intersects(bs4));
  }

  @Test
  void testClearRange() {
    bitSet1.set(0, 100);
    for (int i = 0; i < 100; i++) {
      assertTrue(bitSet1.get(i));
    }
  }

  @Test
  void testSetRange() {
    bitSet1.set(50, 75);
    for (int i = 0; i < 100; i++) {
      if (i < 50 || i >= 75) {
        assertFalse(bitSet1.get(i));
      } else {
        assertTrue(bitSet1.get(i));
      }
    }
  }

  @Test
  void testToString() {
    assertEquals("{}", bitSet1.toString());
    bitSet1.set(1);
    bitSet1.set(3);
    bitSet1.set(5);
    assertEquals("{1, 3, 5}", bitSet1.toString());
  }

  @Test
  void testHashCode() {
    bitSet1.set(10);
    bitSet1.set(20);
    bitSet1.set(30);
    SmartBitSet clone = (SmartBitSet) bitSet1.clone();
    assertEquals(bitSet1.hashCode(), clone.hashCode());
    clone.set(40);
    assertNotEquals(bitSet1.hashCode(), clone.hashCode());
  }

  @Test
  void testSize() {
    assertEquals(64, bitSet1.size()); // empty BitSet still allocates a long
    bitSet1.set(100);
    assertEquals(128, bitSet1.size());
    bitSet1.set(50);
    assertEquals(128, bitSet1.size());
    bitSet1.clear();
    assertEquals(128, bitSet1.size());
    bitSet1.trimToSize();
    assertEquals(0, bitSet1.size()); // hmm, you can trim to size 0
  }

  @Test
  void testValueOf() {
    long[] longs = new long[2];
    longs[0] = (1L << 3) | (1L << 7);
    longs[1] = (1L << 2);
    SmartBitSet bs = SmartBitSet.valueOf(longs);
    for (int i = 0; i < 64; i++) {
      boolean expected = (i == 3) || (i == 7);
      assertEquals(expected, bs.get(i));
    }
    for (int i = 64; i < 128; i++) {
      boolean expected = (i == 66);
      assertEquals(expected, bs.get(i));
    }
  }

  @Test
  void testConstructors() {
    SmartBitSet bsDefault = new SmartBitSet();
    assertEquals(0, bsDefault.cardinality());
    SmartBitSet bsSized = new SmartBitSet(1024);
    bsSized.set(1023);
    assertTrue(bsSized.get(1023));
    bsSized.set(1024);
    assertTrue(bsSized.get(1024));
    assertThrows(NegativeArraySizeException.class, () -> new SmartBitSet(-1));
  }

  @Test
  void testNegativeIndexExceptions() {
    assertThrows(IndexOutOfBoundsException.class, () -> bitSet1.get(-1));
    assertThrows(IndexOutOfBoundsException.class, () -> bitSet1.set(-1));
    assertThrows(IndexOutOfBoundsException.class, () -> bitSet1.clear(-1));
    assertThrows(IndexOutOfBoundsException.class, () -> bitSet1.flip(-1, 10));
    assertThrows(IndexOutOfBoundsException.class, () -> bitSet1.nextSetBit(-1));
    assertThrows(IndexOutOfBoundsException.class, () -> bitSet1.nextClearBit(-1));

    // Also for range methods with negative parameters.
    assertThrows(IndexOutOfBoundsException.class, () -> bitSet1.set(-5, 10));
    assertThrows(IndexOutOfBoundsException.class, () -> bitSet1.flip(-5, 10));
  }

  @Test
  void testInvalidRangeExceptions() {
    // fromIndex > toIndex should throw an exception.
    assertThrows(IndexOutOfBoundsException.class, () -> bitSet1.set(10, 5));
    assertThrows(IndexOutOfBoundsException.class, () -> bitSet1.flip(10, 5));
  }

  @Test
  void testRecalculateWordsInUse() {
    // Set a bit in a high index so that wordsInUse increases.
    bitSet1.set(130);
    int initialWordsInUse = bitSet1.wordsInUse;
    // Clear that bit; clear() calls recalculateWordsInUse internally.
    bitSet1.clear(130);
    // Now wordsInUse should drop.
    assertTrue(bitSet1.wordsInUse < initialWordsInUse);
  }

  @Test
  void testFlipEdgeCases() {
    // Case 1: fromIndex == toIndex (no change)
    bitSet1.set(10);
    long[] before = bitSet1.words.clone();
    bitSet1.flip(20, 20);
    assertArrayEquals(before, bitSet1.words);

    // Case 2: one-word flip: flip a range completely within one word.
    bitSet1.clear();
    bitSet1.set(4);
    bitSet1.set(8);
    // Flip bits 5..7 (none set initially)
    bitSet1.flip(5, 8);
    assertTrue(bitSet1.get(5));
    assertTrue(bitSet1.get(6));
    assertTrue(bitSet1.get(7));
    // Bits outside remain unchanged.
    assertTrue(bitSet1.get(4));
    assertTrue(bitSet1.get(8));

    // Case 3: multi-word flip: flip a range spanning two words.
    bitSet1.clear();
    bitSet1.set(70); // 70 is in the second word (word index 1)
    bitSet1.flip(60, 80); // flip bits 60..79
    // Bit 70 should now be false.
    assertFalse(bitSet1.get(70));
    // Bits 60 and 79, previously false, become true.
    assertTrue(bitSet1.get(60));
    assertTrue(bitSet1.get(79));
    // Bits outside the range remain false.
    assertFalse(bitSet1.get(59));
    assertFalse(bitSet1.get(80));
  }

  @Test
  void testNextSetBitNegative() {
    assertThrows(IndexOutOfBoundsException.class, () -> bitSet1.nextSetBit(-5));
    assertThrows(IndexOutOfBoundsException.class, () -> bitSet1.nextClearBit(-5));
  }
  @Test
  void testToStringLarge() {
    // Set a bit high to force wordsInUse > 128.
    bitSet1.set(9000);
    String s = bitSet1.toString();
    // Check that the output contains the set index and is enclosed in {}.
    assertTrue(s.contains("9000"));
    assertTrue(s.startsWith("{") && s.endsWith("}"));
  }

  @Test
  void testHashCodeCaching() {
    bitSet1.clear();
    bitSet1.set(50);
    bitSet1.set(100);
    int hash1 = bitSet1.hashCode();
    int hash2 = bitSet1.hashCode();
    assertEquals(hash1, hash2);
    // After modifying the bitset, the hash should change.
    bitSet1.set(150);
    int hash3 = bitSet1.hashCode();
    assertNotEquals(hash1, hash3);
  }

  @Test
  void testTrimToSize() {
    bitSet1.clear();
    bitSet1.set(10);
    bitSet1.set(70);
    // Clone should invoke trimToSize if sizeIsSticky is false.
    SmartBitSet clone = (SmartBitSet) bitSet1.clone();
    // After trimming, the internal array length should equal wordsInUse.
    assertEquals(clone.wordsInUse, clone.words.length);
  }

  @Test
  void testLogicalOperationsRemainder() {
    // Create bitsets with an initial size of 100 bits (requires 2 words, and 2 is not a multiple of 4).
    SmartBitSet bsA = new SmartBitSet(100);
    SmartBitSet bsB = new SmartBitSet(100);
    // In bsA, set all even indices.
    for (int i = 0; i < 100; i += 2) {
      bsA.set(i);
    }
    // In bsB, set all indices that are multiples of 3.
    for (int i = 0; i < 100; i += 3) {
      bsB.set(i);
    }
    SmartBitSet orSet = (SmartBitSet) bsA.clone();
    orSet.or(bsB);
    SmartBitSet andSet = (SmartBitSet) bsA.clone();
    andSet.and(bsB);
    SmartBitSet andNotSet = (SmartBitSet) bsA.clone();
    andNotSet.andNot(bsB);

    for (int i = 0; i < 100; i++) {
      boolean inA = (i % 2 == 0);
      boolean inB = (i % 3 == 0);
      boolean expectedOr = inA || inB;
      boolean expectedAnd = inA && inB;
      boolean expectedAndNot = inA && !inB;
      assertEquals(expectedOr, orSet.get(i));
      assertEquals(expectedAnd, andSet.get(i));
      assertEquals(expectedAndNot, andNotSet.get(i));
    }
  }

  @Test
  void testSetBeyondInitialSize() {
    // Create a bitset with an initial size (sticky size) of 32 bits.
    SmartBitSet bs = new SmartBitSet(32);
    // Set a bit well beyond 32.
    bs.set(100);
    assertTrue(bs.get(100));
  }

  // Test multi‑word branch in set(from, to)
  @Test
  void testSetRangeMultiWord() {
    SmartBitSet bs = new SmartBitSet();
    // 60 and 130 are in different words (0–63 and 64–127)
    bs.set(60, 130);
    for (int i = 60; i < 130; i++) {
      assertTrue(bs.get(i));
    }
    // Outside the range, bits remain false
    assertFalse(bs.get(59));
    assertFalse(bs.get(130));
  }

  // Test toString() for a run of consecutive bits
  @Test
  void testToStringConsecutive() {
    SmartBitSet bs = new SmartBitSet();
    bs.set(20);
    bs.set(21);
    bs.set(22);
    // Expect a single run printed as "20, 21, 22"
    assertEquals("{20, 21, 22}", bs.toString());
  }

  @Test
  void testOrCapacityExpansion() {
    // Create a bitset with a small initial capacity (sticky size of 32 bits)
    SmartBitSet bsA = new SmartBitSet(32);
    // Create another bitset that sets a bit in a later word
    SmartBitSet bsB = new SmartBitSet();
    bsB.set(100);
    // Perform or; this should expand bsA's internal array
    bsA.or(bsB);
    assertTrue(bsA.get(100));
  }

  // Test the constructor with initial size zero
  @Test
  void testConstructorZero() {
    SmartBitSet bs = new SmartBitSet(0);
    assertEquals(0, bs.cardinality());
    // Test that we can set a bit and the set expands properly.
    bs.set(10);
    assertTrue(bs.get(10));
  }

  // Test nextClearBit on a full word (all bits set) so it returns wordsInUse * BITS_PER_WORD
  @Test
  void testNextClearBitFullWord() {
    SmartBitSet bs = new SmartBitSet();
    // Set every bit in the first word (0..63)
    for (int i = 0; i < SmartBitSet.BITS_PER_WORD; i++) {
      bs.set(i);
    }
    // Since the first word is completely full, nextClearBit(0) should return 64
    assertEquals(64, bs.nextClearBit(0));
  }

  @Test
  void testCheckRangeNegative() {
    SmartBitSet bs = new SmartBitSet();
    Exception e = assertThrows(IndexOutOfBoundsException.class, () -> bs.set(-1, 10));
    assertNotNull(e);
    e = assertThrows(IndexOutOfBoundsException.class, () -> bs.flip(10, 5));
    assertNotNull(e);
  }

  @Test
  void testIntersectsOverlappingAndNonOverlapping() {
    // Overlapping case:
    SmartBitSet a = new SmartBitSet();
    SmartBitSet b = new SmartBitSet();
    // Set bits in multiple words:
    a.set(10);   // word0
    a.set(70);   // word1 (70 >> 6 == 1)
    a.set(130);  // word2 (130 >> 6 == 2)
    b.set(10);   // word0
    b.set(130);  // word2
    assertTrue(a.intersects(b));

    // Non-overlapping case:
    SmartBitSet c = new SmartBitSet();
    SmartBitSet d = new SmartBitSet();
    c.set(20);   // word0
    c.set(80);   // word1
    d.set(30);   // word0, but 30 != 20
    d.set(90);   // word1, 90 != 80
    assertFalse(c.intersects(d));
  }

  @Test
  void testAndOperation() {
    // Create two bitsets that span multiple words.
    SmartBitSet a = new SmartBitSet();
    SmartBitSet b = new SmartBitSet();
    // a: set bits in word0, word1, word2.
    a.set(5);    // word0
    a.set(70);   // word1 (70 >> 6 == 1)
    a.set(130);  // word2 (130 >> 6 == 2)
    // b: overlap only in word0 and word2.
    b.set(5);    // word0
    b.set(90);   // word1 (90 != 70)
    b.set(130);  // word2
    SmartBitSet c = (SmartBitSet) a.clone();
    c.and(b);
    // Expect only bits that are in both:
    assertTrue(c.get(5));
    assertFalse(c.get(70));
    assertTrue(c.get(130));

    // Also verify that if a has extra words beyond b, they get cleared.
    a.set(200); // 200 >> 6 == 3, new word in a
    c = (SmartBitSet) a.clone();
    c.and(b);
    // b does not have any bits in word3, so bit at 200 must be cleared.
    assertFalse(c.get(200));
  }

  @Test
  void testEqualsLoopUnroll() {
    int[] indices = {0, 70, 140, 210, 280};
    SmartBitSet a = createBitSet(300, indices);
    SmartBitSet b = createBitSet(300, indices);
    // With five words, the unrolled loop in equals will process 4 words in one block and 1 word in the remainder.
    assertTrue(a.equals(b));
    // Modify b in the remainder region (word index 4) and expect equals to fail.
    b.clear(280);
    assertFalse(a.equals(b));
  }

  @Test
  void testIntersectsLoopUnroll() {
    // Create two five-word bit sets.
    // a has bits at {0, 70, 140, 210, 280}
    // b has bits at {10, 70, 150, 210, 290}
    // They overlap in word1 (bit 70) and word3 (bit 210)
    int[] indicesA = {0, 70, 140, 210, 280};
    int[] indicesB = {10, 70, 150, 210, 290};
    SmartBitSet a = createBitSet(300, indicesA);
    SmartBitSet b = createBitSet(300, indicesB);
    // With commonWords = 5, remainder = 5 % 4 = 1, the loop unrolling path is used.
    assertTrue(a.intersects(b));

    // Now create two bit sets with no overlapping bits.
    int[] indicesC = {0, 70, 140, 210, 280};
    int[] indicesD = {5, 75, 145, 215, 285};
    SmartBitSet c = createBitSet(300, indicesC);
    SmartBitSet d = createBitSet(300, indicesD);
    assertFalse(c.intersects(d));
  }

  @Test
  void testAndNotLoopUnroll() {
    // Create two five-word bit sets.
    // a has bits at {0, 70, 140, 210, 280}
    // b has bits at {0, 140, 280} so overlapping in word0, word2, and word4.
    int[] indicesA = {0, 70, 140, 210, 280};
    int[] indicesB = {0, 140, 280};
    SmartBitSet a = createBitSet(300, indicesA);
    SmartBitSet b = createBitSet(300, indicesB);
    // a andNot b should clear bits in a that are also in b.
    SmartBitSet result = (SmartBitSet) a.clone();
    result.andNot(b);
    // Expect:
    // Bit 0 (word0) cleared, bit 70 (word1) remains,
    // Bit 140 (word2) cleared, bit 210 (word3) remains,
    // Bit 280 (word4) cleared.
    assertFalse(result.get(0));
    assertTrue(result.get(70));
    assertFalse(result.get(140));
    assertTrue(result.get(210));
    assertFalse(result.get(280));
  }


  // Helper method to create a bit set with a specified initial size and set bits at the given indices.
  // Using an initial size of 500 bits ensures that words.length is 8.
  private SmartBitSet createBitSet(int initialSize, int... indices) {
    SmartBitSet bs = new SmartBitSet(initialSize);
    for (int index : indices) {
      bs.set(index);
    }
    return bs;
  }

  // Equals – force the unrolled loop.
  // Create a bit set with bits in each of eight words.
  // Then clone it and modify one word in the first 4-word block.
  @Test
  void testEqualsLoopUnrollModified() {
    // Set one bit per word at indices: 0 (word0), 64 (word1), 128 (word2),
    // 192 (word3), 256 (word4), 320 (word5), 384 (word6), 448 (word7).
    SmartBitSet a = createBitSet(500, 0, 64, 128, 192, 256, 320, 384, 448);
    SmartBitSet b = (SmartBitSet) a.clone();
    // In the first unrolled block (words[0]..words[3]), clear the bit in word1.
    b.clear(64);
    // The unrolled loop in equals (which processes words in blocks of 4)
    // should catch the difference.
    assertFalse(a.equals(b));
  }

  // Intersects – force the unrolled block in the for loop.
  // Create two bit sets with eight words and force an intersection in one block.
  @Test
  void testIntersectsLoopUnrollTrue() {
    // a: set bits so that word3 becomes nonzero (e.g. set index 192)
    SmartBitSet a = createBitSet(500, 192, 350);
    // b: set a bit in word3 as well (e.g. 192) plus another bit elsewhere.
    SmartBitSet b = createBitSet(500, 192, 400);
    // With eight words, the unrolled loop processes two blocks.
    // The common block in which word3 resides should produce a nonzero intersection.
    assertTrue(a.intersects(b));
  }

  // Intersects – test the false branch of the unrolled loop.
  @Test
  void testIntersectsLoopUnrollFalse() {
    // Create two bit sets with eight words that have no overlapping bits.
    // a: set bits in word0, word2, word4, word6.
    SmartBitSet a = createBitSet(500, 10, 130, 250, 370);
    // b: set bits in word1, word3, word5, word7.
    SmartBitSet b = createBitSet(500, 70, 190, 310, 430);
    // There should be no overlap in any block.
    assertFalse(a.intersects(b));
  }

  // andNot – force the unrolled loop.
  // Create a bit set with eight words and then use andNot to clear bits in a from b.
  @Test
  void testAndNotLoopUnroll2() {
    // a: bits at indices: 0, 64, 128, 192, 256, 320, 384, 448.
    SmartBitSet a = createBitSet(500, 0, 64, 128, 192, 256, 320, 384, 448);
    // b: clear bits in a by setting bits in the same words in a subset:
    // For example, set bits at 64, 256, and 448.
    SmartBitSet b = createBitSet(500, 64, 256, 448);
    a.andNot(b);
    // After andNot, a should have:
    // word0 (index 0) unchanged, word1 (index 64) cleared,
    // word2 (index 128) unchanged, word3 (index 192) unchanged,
    // word4 (index 256) cleared, word5 (index 320) unchanged,
    // word6 (index 384) unchanged, word7 (index 448) cleared.
    assertTrue(a.get(0));
    assertFalse(a.get(64));
  }

  // Test with wordsInUse a multiple of 4.
  // For example, with an initial size of 300 bits, if we set a bit in word0, word1, word2, word3,
  // then wordsInUse should become 4 (since 300/64 is at least 5, but if we set bits only in words 0-3, then 4 words are used).
  @Test
  void testIsSubsetMultipleOfFourTrue() {
    // Set bits so that used words are in word indices: 0,1,2,3.
    SmartBitSet sub = createBitSet(300, 10, 70, 130, 200);
    SmartBitSet sup = createBitSet(300, 10, 70, 130, 200, 250); // extra bit in word3
    assertTrue(sub.isSubset(sup));
  }

  @Test
  void testIsSubsetMultipleOfFourFalse() {
    // Same as above but sup is missing a bit in one of the words.
    SmartBitSet sub = createBitSet(300, 10, 70, 130, 200);
    SmartBitSet sup = createBitSet(300, 10, 70, 130); // missing bit in word3
    assertFalse(sub.isSubset(sup));
  }

  // Test with a remainder: wordsInUse not a multiple of 4.
  // For instance, if we set bits in five distinct words.
  @Test
  void testIsSubsetWithRemainderTrue() {
    // With an initial size of 400 bits, word indices 0,1,2,3,4.
    SmartBitSet sub = createBitSet(400, 10, 70, 130, 200, 270);
    SmartBitSet sup = createBitSet(400, 10, 70, 130, 200, 270, 350); // sup has extra bit in word4
    assertTrue(sub.isSubset(sup));
  }

  @Test
  void testIsSubsetWithRemainderFalse() {
    // sub uses 5 words but sup misses the bit in the last word.
    SmartBitSet sub = createBitSet(400, 10, 70, 130, 200, 270);
    SmartBitSet sup = createBitSet(400, 10, 70, 130, 200); // missing bit in word4
    assertFalse(sub.isSubset(sup));
  }

  // Test when sub has extra words that sup does not cover.
  @Test
  void testIsSubsetWhenSubHasExtraWords() {
    // sub has 5 words; sup only 4 words.
    SmartBitSet sub = createBitSet(500, 5, 70, 130, 200, 270);
    SmartBitSet sup = createBitSet(300, 5, 70, 130, 200);
    // In the remainder loop for word index 4, sup does not have any bits (treated as 0),
    // so sub is not a subset of sup.
    assertFalse(sub.isSubset(sup));
  }
}