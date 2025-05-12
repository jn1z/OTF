package OTF.Simulation;

/**
 * BitSet of fixed length (numBits).
 * This avoids dynamic resizing, which is good since we're messing around with threading.
 */
public final class FixedBitSet {
  private final long[] bits; // Array of longs holding the bits

  /**
   * Creates a new FixedBitSet. The internally allocated long array will be exactly the size needed
   * to accommodate the numBits specified.
   *
   * @param numBits the number of bits needed
   */
  public FixedBitSet(final int numBits) {
    bits = new long[((numBits - 1) >> 6) + 1];
  }

  public boolean get(final int index) {
    final int wordNum = index >> 6; // div 64
    // signed shift will keep a negative index and cause an exception, removing the need for an explicit check.
    final long bitmask = 1L << index;
    return (bits[wordNum] & bitmask) != 0;
  }

  public void set(final int index) {
    final int wordNum = index >> 6; // div 64
    final long bitmask = 1L << index;
    bits[wordNum] |= bitmask;
  }

  public void clear(final int index) {
    final int wordNum = index >> 6; // div 64
    final long bitmask = 1L << index;
    bits[wordNum] &= ~bitmask;
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
    long word = bits[i] >> index;

    if (word != 0) {
      // Long.numberOfTrailingZeros(word) gives the position of the least-significant 1
      // in 'word'.  Adding 'index' converts back to the original bit’s absolute index.
      return index + Long.numberOfTrailingZeros(word);
    }

    // There were no set bits at or after 'index' in this first word.
    // Scan each subsequent 64-bit word until we find a non-zero one.
    while (++i < numWords) {
      word = bits[i];
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