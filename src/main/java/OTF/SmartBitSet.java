package OTF;

import java.util.Arrays;
import java.util.Comparator;

/**
 * SmartBitSet is adapted from BitSet, with additional:
 *   access to words and wordsInUse (used in faster subset calculation)
 *   cached cardinality and hashCode
 *   loop unrolling in or(), and(), andNot(), equals(), intersects(), isSubset()
 * Potentially we could also:
 *   remove checkInvariants() and checkRange() validations
 */
public class SmartBitSet implements Cloneable {
  public static final Comparator<SmartBitSet> SMART_CARDINALITY_COMPARATOR =
      Comparator.comparingInt(SmartBitSet::cardinality); // ascending order of cardinality
  /*
   * SmartBitSets are packed into arrays of "words."  Currently, a word is
   * a long, which consists of 64 bits, requiring 6 address bits.
   * The choice of word size is determined purely by performance concerns.
   */
  private final static int ADDRESS_BITS_PER_WORD = 6;
  public final static int BITS_PER_WORD = 1 << ADDRESS_BITS_PER_WORD;
  private final static int BIT_INDEX_MASK = BITS_PER_WORD - 1;

  /* Used to shift left or right for a partial word mask */
  private static final long WORD_MASK = 0xffffffffffffffffL;
  public static SmartBitSet EMPTY_SMART_BITSET = new SmartBitSet();
  public boolean dirtyCardinality = true;
  public int cardinality = -1;
  public boolean dirtyHash= true;
  private int hash = -1;

  private static final int LOOP_UNROLL = 4;

  /**
   * The internal field corresponding to the serialField "bits".
   */
  public long[] words;

  /**
   * The number of words in the logical size of this SmartBitSet.
   */
  public transient int wordsInUse = 0;

  /**
   * Whether the size of "words" is user-specified.  If so, we assume
   * the user knows what he's doing and try harder to preserve it.
   */
  private transient boolean sizeIsSticky = false;

  /**
   * Given a bit index, return word index containing it.
   */
  private static int wordIndex(int bitIndex) {
    return bitIndex >> ADDRESS_BITS_PER_WORD;
  }

  /**
   * Every public method must preserve these invariants.
   */
  private void checkInvariants() {
    assert(wordsInUse == 0 || words[wordsInUse - 1] != 0);
    assert wordsInUse <= words.length;
    assert(wordsInUse == words.length || words[wordsInUse] == 0);
  }

  /**
   * Sets the field wordsInUse to the logical size in words of the bit set.
   * WARNING:This method assumes that the number of words actually in use is
   * less than or equal to the current value of wordsInUse!
   */
  private void recalculateWordsInUse() {
    markAsDirty();
    // Traverse the SmartBitSet until a used word is found
    int i;
    for (i = wordsInUse-1; i >= 0; i--)
      if (words[i] != 0)
        break;

    wordsInUse = i+1; // The new logical size
  }

  private void markAsDirty() {
    dirtyCardinality = true;
    dirtyHash = true;
  }

  /**
   * Creates a new bit set. All bits are initially {@code false}.
   */
  public SmartBitSet() {
    initWords(BITS_PER_WORD);
    sizeIsSticky = false;
  }

  /**
   * Creates a bit set whose initial size is large enough to explicitly
   * represent bits with indices in the range {@code 0} through
   * {@code nbits-1}. All bits are initially {@code false}.
   *
   * @param  nbits the initial size of the bit set
   * @throws NegativeArraySizeException if the specified initial size
   *         is negative
   */
  public SmartBitSet(int nbits) {
    // nbits can't be negative; size 0 is OK
    if (nbits < 0)
      throw new NegativeArraySizeException("nbits < 0: " + nbits);

    initWords(nbits);
    sizeIsSticky = true;
  }

  private void initWords(int nbits) {
    words = new long[wordIndex(nbits-1) + 1];
  }

  /**
   * Creates a bit set using words as the internal representation.
   * The last word (if there is one) must be non-zero.
   */
  private SmartBitSet(long[] words) {
    this.words = words;
    this.wordsInUse = words.length;
    checkInvariants();
  }

