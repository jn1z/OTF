package OTF.Compress;

import OTF.BitSetUtils;
import OTF.Registry.Registry;
import OTF.SmartBitSet;
import net.automatalib.alphabet.impl.Alphabets;
import net.automatalib.automaton.fsa.impl.CompactNFA;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class AntichainForestTest {
    @Test
    void testEmpty() {
      BitSet[] simSupersets = new BitSet[0];
      CompactNFA<Integer> CompactNFA = new CompactNFA<>(Alphabets.integers(0, 1));
      AntichainForest acf = new AntichainForest(CompactNFA, simSupersets);
      Assertions.assertNotNull(acf.toString());
      Assertions.assertEquals(0, acf.size());
      Assertions.assertEquals(SmartBitSet.EMPTY_SMART_BITSET, acf.acG.simAccelerate.pruneEltWithSims(SmartBitSet.EMPTY_SMART_BITSET));
      Assertions.assertEquals(SmartBitSet.EMPTY_SMART_BITSET, acf.acG.simAccelerate.saturateEltWithSims(SmartBitSet.EMPTY_SMART_BITSET));
      acf.compress();
      Assertions.assertEquals(Registry.MISSING_ELEMENT, acf.get(new BitSet()));
    }

    @Test
    void testPruneSaturate() {
      BitSet[] simSupersets = new BitSet[4];
      simSupersets[0] = BitSetUtils.convertListToBitSet(List.of(1));
      simSupersets[2] = BitSetUtils.convertListToBitSet(List.of(3,4));
      CompactNFA<Integer> CompactNFA = new CompactNFA<>(Alphabets.integers(0, 1));
      for(int i=0;i<5;i++) {
        CompactNFA.addState(true);
      }
      AntichainForest acf = new AntichainForest(CompactNFA, simSupersets);
      SmartBitSet sbsSmall = BitSetUtils.convertListToSmartBitSet(List.of(0));
      SmartBitSet sbsFull = BitSetUtils.convertListToSmartBitSet(List.of(0,1));
      SmartBitSet sbsUnrelated = BitSetUtils.convertListToSmartBitSet(List.of(3,4,5));
      SmartBitSet sbsSmall2 = BitSetUtils.convertListToSmartBitSet(List.of(2));
      SmartBitSet sbsFull2 = BitSetUtils.convertListToSmartBitSet(List.of(2,3,4));
      SmartBitSet sbsAllSuperSets = BitSetUtils.convertListToSmartBitSet(List.of(0,2));


      Assertions.assertEquals(sbsSmall, acf.acG.simAccelerate.pruneEltWithSims(sbsSmall));
      acf = new AntichainForest(CompactNFA, simSupersets);
      Assertions.assertEquals(sbsSmall, acf.acG.simAccelerate.pruneEltWithSims(sbsFull));
      Assertions.assertEquals(sbsSmall, acf.acG.simAccelerate.pruneEltWithSims(sbsFull)); // test caching
      Assertions.assertEquals(sbsSmall2, acf.acG.simAccelerate.pruneEltWithSims(sbsFull2));
      Assertions.assertEquals(sbsUnrelated, acf.acG.simAccelerate.pruneEltWithSims(sbsUnrelated));
      Assertions.assertEquals(sbsAllSuperSets, acf.acG.simAccelerate.pruneEltWithSims(sbsAllSuperSets));

      Assertions.assertEquals(sbsFull, acf.acG.simAccelerate.saturateEltWithSims(sbsSmall));
      Assertions.assertEquals(sbsFull, acf.acG.simAccelerate.saturateEltWithSims(sbsFull));
      Assertions.assertEquals(sbsFull2, acf.acG.simAccelerate.saturateEltWithSims(sbsSmall2));
      Assertions.assertEquals(sbsUnrelated, acf.acG.simAccelerate.saturateEltWithSims(sbsUnrelated));
    }

    @Test
    void testNullCase() {
      ACGlobals acGlobals = new ACGlobals(10, new BitSet[0]);
      Assertions.assertNull(
          acGlobals.getACPlus(0, SmartBitSet.EMPTY_SMART_BITSET));

      assertThrows(RuntimeException.class,
          ()->acGlobals.getACPlus(0, null));
    }

    @Test
    void testLifecycle() {
      BitSet[] simSupersets = new BitSet[7];
      CompactNFA<Integer> CompactNFA = new CompactNFA<>(Alphabets.integers(0, 1));
      for (int i = 0; i < 7; i++) {
        CompactNFA.addState(true);
      }
      AntichainForest acf = new AntichainForest(CompactNFA, simSupersets);
      BitSet origChain = BitSetUtils.convertListToBitSet(List.of(1, 2));
      Assertions.assertEquals(AntichainForest.MISSING_ELEMENT, acf.get(origChain));
      Assertions.assertEquals(0, acf.size()); // nothing found yet
      Assertions.assertTrue(acf.acG.foundSets.isEmpty());

      acf.put(origChain, 0);
      Assertions.assertEquals(1, acf.size()); // 1 1-element equivalences {1,2}
      Assertions.assertTrue(acf.acG.foundSets.isEmpty());

      Assertions.assertEquals(0, acf.get(origChain));
      Assertions.assertEquals(Registry.MISSING_ELEMENT, acf.find(SmartBitSet.valueOf(origChain.toLongArray())));
      Assertions.assertEquals(1, acf.size()); // 1 1-element equivalences {1,2}
      Assertions.assertTrue(acf.acG.foundSets.isEmpty());

      BitSet origChain2 = BitSetUtils.convertListToBitSet(List.of(1, 3));
      acf.put(origChain2, 1);
      Assertions.assertEquals(2, acf.size()); // 2 1-element equivalences: {1,2}, {1,3}
      Assertions.assertTrue(acf.acG.foundSets.isEmpty());

      BitSet secondaries = BitSetUtils.convertListToBitSet(List.of(1));

      acf.unify(0, secondaries);

      Assertions.assertEquals(0, acf.get(origChain2));
      Assertions.assertEquals(2, acf.size()); // {{1,2},{1,3} -> {1,2,3}} : {1,2} and {1,3} are found
      Assertions.assertEquals(2, acf.acG.foundSets.size());

      // At this point, we have one antichain: {1,2},{1,3} -> {1,2,3}
      ACPlus acPlus = acf.acG.getACPlus(0, null);
      Assertions.assertNotNull(acPlus);
      Assertions.assertEquals(0, acPlus.getStateId());
      Assertions.assertEquals("NodeID0:{1,2},{1,3},:{1,2,3}",
          acPlus.toString().replace(" ", ""));
      Assertions.assertEquals(1, acf.acG.getAllACs().size());
      int acInt = acf.get(origChain);
      Assertions.assertEquals(0, acInt);

      acInt = acf.find(SmartBitSet.valueOf(origChain.toLongArray()));
      Assertions.assertEquals(Registry.MISSING_ELEMENT, acInt);
      // Note: this is missing! This is only because find() isn't ever called directly
      // get() is called directly, and it finds the element.

      //Assertions.assertNotNull(aPlus);
      Assertions.assertEquals(2, acf.size()); // 1 3-element equivalence, of which 2 elements are found.
      Assertions.assertEquals(2, acf.acG.foundSets.size());

      // We can find the union in the convex set!
      BitSet union = (BitSet) origChain.clone();
      union.or(origChain2); // {1,2,3}
      acf.compress();
      int unionPlus = acf.get(union);
      Assertions.assertEquals(Registry.MISSING_ELEMENT, unionPlus);
      // Note: this is missing! This is because the AC is so small that it's not searchable
      // and because the ACUnion has never been seen as an actual element.

      /*Assertions.assertEquals(1, acf.acG.searchableACsList.length);
      Assertions.assertNull(acf.acG.searchableACsList[0]);
      Assertions.assertEquals(0, acf.acG.searchableACsUnions.size());*/
    }
}
