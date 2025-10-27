package OTF.Compress;

import OTF.SmartBitSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static OTF.BitSetUtils.convertListToSmartBitSet;


public class InvertedIndexTest {
  @Test
  void testInvertedInternals() {
    List<SmartBitSet> inputSet = inputSet();
    InvertedIndex st = new InvertedIndex(10, inputSet);
    Assertions.assertEquals(inputSet.size(), st.maxElts);
    Assertions.assertEquals(10, st.inverted.length);

    SmartBitSet b = convertListToSmartBitSet(List.of(2, 3, 5, 6));
    Assertions.assertEquals(b, st.inverted[3]);

    st.overwrite(b, 3);
    Assertions.assertEquals(b, st.inverted[3]);

    st.overwrite(new SmartBitSet(), 3);
    Assertions.assertEquals(convertListToSmartBitSet(List.of(2, 5, 6)), st.inverted[3]);

    st.clear();
    Assertions.assertNull(st.inverted);
  }

  @Test
  void testSubsetQuery() {
    List<SmartBitSet> inputSet = inputSet();
    List<SmartBitSet> testSet = testSet();
    InvertedIndex st = new InvertedIndex(10, inputSet);
    testSubsetQueryInternal(st, testSet, inputSet);
  }

  private static void testSubsetQueryInternal(InvertedIndex st, List<SmartBitSet> testSet, List<SmartBitSet> inputSet) {
    SmartBitSet empty = new SmartBitSet();
    Assertions.assertNull(st.findFirstSubset(inputSet, empty, testSet.get(0)));
    Assertions.assertEquals(inputSet.get(0), st.findFirstSubset(inputSet, empty, testSet.get(1)));

    Assertions.assertNull(st.findFirstSubset(inputSet, empty, testSet.get(2)));
    Assertions.assertEquals(inputSet.get(10), st.findFirstSubset(inputSet, empty, testSet.get(3)));

    Assertions.assertNull(st.findFirstSubset(inputSet, empty, testSet.get(4)));
    Assertions.assertNull(st.findFirstSubset(inputSet, empty, testSet.get(5)));
    Assertions.assertEquals(inputSet.get(7), st.findFirstSubset(inputSet, empty, testSet.get(6)));

    Assertions.assertNull(st.findFirstSubset(inputSet, empty, testSet.get(7)));
    Assertions.assertEquals(inputSet.get(9), st.findFirstSubset(inputSet, empty, testSet.get(8)));
  }

  @Test
  void testSupersetQuery() {
    List<SmartBitSet> inputSet = inputSet();
    List<SmartBitSet> testSet = testSet();
    InvertedIndex st = new InvertedIndex(10, inputSet);

    testSupersetQueryInternal(st, testSet, inputSet);
  }