  /**
   * Returns a new bit set containing all the bits in the given long array.
   *
   * <p>More precisely,
   * <br>{@code SmartBitSet.valueOf(longs).get(n) == ((longs[n/64] & (1L<<(n%64))) != 0)}
   * <br>for all {@code n < 64 * longs.length}.
   *
   * <p>This method is equivalent to
   * {@code SmartBitSet.valueOf(LongBuffer.wrap(longs))}.
   *
   * @param longs a long array containing a little-endian representation
   *        of a sequence of bits to be used as the initial bits of the
   *        new bit set
   */
  public static SmartBitSet valueOf(long[] longs) {
    int n;
    for (n = longs.length; n > 0 && longs[n - 1] == 0; n--)
      ;
    return new SmartBitSet(Arrays.copyOf(longs, n));
  }

  /**
   * Ensures that the SmartBitSet can hold enough words.
   * @param wordsRequired the minimum acceptable number of words.
   */
  private void ensureCapacity(int wordsRequired) {
    if (words.length < wordsRequired) {
      // Allocate larger of doubled size or required size
      int request = Math.max(2 * words.length, wordsRequired);
      words = Arrays.copyOf(words, request);
      sizeIsSticky = false;
      markAsDirty();
    }
  }

  /**
   * Ensures that the SmartBitSet can accommodate a given wordIndex,
   * temporarily violating the invariants.  The caller must
   * restore the invariants before returning to the user,
   * possibly using recalculateWordsInUse().
   * @param wordIndex the index to be accommodated.
   */
  private void expandTo(int wordIndex) {
    int wordsRequired = wordIndex+1;
    if (wordsInUse < wordsRequired) {
      ensureCapacity(wordsRequired);
      wordsInUse = wordsRequired;
    }
  }

  /**
   * Checks that fromIndex ... toIndex is a valid range of bit indices.
   */
  private static void checkRange(int fromIndex, int toIndex) {
    if (fromIndex < 0)
      throw new IndexOutOfBoundsException("fromIndex < 0: " + fromIndex);
    if (toIndex < 0)
      throw new IndexOutOfBoundsException("toIndex < 0: " + toIndex);
    if (fromIndex > toIndex)
      throw new IndexOutOfBoundsException("fromIndex: " + fromIndex +
          " > toIndex: " + toIndex);
  }

  /**
   * Sets each bit from the specified {@code fromIndex} (inclusive) to the
   * specified {@code toIndex} (exclusive) to the complement of its current
   * value.
   *
   * @param  fromIndex index of the first bit to flip
   * @param  toIndex index after the last bit to flip
   * @throws IndexOutOfBoundsException if {@code fromIndex} is negative,
   *         or {@code toIndex} is negative, or {@code fromIndex} is
   *         larger than {@code toIndex}
   */
  public void flip(int fromIndex, int toIndex) {
    checkRange(fromIndex, toIndex);

    if (fromIndex == toIndex)
      return;

    int startWordIndex = wordIndex(fromIndex);
    int endWordIndex   = wordIndex(toIndex - 1);
    expandTo(endWordIndex);

    long firstWordMask = WORD_MASK << fromIndex;
    long lastWordMask  = WORD_MASK >>> -toIndex;
    if (startWordIndex == endWordIndex) {
      // Case 1: One word
      words[startWordIndex] ^= (firstWordMask & lastWordMask);
    } else {
      // Case 2: Multiple words
      // Handle first word
      words[startWordIndex] ^= firstWordMask;

      // Handle intermediate words, if any
      for (int i = startWordIndex+1; i < endWordIndex; i++)
        words[i] ^= WORD_MASK;

      // Handle last word
      words[endWordIndex] ^= lastWordMask;
    }

    recalculateWordsInUse();
    checkInvariants();
  }

  /**
   * Sets the bit at the specified index to {@code true}.
   *
   * @param  bitIndex a bit index
   * @throws IndexOutOfBoundsException if the specified index is negative
   */
  public void set(int bitIndex) {
    if (bitIndex < 0)
      throw new IndexOutOfBoundsException("bitIndex < 0: " + bitIndex);

    int wordIndex = wordIndex(bitIndex);
    expandTo(wordIndex);

    words[wordIndex] |= (1L << bitIndex); // Restores invariants

    checkInvariants();
    markAsDirty();
  }

