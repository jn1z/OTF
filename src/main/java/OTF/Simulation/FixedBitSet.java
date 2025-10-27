package OTF.Simulation;

import java.util.Arrays;

/**
 * BitSet of fixed length (numBits).
 * This avoids dynamic resizing, which is good since we're messing around with threading.
 */
public final class FixedBitSet {
  final long[] bits; // Array of longs holding the bits
  private final long lastWordMask; // mask off bits beyond numBits in the last word

  /**
   * Creates a new FixedBitSet. The internally allocated long array will be exactly the size needed
   * to accommodate the numBits specified.
   *
   * @param numBits the number of bits needed
   */
  public FixedBitSet(final int numBits) {
    this.bits = new long[((numBits - 1) >> 6) + 1];
    final int r = numBits & 63;
    this.lastWordMask = (r == 0) ? -1L : (-1L >>> (64 - r));
  }

  public boolean get(final int index) {
    final int wordNum = index >> 6; // div 64
    final long bitmask = 1L << (index & 63);
    return (bits[wordNum] & bitmask) != 0;
  }

  public void set(final int index) {
    final int wordNum = index >> 6; // div 64
    final long bitmask = 1L << (index & 63);
    bits[wordNum] |= bitmask;
  }

  public void clear(final int index) {
    final int wordNum = index >> 6; // div 64
    final long bitmask = 1L << (index & 63);
    bits[wordNum] &= ~bitmask;
  }

  public void setAll() {
    Arrays.fill(bits, -1L);
    bits[bits.length - 1] &= lastWordMask; // clear tail beyond numBits
  }
  public void and(final FixedBitSet other) {
    for (int i = 0; i < bits.length; i++) bits[i] &= other.bits[i];
  }
  public void andNot(final FixedBitSet other) {
    for (int i = 0; i < bits.length; i++) bits[i] &= ~other.bits[i];
  }

  /**
   * Returns the next set bit in the specified range.
   * If no such bit exists, returns -1.
   */
  public int nextSetBit(final int index) {
    // Determine which 64-bit word in the array holds 'index'.
    int i = index >> 6;

    final int numWords = bits.length; // The exact number of longs needed to hold numBits (<= bits.length)

    if (i >= numWords) {
      return -1; // past end of array
    }

    // Create a view of that word where all bits _below_ 'index' are discarded.
    // Right-shifting the entire long by 'index' bits moves bit 'index' down to position 0,
    // and any lower bits (positions 0..index−1) vanish.
    // unsigned shift by in-word offset, not the absolute index
    long word = bits[i] >> index;
    if (i == numWords - 1) word &= lastWordMask;

    if (word != 0) {
      // Long.numberOfTrailingZeros(word) gives the position of the least-significant 1
      // in 'word'.  Adding 'index' converts back to the original bit’s absolute index.
      return index + Long.numberOfTrailingZeros(word);
    }

    // There were no set bits at or after 'index' in this first word.
    // Scan each subsequent 64-bit word until we find a non-zero one.
    while (++i < numWords) {
      word = bits[i];
      if (i == numWords - 1) word &= lastWordMask;
      if (word != 0) {
        // Found a word with at least one set bit.
        // (i << 6) computes the starting bit-index of this word (i*64).
        // Again use numberOfTrailingZeros to find the offset inside the word.
        return (i << 6) + Long.numberOfTrailingZeros(word);
      }
    }

    return -1;
  }
}