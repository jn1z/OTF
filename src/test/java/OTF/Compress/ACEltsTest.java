package OTF.Compress;

import OTF.BitSetUtils;
import OTF.SmartBitSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ACEltsTest {
  @Test
  void testEmpty() {
    ACElts acElts = new ACElts(SmartBitSet.EMPTY_SMART_BITSET);
    Assertions.assertEquals("{},", acElts.toString().replace(" ",""));

    Assertions.assertEquals(1, acElts.getEltsSize()); // empty bitset is still a bitset
    Assertions.assertFalse(acElts.properSubsetExists(SmartBitSet.EMPTY_SMART_BITSET));
    Assertions.assertEquals(1, acElts.getLiveElts().size());
    Assertions.assertEquals(SmartBitSet.EMPTY_SMART_BITSET, acElts.getLiveElts().get(0));
    acElts.clear();
    Assertions.assertEquals(0, acElts.getEltsSize()); // empty bitset is still a bitset
    Assertions.assertEquals("", acElts.toString());
  }

  @Test
  void testSingleACELts() {
    SmartBitSet b = BitSetUtils.convertListToSmartBitSet(List.of(1,2,3));
    ACElts acElts = new ACElts(b);
    Assertions.assertEquals("{1,2,3},", acElts.toString().replace(" ",""));
    Assertions.assertEquals(1, acElts.getEltsSize());
    Assertions.assertEquals(1, acElts.getLiveElts().size());
    Assertions.assertFalse(acElts.properSubsetExists(SmartBitSet.EMPTY_SMART_BITSET));
    Assertions.assertFalse(acElts.properSubsetExists(b));

    SmartBitSet b2 = BitSetUtils.convertListToSmartBitSet(List.of(1,2,3,4));
    Assertions.assertTrue(acElts.properSubsetExists(b2));

    SmartBitSet b3 = BitSetUtils.convertListToSmartBitSet(List.of(2,3,4,5));
    Assertions.assertFalse(acElts.properSubsetExists(b3));

    Assertions.assertTrue(acElts.minCardinality(3));
    Assertions.assertFalse(acElts.minCardinality(2));
  }

  @Test
  void testMultipleACELts() {
    SmartBitSet b = BitSetUtils.convertListToSmartBitSet(List.of(1,2,3));
    ACElts acElts = new ACElts(b);
    SmartBitSet b2;
    b2 =  BitSetUtils.convertListToSmartBitSet(List.of(1,2,3,4));
    acElts.unifyEltIntoAC(b2, 4);     // {1,2,3,4} ignored due to {1,2,3}
    Assertions.assertEquals("{1,2,3},", acElts.toString().replace(" ",""));
    Assertions.assertEquals(1, acElts.getEltsSize());
    Assertions.assertEquals(1, acElts.getLiveElts().size());

    b2 =  BitSetUtils.convertListToSmartBitSet(List.of(2,3,4));
    acElts.unifyEltIntoAC(b2, 4);     // added
    Assertions.assertEquals(2, acElts.getEltsSize());
    Assertions.assertEquals(2, acElts.getLiveElts().size());

    b2 =  BitSetUtils.convertListToSmartBitSet(List.of(1,4));
    acElts.unifyEltIntoAC(b2, 4);     // added
    Assertions.assertEquals(3, acElts.getEltsSize());
    Assertions.assertEquals(3, acElts.getLiveElts().size());

    b2 =  BitSetUtils.convertListToSmartBitSet(List.of(2));
    acElts.unifyEltIntoAC(b2, 4);     // {2} replaces {1,2,3} and {2,3,4}
    Assertions.assertEquals("{2},{1,4},", acElts.toString().replace(" ",""));
    Assertions.assertEquals(2, acElts.getEltsSize());
    Assertions.assertEquals(2, acElts.getLiveElts().size());

    // both {2} and {1,4} are proper subsets
    Assertions.assertTrue(acElts.properSubsetExists(BitSetUtils.convertListToSmartBitSet(List.of(1,2,3,4))));
  }

  @Test
  void testActivateInvertedIndex() {
    // All BitSets with up to six bits
    List<SmartBitSet> distinctBitSets = IntStream.range(0, 1 << 6)
        .mapToObj(i -> SmartBitSet.valueOf(new long[]{i}))
        .toList();

    // Make all elements distinct, i.e., not subsets/supersets of each other
    int distinctCounter = 6; // distinct element counter
    for (SmartBitSet b: distinctBitSets) {
      b.set(distinctCounter++);
    }

    int nNFA = distinctCounter+1;
    ACElts acElts = new ACElts(distinctBitSets.get(0));
    for(SmartBitSet b: distinctBitSets) {
      acElts.unifyEltIntoAC(b, nNFA);
    }
    Assertions.assertEquals(64, acElts.getEltsSize());
    Assertions.assertFalse(acElts.properSubsetExists(SmartBitSet.EMPTY_SMART_BITSET));

    SmartBitSet b2 = (SmartBitSet) distinctBitSets.get(0).clone();
    b2.set(1000); // superset element
    Assertions.assertTrue(acElts.properSubsetExists(b2));
    acElts.unifyEltIntoAC(b2, nNFA);
    Assertions.assertEquals(64, acElts.getEltsSize());

    // Unify {0} into acElts.
    // This collapses 32 elements into 1, and creates a large deadElts set.
    b2 = BitSetUtils.convertListToSmartBitSet(List.of(0));
    acElts.unifyEltIntoAC(b2, nNFA);
    Assertions.assertEquals(33, acElts.getEltsSize());

    // Add a new element, re-using a deadElt.
    b2 = BitSetUtils.convertListToSmartBitSet(List.of(10,11));
    acElts.unifyEltIntoAC(b2, nNFA);
    Assertions.assertEquals(34, acElts.getEltsSize());

    // clear acElts, including inverted index
    acElts.clear();
  }

  @Test
  void testRebuildInvertedIndex() {
    // the bound for deadElts to rebuild is quite large
    // we'll overcount by using 1024 elements

    // All BitSets with up to ten bits
    List<SmartBitSet> distinctBitSets = IntStream.range(0, 1 << 10)
        .mapToObj(i -> SmartBitSet.valueOf(new long[]{i}))
        .toList();
    // Make all elements distinct, i.e., not subsets/supersets of each other
    int distinctCounter = 11; // distinct element counter
    for (SmartBitSet b: distinctBitSets) {
      b.set(distinctCounter++);
    }

    int nNFA = distinctCounter+1;
    ACElts acElts = new ACElts(distinctBitSets.get(0));
    for(SmartBitSet b: distinctBitSets) {
      acElts.unifyEltIntoAC(b, nNFA);
    }

    // Unify {0} into acElts.
    // This collapses 512 elements into 1, and creates a large deadElts set.
    SmartBitSet b2 = BitSetUtils.convertListToSmartBitSet(List.of(0));
    acElts.unifyEltIntoAC(b2, nNFA);
    Assertions.assertEquals(513, acElts.getEltsSize());
    // exercise iteration over deadElts
    Assertions.assertEquals(513, acElts.getLiveElts().size());

    // Add one more element, triggering rebuild
    b2 = BitSetUtils.convertListToSmartBitSet(List.of(20,21));
    acElts.unifyEltIntoAC(b2, nNFA);
    Assertions.assertEquals(514, acElts.getEltsSize());
  }
}