  /**
   * Sets the bit at the specified index to the specified value.
   *
   * @param  bitIndex a bit index
   * @param  value a boolean value to set
   * @throws IndexOutOfBoundsException if the specified index is negative
   */
  public void set(int bitIndex, boolean value) {
    if (value)
      set(bitIndex);
    else
      clear(bitIndex);
  }

  /**
   * Sets the bits from the specified {@code fromIndex} (inclusive) to the
   * specified {@code toIndex} (exclusive) to {@code true}.
   *
   * @param  fromIndex index of the first bit to be set
   * @param  toIndex index after the last bit to be set
   * @throws IndexOutOfBoundsException if {@code fromIndex} is negative,
   *         or {@code toIndex} is negative, or {@code fromIndex} is
   *         larger than {@code toIndex}
   */
  public void set(int fromIndex, int toIndex) {
    checkRange(fromIndex, toIndex);

    if (fromIndex == toIndex)
      return;

    // Increase capacity if necessary
    int startWordIndex = wordIndex(fromIndex);
    int endWordIndex   = wordIndex(toIndex - 1);
    expandTo(endWordIndex);

    long firstWordMask = WORD_MASK << fromIndex;
    long lastWordMask  = WORD_MASK >>> -toIndex;
    if (startWordIndex == endWordIndex) {
      // Case 1: One word
      words[startWordIndex] |= (firstWordMask & lastWordMask);
    } else {
      // Case 2: Multiple words
      // Handle first word
      words[startWordIndex] |= firstWordMask;

      // Handle intermediate words, if any
      for (int i = startWordIndex+1; i < endWordIndex; i++)
        words[i] = WORD_MASK;

      // Handle last word (restores invariants)
      words[endWordIndex] |= lastWordMask;
    }

    checkInvariants();
    markAsDirty();

  }

  /**
   * Sets the bit specified by the index to {@code false}.
   *
   * @param  bitIndex the index of the bit to be cleared
   * @throws IndexOutOfBoundsException if the specified index is negative
   */
  public void clear(int bitIndex) {
    if (bitIndex < 0)
      throw new IndexOutOfBoundsException("bitIndex < 0: " + bitIndex);

    int wordIndex = wordIndex(bitIndex);
    if (wordIndex >= wordsInUse)
      return;

    words[wordIndex] &= ~(1L << bitIndex);

    recalculateWordsInUse();
    checkInvariants();
    markAsDirty();

  }

  /**
   * Sets all of the bits in this SmartBitSet to {@code false}.
   */
  public void clear() {
    while (wordsInUse > 0)
      words[--wordsInUse] = 0;
    markAsDirty();

  }

  /**
   * Returns the value of the bit with the specified index. The value
   * is {@code true} if the bit with the index {@code bitIndex}
   * is currently set in this {@code SmartBitSet}; otherwise, the result
   * is {@code false}.
   *
   * @param  bitIndex   the bit index
   * @return the value of the bit with the specified index
   * @throws IndexOutOfBoundsException if the specified index is negative
   */
  public boolean get(int bitIndex) {
    if (bitIndex < 0)
      throw new IndexOutOfBoundsException("bitIndex < 0: " + bitIndex);

    checkInvariants();

    int wordIndex = wordIndex(bitIndex);
    return (wordIndex < wordsInUse)
        && ((words[wordIndex] & (1L << bitIndex)) != 0);
  }

  /**
   * Returns the index of the first bit that is set to {@code true}
   * that occurs on or after the specified starting index. If no such
   * bit exists then {@code -1} is returned.
   *
   * <p>To iterate over the {@code true} bits in a {@code SmartBitSet},
   * use the following loop:
   *
   *  <pre> {@code
   * for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i+1)) {
   *     // operate on index i here
   * }}</pre>
   *
   * @param  fromIndex the index to start checking from (inclusive)
   * @return the index of the next set bit, or {@code -1} if there
   *         is no such bit
   * @throws IndexOutOfBoundsException if the specified index is negative
   */
  public int nextSetBit(int fromIndex) {
    if (fromIndex < 0)
      throw new IndexOutOfBoundsException("fromIndex < 0: " + fromIndex);

    checkInvariants();

    int u = wordIndex(fromIndex);
    if (u >= wordsInUse)
      return -1;

    long word = words[u] & (WORD_MASK << fromIndex);

    while (true) {
      if (word != 0)
        return (u * BITS_PER_WORD) + Long.numberOfTrailingZeros(word);
      if (++u == wordsInUse)
        return -1;
      word = words[u];
    }
  }