  private static void testSupersetQueryInternal(InvertedIndex st, List<SmartBitSet> testSet, List<SmartBitSet> inputSet) {
    SmartBitSet empty = new SmartBitSet();

    Assertions.assertEquals(1, findSupersets(st, inputSet, empty, testSet.get(0)).size());
    Assertions.assertTrue(findSupersets(st, inputSet, empty, testSet.get(0)).contains(inputSet.get(0)));

    Assertions.assertEquals(0, findSupersets(st, inputSet, empty, testSet.get(1)).size());
    Assertions.assertEquals(4, findSupersets(st, inputSet, empty, testSet.get(2)).size());
    Assertions.assertTrue(findSupersets(st, inputSet, empty, testSet.get(2)).contains(inputSet.get(2)));
    Assertions.assertTrue(findSupersets(st, inputSet, empty, testSet.get(2)).contains(inputSet.get(3)));
    Assertions.assertTrue(findSupersets(st, inputSet, empty, testSet.get(2)).contains(inputSet.get(5)));
    Assertions.assertTrue(findSupersets(st, inputSet, empty, testSet.get(2)).contains(inputSet.get(6)));

    Assertions.assertEquals(0, findSupersets(st, inputSet, empty, testSet.get(3)).size());
    Assertions.assertEquals(0, findSupersets(st, inputSet, empty, testSet.get(4)).size());
    Assertions.assertEquals(0, findSupersets(st, inputSet, empty, testSet.get(5)).size());

    Assertions.assertEquals(1, findSupersets(st, inputSet, empty, testSet.get(6)).size());
    Assertions.assertTrue(findSupersets(st, inputSet, empty, testSet.get(6)).contains(inputSet.get(7)));

    Assertions.assertEquals(2, findSupersets(st, inputSet, empty, testSet.get(7)).size());
    Assertions.assertTrue(findSupersets(st, inputSet, empty, testSet.get(7)).contains(inputSet.get(0)));
    Assertions.assertTrue(findSupersets(st, inputSet, empty, testSet.get(7)).contains(inputSet.get(2)));

    Assertions.assertEquals(1, findSupersets(st, inputSet, empty, testSet.get(8)).size());
    Assertions.assertTrue(findSupersets(st, inputSet, empty, testSet.get(8)).contains(inputSet.get(7)));
  }
/*
  @Test
  void testSmallTrees() {
    List<SmartBitSet> inputSets = new ArrayList<>();
    SmartBitSet b012 = convertListToSmartBitSet(Arrays.asList(0,1,2));
    inputSets.add(b012);
    InvertedIndex st = new InvertedIndex(10, inputSets);

    SmartBitSet b01 = convertListToSmartBitSet(Arrays.asList(0,1));
    Assertions.assertNull(st.findFirstSubset(b01));

    Assertions.assertEquals(b012, st.findFirstSubset(b012));

    Assertions.assertEquals(1, findSupersets(st, b01).size());
    Assertions.assertTrue(findSupersets(st, b01).contains(b012));

    SmartBitSet b0123 = convertListToSmartBitSet(Arrays.asList(0,1,2,3));
    Assertions.assertTrue(findSupersets(st, b0123).isEmpty());
  }
*/
  @Test
  void testAdd() {
    List<SmartBitSet> inputSet = inputSet();
    List<SmartBitSet> testSet = testSet();
    InvertedIndex st = new InvertedIndex(10, List.of(inputSet.get(0), inputSet.get(1), inputSet.get(2)));
    for(int i=3;i<inputSet.size();i++) {
      st.insert(inputSet.get(i), inputSet.size());
    }
    testSubsetQueryInternal(st, testSet, inputSet);
    testSupersetQueryInternal(st, testSet, inputSet);
  }

  @Test
  void testReplace() {
    List<SmartBitSet> inputSet = inputSet();
    List<SmartBitSet> testSet = testSet();
    InvertedIndex st = new InvertedIndex(10, inputSet);

    testSubsetQueryInternal(st, testSet, inputSet);
    testSupersetQueryInternal(st, testSet, inputSet);

    st.overwrite(inputSet.get(0), 0);     // Replace entry with itself, should be a no-op
    testSubsetQueryInternal(st, testSet, inputSet);
    testSupersetQueryInternal(st, testSet, inputSet);

    SmartBitSet b = convertListToSmartBitSet(Arrays.asList(0, 1, 3));
    st.overwrite(b, 0);

    SmartBitSet deadElts = new SmartBitSet();
    Assertions.assertEquals(1, findSupersets(st, inputSet, deadElts, testSet.get(0)).size());
    Assertions.assertTrue(findSupersets(st, inputSet, deadElts, testSet.get(0)).contains(inputSet.get(0)));

    Assertions.assertEquals(0, findSupersets(st, inputSet, deadElts, testSet.get(1)).size());
    Assertions.assertEquals(4, findSupersets(st, inputSet, deadElts, testSet.get(2)).size());
    Assertions.assertTrue(findSupersets(st, inputSet, deadElts, testSet.get(2)).contains(inputSet.get(2)));
    Assertions.assertTrue(findSupersets(st, inputSet, deadElts, testSet.get(2)).contains(inputSet.get(3)));
    Assertions.assertTrue(findSupersets(st, inputSet, deadElts, testSet.get(2)).contains(inputSet.get(5)));
    Assertions.assertTrue(findSupersets(st, inputSet, deadElts, testSet.get(2)).contains(inputSet.get(6)));

    Assertions.assertEquals(0, findSupersets(st, inputSet, deadElts, testSet.get(3)).size());
    Assertions.assertEquals(0, findSupersets(st, inputSet, deadElts, testSet.get(4)).size());
    Assertions.assertEquals(0, findSupersets(st, inputSet, deadElts, testSet.get(5)).size());

    Assertions.assertEquals(1, findSupersets(st, inputSet, deadElts, testSet.get(6)).size());
    Assertions.assertTrue(findSupersets(st, inputSet, deadElts, testSet.get(6)).contains(inputSet.get(7)));

    Assertions.assertEquals(2, findSupersets(st, inputSet, deadElts, testSet.get(7)).size());
    Assertions.assertTrue(findSupersets(st, inputSet, deadElts, testSet.get(7)).contains(inputSet.get(2)));

    Assertions.assertEquals(1, findSupersets(st, inputSet, deadElts, testSet.get(8)).size());
    Assertions.assertTrue(findSupersets(st, inputSet, deadElts, testSet.get(8)).contains(inputSet.get(7)));
  }

