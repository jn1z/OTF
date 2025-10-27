package OTF.Compress;

import OTF.SmartBitSet;

import java.util.*;

/**
 * Inverted index in bitsets.
 * Total number of bitsets is nNFA.
 * Each bitset is of length ACElts.size().
 * bitsetArr[i] is a bitset, where its jth bit == the ith bit of the jth bitset in ACElts.
 */
public final class InvertedIndex {
  SmartBitSet[] inverted;
  private final SmartBitSet nullInvertedElts; // tracks null elements of inverted

  int maxElts; // maximum length of ACElts, corresponds to maximum possible length of bitsets
  private final SmartBitSet tempUnionInv = new SmartBitSet();
  private final SmartBitSet tempIntInv = new SmartBitSet();

  // common root of all ACElts, often empty. This can be an under-approximation.
  private final SmartBitSet dirtyCommonRoot;

  // This keeps us from doing too much work on repeated ORs
  private int MAX_CUTOFF = 500;
  private static final int MAX_FRACTION = 24;
  private static final double MAX_CARD_FRACTION = 0.9;
  private static final int LOOP_CARD_TEST = 50;
  /**
   * Initialize the nNFA inverted bitsets.
   */
  public InvertedIndex(int nNFA, List<SmartBitSet> elts) {
    inverted = new SmartBitSet[nNFA];
    nullInvertedElts = new SmartBitSet(nNFA);
    nullInvertedElts.set(0,nNFA); // completely null at first
    dirtyCommonRoot = new SmartBitSet(nNFA);
    dirtyCommonRoot.set(0,nNFA);
    MAX_CUTOFF = Math.max(MAX_CUTOFF, nNFA / MAX_FRACTION);
    // Initialize inverted array
    final int eltsSize = elts.size();
    for (int j = 0; j < eltsSize; j++) {
      insert(elts.get(j), eltsSize);
    }
  }

  /**
   * Overwrite the jth element of ACElt. Takes nNFA operations.
   */
  public void overwrite(SmartBitSet b, int j) {
    for (int k=0;k<inverted.length;k++) {
      SmartBitSet inv = inverted[k];
      if (inv == null) {
        inverted[k] = inv = new SmartBitSet(this.maxElts);
        nullInvertedElts.clear(k);
      }
      if (b.get(k)) {
        inv.set(j);
      } else {
        inv.clear(j);
      }
    }
    dirtyCommonRoot.dirtyAnd(b);
  }

  // Overwrite only elements that have changed (average-case, this means writing half as many elements).
  // This should be even faster when b is sparse, which is common for the AC use-case.
  public void overwrite(SmartBitSet b, SmartBitSet oldElt, int j) {
    final SmartBitSet tempXOR = (SmartBitSet) b.clone();
    tempXOR.xor(oldElt);
    for (int k = tempXOR.nextSetBit(0); k >= 0; k = tempXOR.nextSetBit(k + 1)) {
      SmartBitSet inv = inverted[k];
      if (inv == null) {
        inverted[k] = inv = new SmartBitSet(this.maxElts);
        nullInvertedElts.clear(k);
      }
      if (b.get(k)) {
        inv.set(j);
      } else {
        inv.clear(j);
      }
    }
    dirtyCommonRoot.dirtyAnd(b);
  }

  /**
   * Insert the jth element of ACElt.
   * Takes <= nNFA operations, on average more like nNFA/2.
   */
  public void insert(SmartBitSet b, int sizeHint) {
    final int index = maxElts++;
    for (int k = b.nextSetBit(0); k >= 0; k = b.nextSetBit(k + 1)) {
      SmartBitSet inv = inverted[k];
      if (inv == null) {
        inverted[k] = inv = new SmartBitSet(sizeHint);
        nullInvertedElts.clear(k);
      }
      inv.set(index);
    }
    dirtyCommonRoot.dirtyAnd(b);
  }

  /**
   * Find the first element of acElts (ignoring deadElts) that's a subset of b, or return null.
   */
  public SmartBitSet findFirstSubset(List<SmartBitSet> acElts, SmartBitSet deadElts, SmartBitSet b) {
    if (!dirtyCommonRoot.isEmpty() && !dirtyCommonRoot.isSubset(b)) {
      // If common root isn't a subset of b, then clearly no ACElt is a subset of b
      // This is a shortcut for an element of bShort corresponding to an element of inverted[i] that's all 1s
      return null;
    }
    filterPotentialSubsets(deadElts, b);

    return determineFirstSubset(acElts, b);
  }