  /**
   * Returns the index of the first bit that is set to {@code false}
   * that occurs on or after the specified starting index.
   *
   * @param  fromIndex the index to start checking from (inclusive)
   * @return the index of the next clear bit
   * @throws IndexOutOfBoundsException if the specified index is negative
   */
  public int nextClearBit(int fromIndex) {
    // Neither spec nor implementation handle SmartBitSets of maximal length.
    // See 4816253.
    if (fromIndex < 0)
      throw new IndexOutOfBoundsException("fromIndex < 0: " + fromIndex);

    checkInvariants();

    int u = wordIndex(fromIndex);
    if (u >= wordsInUse)
      return fromIndex;

    long word = ~words[u] & (WORD_MASK << fromIndex);

    while (true) {
      if (word != 0)
        return (u * BITS_PER_WORD) + Long.numberOfTrailingZeros(word);
      if (++u == wordsInUse)
        return wordsInUse * BITS_PER_WORD;
      word = ~words[u];
    }
  }

  /**
   * Returns the "logical size" of this {@code SmartBitSet}: the index of
   * the highest set bit in the {@code SmartBitSet} plus one. Returns zero
   * if the {@code SmartBitSet} contains no set bits.
   *
   * @return the logical size of this {@code SmartBitSet}
   */
  public int length() {
    if (wordsInUse == 0)
      return 0;

    return BITS_PER_WORD * (wordsInUse - 1) +
        (BITS_PER_WORD - Long.numberOfLeadingZeros(words[wordsInUse - 1]));
  }

  /**
   * Returns true if this {@code SmartBitSet} contains no bits that are set
   * to {@code true}.
   *
   * @return boolean indicating whether this {@code SmartBitSet} is empty
   */
  public boolean isEmpty() {
    return wordsInUse == 0;
  }

  /**
   * Returns true if the specified {@code SmartBitSet} has any bits set to
   * {@code true} that are also set to {@code true} in this {@code SmartBitSet}.
   *
   * @param  set {@code SmartBitSet} to intersect with
   * @return boolean indicating whether this {@code SmartBitSet} intersects
   *         the specified {@code SmartBitSet}
   */
  public boolean intersects(SmartBitSet set) {
    // Determine the number of words to compare.
    int common = Math.min(this.wordsInUse, set.wordsInUse);
    // Process any leftover words (if common is not a multiple of LOOP_UNROLL).
    int remainder = common % LOOP_UNROLL;
    int i = common - 1;
    int limit = common - remainder;

    // Process the remainder words one-by-one.
    while (i >= limit) {
      if ((this.words[i] & set.words[i]) != 0)
        return true;
      i--;
    }

    // Process blocks of 4 words at a time.
    for (i = limit - 1; i >= 0; i -= LOOP_UNROLL) {
      if ((this.words[i]     & set.words[i])     != 0 ||
          (this.words[i - 1] & set.words[i - 1]) != 0 ||
          (this.words[i - 2] & set.words[i - 2]) != 0 ||
          (this.words[i - 3] & set.words[i - 3]) != 0)
        return true;
    }

    return false;
  }


  /**
   * Returns the number of bits set to {@code true} in this {@code SmartBitSet}.
   */
  public int cardinality() {
    if (!dirtyCardinality) {
      return cardinality;
    }
    int sum = 0;
    for (int i = 0; i < wordsInUse; i++)
      sum += Long.bitCount(words[i]);
    cardinality = sum;
    dirtyCardinality = false;
    return sum;
  }