  @Test
  void testString() {
    List<SmartBitSet> inputSet = inputSet();
    InvertedIndex st = new InvertedIndex(10, inputSet);
    Assertions.assertFalse(st.toString().isEmpty());
  }

  private static List<SmartBitSet> findSupersets(InvertedIndex st, List<SmartBitSet> acElts, SmartBitSet deadElts, SmartBitSet b) {
    SmartBitSet supersetIndices = st.findSupersetIndices(acElts, deadElts, b, false);
    List<SmartBitSet> supersets = new ArrayList<>();
    for (int k = supersetIndices.nextSetBit(0); k >= 0; k = supersetIndices.nextSetBit(k + 1)) {
      supersets.add(acElts.get(k));
    }
    return supersets;
  }

  static List<SmartBitSet> inputSet() {
    List<SmartBitSet> sets = new ArrayList<>();
    sets.add(convertListToSmartBitSet(Arrays.asList(0, 1, 2)));
    sets.add(convertListToSmartBitSet(Arrays.asList(0, 4, 5)));
    sets.add(convertListToSmartBitSet(Arrays.asList(1, 2, 3, 4, 5)));
    sets.add(convertListToSmartBitSet(Arrays.asList(2, 3, 5, 6)));
    sets.add(convertListToSmartBitSet(Arrays.asList(2, 4, 5, 7)));
    sets.add(convertListToSmartBitSet(Arrays.asList(3, 4, 5, 6)));
    sets.add(convertListToSmartBitSet(Arrays.asList(3, 5, 6, 7)));
    sets.add(convertListToSmartBitSet(Arrays.asList(4, 5, 6, 7, 8, 9)));
    sets.add(convertListToSmartBitSet(Arrays.asList(4, 6, 8, 9)));
    sets.add(convertListToSmartBitSet(Arrays.asList(5, 6, 7)));
    sets.add(convertListToSmartBitSet(Arrays.asList(5, 6, 8, 9)));
    sets.add(convertListToSmartBitSet(Arrays.asList(6, 7, 8, 9)));
    return sets;
  }

  static List<SmartBitSet> testSet() {
    List<SmartBitSet> sets = new ArrayList<>();
    sets.add(convertListToSmartBitSet(Arrays.asList(0, 1)));
    sets.add(convertListToSmartBitSet(Arrays.asList(0, 1, 2, 4)));
    sets.add(convertListToSmartBitSet(Arrays.asList(3, 5)));
    sets.add(convertListToSmartBitSet(Arrays.asList(3, 5, 6, 8, 9)));
    sets.add(convertListToSmartBitSet(Arrays.asList(1, 3, 8)));
    sets.add(convertListToSmartBitSet(Arrays.asList(3, 5, 8)));
    sets.add(convertListToSmartBitSet(Arrays.asList(4, 5, 6, 7, 8, 9)));
    sets.add(convertListToSmartBitSet(List.of(1)));
    sets.add(convertListToSmartBitSet(Arrays.asList(5, 6, 7, 8)));
    return sets;
  }

}