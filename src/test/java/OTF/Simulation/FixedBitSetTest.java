package OTF.Simulation;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

class FixedBitSetTest {
  private static int wordsForBits(int numBits) {
    return ((numBits - 1) >> 6) + 1;
  }

  @Test
  @DisplayName("nextSetBit ignores tail bits beyond numBits (fails on old code, passes after mask fix)")
  public void testNextSetBitIgnoresTailBits() {
    // Choose a size that's not a multiple of 64 so we have a "tail" in the last word.
    final int numBits = 130;      // 2 full bits in the last word (indices 128, 129)
    final FixedBitSet fbs = new FixedBitSet(numBits);

    // We do NOT set any valid bit. Logically, the set is empty.
    // Now contaminate ONLY the tail region (bits >= 130) to simulate a bug source.
    // last word index:
    final int lastWord = wordsForBits(numBits) - 1;
    final int r = numBits & 63; // r = 2
    Assertions.assertEquals(2, r);

    // Build a value with the low r bits = 0 (valid region), high (64 - r) bits = 1 (tail region).
    // That is: 11..1100 (binary), so ONLY out-of-range bits are 1.
    long tailOnesOnly = -1L << r;   // e.g., 0xFFFF...FFFC

    fbs.bits[lastWord] = tailOnesOnly;

    // EXPECTED (correct implementation with lastWordMask inside nextSetBit): no set bit found.
    int next = fbs.nextSetBit(0);
    Assertions.assertEquals(-1, next,
        "nextSetBit() must ignore tail bits beyond numBits; got " + next + " instead");
  }

  @Test
  @DisplayName("nextSetBit from non-word-aligned indices returns the first set bit at/after index")
  public void testNextSetBitFromMiddle() {
    final int numBits = 192; // 3 words
    final FixedBitSet fbs = new FixedBitSet(numBits);

    // Set a bit in word #1 (indices 64..127), say at 95
    fbs.set(95);
    // Also set one earlier to ensure scanning correctly skips it when index > earlier
    fbs.set(10);

    // Scan starting mid word (e.g., 64+31=95). First should be 95.
    Assertions.assertEquals(95, fbs.nextSetBit(95));
    // Scan starting at 64: should still get 95, not 10.
    Assertions.assertEquals(95, fbs.nextSetBit(64));
    // Scan past 95: should be -1.
    Assertions.assertEquals(-1, fbs.nextSetBit(96));
  }

  @Test
  @DisplayName("get/set/clear roundtrip across many offsets")
  public void testGetSetClearRoundtrip() {
    final int numBits = 257; // span > 4 words, include multiple boundaries
    final FixedBitSet fbs = new FixedBitSet(numBits);
    final Random rnd = new Random(12345);

    // Randomly set some bits
    boolean[] truth = new boolean[numBits];
    for (int i = 0; i < 2000; i++) {
      int idx = rnd.nextInt(numBits);
      if (rnd.nextBoolean()) {
        fbs.set(idx);
        truth[idx] = true;
      } else {
        fbs.clear(idx);
        truth[idx] = false;
      }
    }

    // Verify
    for (int i = 0; i < numBits; i++) {
      Assertions.assertEquals(truth[i], fbs.get(i), "Mismatch at " + i);
    }

    // Walk all set bits and ensure they match the truth map
    int c = 0;
    for (int i = fbs.nextSetBit(0); i >= 0; i = fbs.nextSetBit(i + 1)) {
      Assertions.assertTrue(truth[i], "nextSetBit returned an index that is not set in truth map: " + i);
      c++;
    }
    // Also count true in the truth map
    int t = 0;
    for (boolean b : truth) if (b) t++;
    Assertions.assertEquals(t, c, "Count of set bits mismatch");
  }
}