  /**
   * Performs a logical <b>AND</b> of this target bit set with the
   * argument bit set. This bit set is modified so that each bit in it
   * has the value {@code true} if and only if it both initially
   * had the value {@code true} and the corresponding bit in the
   * bit set argument also had the value {@code true}.
   *
   * @param set a bit set
   */
  public void and(SmartBitSet set) {
    // Process only up to the smaller number of words.
    final int commonWords = Math.min(this.wordsInUse, set.wordsInUse);
    int i = 0;
    final int limit = commonWords - (commonWords % LOOP_UNROLL);

    // Loop unrolling: process LOOP_UNROLL words at a time
    while (i < limit) {
      this.words[i]     &= set.words[i];
      this.words[i + 1] &= set.words[i + 1];
      this.words[i + 2] &= set.words[i + 2];
      this.words[i + 3] &= set.words[i + 3];
      i += LOOP_UNROLL;
    }
    // Process any remaining words one-by-one.
    while (i < commonWords) {
      this.words[i] &= set.words[i];
      i++;
    }

    // If this BitSet has more words than the other, clear the remaining words.
    if (this.wordsInUse > commonWords) {
      // Using System.arraycopy-like performance with Arrays.fill
      Arrays.fill(this.words, commonWords, this.wordsInUse, 0L);
    }

    recalculateWordsInUse();
    checkInvariants();
    markAsDirty();
  }

  /**
   * Performs a logical <b>OR</b> of this bit set with the bit set
   * argument. This bit set is modified so that a bit in it has the
   * value {@code true} if and only if it either already had the
   * value {@code true} or the corresponding bit in the bit set
   * argument has the value {@code true}.
   *
   * @param set a bit set
   */
  public void or(SmartBitSet set) {
    // Determine the new wordsInUse after the OR operation
    final int newWordsInUse = Math.max(this.wordsInUse, set.wordsInUse);

    // Ensure our words array is large enough
    if (this.words.length < newWordsInUse) {
      this.words = Arrays.copyOf(this.words, newWordsInUse);
    }

    // Process the common words (i.e. words present in both sets)
    final int commonWords = Math.min(this.wordsInUse, set.wordsInUse);
    int i = 0;
    int limit = commonWords - (commonWords % LOOP_UNROLL);
    while (i < limit) {
      this.words[i]     |= set.words[i];
      this.words[i + 1] |= set.words[i + 1];
      this.words[i + 2] |= set.words[i + 2];
      this.words[i + 3] |= set.words[i + 3];
      i += LOOP_UNROLL;
    }
    // Process any remaining words in the common portion
    while (i < commonWords) {
      this.words[i] |= set.words[i];
      i++;
    }

    if (set.wordsInUse > this.wordsInUse) {
      System.arraycopy(set.words, this.wordsInUse, this.words, this.wordsInUse, set.wordsInUse - this.wordsInUse);
    }

    // Update the wordsInUse to reflect the union of both sets
    this.wordsInUse = newWordsInUse;

    checkInvariants();
    markAsDirty();
  }


  /**
   * Clears the bits in this {@code SmartBitSet} whose corresponding
   * bit is set in the specified {@code SmartBitSet}.
   *
   * @param  set the {@code SmartBitSet} with which to mask this
   *         {@code SmartBitSet}
   */
  public void andNot(SmartBitSet set) {
    // Calculate the number of words to process (the minimum between the two sets)
    final int minWords = Math.min(this.wordsInUse, set.wordsInUse);
    int i = 0;

    // Loop unrolling: process 4 words per iteration for better performance
    final int limit = minWords - (minWords % LOOP_UNROLL);
    while (i < limit) {
      this.words[i]     &= ~set.words[i];
      this.words[i + 1] &= ~set.words[i + 1];
      this.words[i + 2] &= ~set.words[i + 2];
      this.words[i + 3] &= ~set.words[i + 3];
      i += LOOP_UNROLL;
    }

    // Process any remaining words one-by-one
    while (i < minWords) {
      this.words[i] &= ~set.words[i];
      i++;
    }

    recalculateWordsInUse();
    checkInvariants();
    markAsDirty();
  }

  public boolean isSubset(SmartBitSet sup) {
    long[] subWords = this.words;
    long[] supWords = sup.words;
    int subWordsInUse = this.wordsInUse;
    int supWordsInUse = sup.wordsInUse;

    int i = 0;
    int limit = subWordsInUse - (subWordsInUse % LOOP_UNROLL);
    // Process words in blocks of 4.
    for (; i < limit; i += LOOP_UNROLL) {
      long supWord0 = (i < supWordsInUse ? supWords[i] : 0L);
      long supWord1 = ((i + 1) < supWordsInUse ? supWords[i + 1] : 0L);
      long supWord2 = ((i + 2) < supWordsInUse ? supWords[i + 2] : 0L);
      long supWord3 = ((i + 3) < supWordsInUse ? supWords[i + 3] : 0L);
      if ((subWords[i]     & ~supWord0) != 0L ||
          (subWords[i + 1] & ~supWord1) != 0L ||
          (subWords[i + 2] & ~supWord2) != 0L ||
          (subWords[i + 3] & ~supWord3) != 0L) {
        return false;
      }
    }
    // Process any remaining words one-by-one.
    for (; i < subWordsInUse; i++) {
      long supWord = (i < supWordsInUse ? supWords[i] : 0L);
      if ((subWords[i] & ~supWord) != 0L) {
        return false;
      }
    }
    return true;
  }