  private void filterPotentialSubsets(SmartBitSet deadElts, SmartBitSet b) {
    final SmartBitSet bShort = (SmartBitSet)b.clone();
    bShort.dirtyOr(nullInvertedElts); // ignore null inverted bits

    // Start by including all dead elements
    tempUnionInv.clear();
    tempUnionInv.dirtyOr(deadElts);

    final int maxCardinality = (int)(maxElts * MAX_CARD_FRACTION);
    int maxCount = 0;
    // For each clear bit in 'b', OR the corresponding inverted BitSet
    final SmartBitSet[] invertedArr = inverted;
    for (int i = bShort.nextClearBit(0); i >= 0 && i < inverted.length; i = bShort.nextClearBit(i + 1)) {
      tempUnionInv.dirtyOr(invertedArr[i]);
      if (++maxCount > MAX_CUTOFF) {
        break; // Cutoff to avoid diminishing returns with repeated iterations
      }
      if (maxCount % LOOP_CARD_TEST == 0) {
        tempUnionInv.markAsDirty();
        if (tempUnionInv.cardinality() > maxCardinality) {
          break; // Cutoff when cardinality is close to max
        }
      }
    }
  }

  private SmartBitSet determineFirstSubset(List<SmartBitSet> acElts, SmartBitSet b) {
    // Iterate over potential indices
    for (int i = tempUnionInv.nextClearBit(0); i >= 0 && i < maxElts; i = tempUnionInv.nextClearBit(i + 1)) {
      final SmartBitSet oldElt = acElts.get(i);
      if (oldElt.isSubset(b)) {
        return oldElt;
      }
    }
    return null;
  }

  /**
   * Find the indices of acElts (ignoring deadElts) that are supersets of b.
   * @param filterOnly if filterOnly, then only filter down, don't fully validate supersets.
   */
  public SmartBitSet findSupersetIndices(
      List<SmartBitSet> acElts, SmartBitSet deadElts, SmartBitSet b, boolean filterOnly) {
    if (acElts.isEmpty()) {
      return SmartBitSet.EMPTY_SMART_BITSET; // empty list (can't happen): nothing can be a superset
    }
    if (b.isEmpty()) {
      // All zeroes, so everything is a superset. Return all (non-dead) elements
      final SmartBitSet supersets = (SmartBitSet) deadElts.clone();
      supersets.flip(0, acElts.size());
      return supersets;
    }
    if (b.intersects(nullInvertedElts)) {
      return SmartBitSet.EMPTY_SMART_BITSET; // bits in b but not in InvertedIndex: nothing can be a superset
    }

    filterPotentialSupersets(deadElts, b);

    if (!filterOnly) {
      validateSupersets(acElts, b);
    }

    return tempIntInv;
  }

  private void filterPotentialSupersets(SmartBitSet deadElts, SmartBitSet b) {
    final int firstSetBit = b.nextSetBit(0);
    final SmartBitSet[] invertedArr = inverted;

    tempIntInv.clear();
    tempIntInv.dirtyOr(invertedArr[firstSetBit]);
    // Exclude dead elements
    tempIntInv.dirtyAndNot(deadElts);

    final int minCardinality = (int)(maxElts * (1.0f -MAX_CARD_FRACTION));
    int maxCount = 0;
    // For each set bit in 'b', AND the corresponding inverted BitSet
    for (int i = b.nextSetBit(firstSetBit+1); i >= 0; i = b.nextSetBit(i + 1)) {
      tempIntInv.dirtyAnd(invertedArr[i]);
      if (tempIntInv.isEmpty()) {
        return;
      }
      if (++maxCount > MAX_CUTOFF) {
        return; // Cutoff to avoid diminishing returns with repeated iterations
      }
      if (maxCount % LOOP_CARD_TEST == 0) {
        tempIntInv.markAsDirty();
        if (tempIntInv.cardinality() < minCardinality) {
          return; // Cutoff when cardinality is close to min
        }
      }
    }
  }

  private void validateSupersets(List<SmartBitSet> acElts, SmartBitSet b) {
    // Iterate over potential indices
    for (int i = tempIntInv.nextSetBit(0); i >= 0 && i < maxElts; i = tempIntInv.nextSetBit(i + 1)) {
      final SmartBitSet oldElt = acElts.get(i);
      if (!b.isSubset(oldElt)) {
        tempIntInv.clear(i);
      }
    }
  }

  public void clear() {
    this.inverted = null;
    this.maxElts = 0;
  }

  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append("Inverted:\n");
    for (int i = 0; i < this.inverted.length; i++) {
      sb.append(i).append(" : ").append(inverted[i]).append("\n");
    }
    return sb.toString();
  }
}