  public int hashCode() {
    if (!dirtyHash) {
      return hash;
    }
    long h = 1234;
    for (int i = wordsInUse; --i >= 0; )
      h ^= words[i] * (i + 1);

    hash = (int)((h >> 32) ^ h);
    dirtyHash = false;
    return hash;
  }

  /**
   * Returns the number of bits of space actually in use by this
   * {@code SmartBitSet} to represent bit values.
   * The maximum element in the set is the size - 1st element.
   *
   * @return the number of bits currently in this bit set
   */
  public int size() {
    return words.length * BITS_PER_WORD;
  }

  /**
   * Compares this object against the specified object.
   * The result is {@code true} if and only if the argument is
   * not {@code null} and is a {@code SmartBitSet} object that has
   * exactly the same set of bits set to {@code true} as this bit
   * set. That is, for every nonnegative {@code int} index {@code k},
   * <pre>((SmartBitSet)obj).get(k) == this.get(k)</pre>
   * must be true. The current sizes of the two bit sets are not compared.
   */
  public boolean equals(Object obj) {
    if (!(obj instanceof SmartBitSet set))
      return false;
    if (this == obj)
      return true;

    if (wordsInUse != set.wordsInUse)
      return false;

    checkInvariants();
    set.checkInvariants();

    int i = 0;
    // Process blocks of 4 longs at a time.
    final int limit = wordsInUse - (wordsInUse % LOOP_UNROLL);
    while (i < limit) {
      if (this.words[i] != set.words[i] ||
          this.words[i + 1] != set.words[i + 1] ||
          this.words[i + 2] != set.words[i + 2] ||
          this.words[i + 3] != set.words[i + 3]) {
        return false;
      }
      i += LOOP_UNROLL;
    }
    // Process any remaining words.
    while (i < wordsInUse) {
      if (this.words[i] != set.words[i])
        return false;
      i++;
    }
    return true;
  }

  /**
   * Cloning this {@code SmartBitSet} produces a new {@code SmartBitSet}
   * that is equal to it.
   * The clone of the bit set is another bit set that has exactly the
   * same bits set to {@code true} as this bit set.
   */
  public Object clone() {
    if (! sizeIsSticky)
      trimToSize();

    try {
      SmartBitSet result = (SmartBitSet) super.clone();
      result.words = words.clone();
      result.checkInvariants();
      result.dirtyCardinality = this.dirtyCardinality;
      result.cardinality = this.cardinality;
      result.dirtyHash = this.dirtyHash;
      result.hash = this.hash;
      return result;
    } catch (CloneNotSupportedException e) {
      throw new InternalError();
    }
  }

  /**
   * Attempts to reduce internal storage used for the bits in this bit set.
   * Calling this method may, but is not required to, affect the value
   * returned by a subsequent call to the {@link #size()} method.
   */
  void trimToSize() {
    if (wordsInUse != words.length) {
      words = Arrays.copyOf(words, wordsInUse);
      checkInvariants();
    }
  }

  public String toString() {
    checkInvariants();

    int numBits = (wordsInUse > 128) ?
        cardinality() : wordsInUse * BITS_PER_WORD;
    StringBuilder b = new StringBuilder(6*numBits + 2);
    b.append('{');

    int i = nextSetBit(0);
    if (i != -1) {
      b.append(i);
      for (i = nextSetBit(i+1); i >= 0; i = nextSetBit(i+1)) {
        int endOfRun = nextClearBit(i);
        do { b.append(", ").append(i); }
        while (++i < endOfRun);
      }
    }

    b.append('}');
    return b.toString();
  }
}
