package OTF.Registry;

import OTF.BitSetUtils;
import OTF.Compress.AntichainForest2;
import OTF.Compress.AntichainForest5;
import OTF.Compress.AntichainForest5Idx;
import net.automatalib.alphabet.impl.Alphabets;
import net.automatalib.automaton.fsa.impl.CompactNFA;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class RegistryTest {
  @Test
  void testNoOpRegistry() {
    NoOpRegistry noOpRegistry = new NoOpRegistry();
    Assertions.assertEquals(0, noOpRegistry.get(new BitSet()));
    noOpRegistry.put(new BitSet(), 1);
    noOpRegistry.unify(0, 1);
    Assertions.assertEquals("NoOp", noOpRegistry.toString());

    // Also test default Registry values
    noOpRegistry.unify(0, BitSetUtils.convertListToBitSet(List.of(1,2,3)));
    noOpRegistry.compress();
    Assertions.assertEquals(-1, noOpRegistry.getMaxIntermediateCount());
  }

  @Test
  void testAddressRegistry() {
    AddressRegistry addressRegistry = new AddressRegistry();
    Assertions.assertEquals(Registry.MISSING_ELEMENT, addressRegistry.get(new BitSet()));
    BitSet b = BitSetUtils.convertListToBitSet(List.of(1,2,3));
    addressRegistry.put(b, 1);
    Assertions.assertEquals(1, addressRegistry.get(b));

    b = BitSetUtils.convertListToBitSet(List.of(1));
    addressRegistry.put(b, 0);
    Assertions.assertEquals(0, addressRegistry.get(b));

    addressRegistry.unify(0, 1);
    Assertions.assertEquals("OTF1", addressRegistry.toString());
  }

  @Test
  void testAntichainForestRegistryCCL() {
    CompactNFA<Integer> myNfa = new CompactNFA<>(Alphabets.integers(0,1));
    for(int i=0;i<4;i++) {
      myNfa.addState();
    }
    AntichainForestRegistry<Integer> acfRegistry = new AntichainForestRegistry<>(myNfa);
    Assertions.assertEquals(0, acfRegistry.getMaxIntermediateCount());

    Assertions.assertEquals(Registry.MISSING_ELEMENT, acfRegistry.get(new BitSet()));
    BitSet b1 = BitSetUtils.convertListToBitSet(List.of(1,2,3));
    acfRegistry.put(b1, 1);
    Assertions.assertEquals(1, acfRegistry.get(b1));
    Assertions.assertEquals(1, acfRegistry.getMaxIntermediateCount());

    BitSet b2 = BitSetUtils.convertListToBitSet(List.of(1));
    acfRegistry.put(b2, 0);
    Assertions.assertEquals(0, acfRegistry.get(b2));
    Assertions.assertEquals(2, acfRegistry.getMaxIntermediateCount());

    assertThrows(RuntimeException.class, () -> acfRegistry.unify(0,1));

    BitSet secondaries = BitSetUtils.convertListToBitSet(List.of(1));
    acfRegistry.unify(0,secondaries);
    // both are unified to ID 0
    Assertions.assertEquals(0, acfRegistry.get(b1));
    Assertions.assertEquals(0, acfRegistry.get(b2));

    Assertions.assertEquals("CCL", acfRegistry.toString());

    acfRegistry.compress();
  }

  @Test
  void testAntichainForestRegistryCCLS() {
    CompactNFA<Integer> myNfa = new CompactNFA<>(Alphabets.integers(0,1));
    for(int i=0;i<4;i++) {
      myNfa.addState();
    }
    AntichainForestRegistry<Integer> acfRegistry =
        new AntichainForestRegistry<>(myNfa, new BitSet[]{null,null,null,null});
    Assertions.assertEquals("CCLS", acfRegistry.toString());
  }

  @Test
  void testAntichainForest2() {
    CompactNFA<Integer> myNfa = new CompactNFA<>(Alphabets.integers(0,1));
    for(int i=0;i<4;i++) {
      myNfa.addState();
    }
    Registry acfRegistry = new AntichainForest2();
    Assertions.assertEquals("OTF1.5", acfRegistry.toString());
    Assertions.assertEquals(0, acfRegistry.getMaxIntermediateCount());

    Assertions.assertEquals(Registry.MISSING_ELEMENT, acfRegistry.get(new BitSet()));
    BitSet b1 = BitSetUtils.convertListToBitSet(List.of(1,2,3));
    acfRegistry.put(b1, 1);
    Assertions.assertEquals(1, acfRegistry.get(b1));
    Assertions.assertEquals(1, acfRegistry.getMaxIntermediateCount());

    BitSet b2 = BitSetUtils.convertListToBitSet(List.of(1));
    acfRegistry.put(b2, 0);
    Assertions.assertEquals(0, acfRegistry.get(b2));
    Assertions.assertEquals(2, acfRegistry.getMaxIntermediateCount());

    acfRegistry.unify(0,1);

    Assertions.assertEquals(0, acfRegistry.get(b1));
    Assertions.assertEquals(0, acfRegistry.get(b2));

    acfRegistry.compress();
  }

  @Test
  void testAntichainForest5() {
    CompactNFA<Integer> myNfa = new CompactNFA<>(Alphabets.integers(0,1));
    for(int i=0;i<4;i++) {
      myNfa.addState();
    }
    AntichainForest5 acfRegistry = new AntichainForest5();
    Assertions.assertEquals("OTF5", acfRegistry.toString());
    Assertions.assertEquals(0, acfRegistry.getMaxIntermediateCount());

    Assertions.assertEquals(Registry.MISSING_ELEMENT, acfRegistry.get(new BitSet()));
    BitSet b1 = BitSetUtils.convertListToBitSet(List.of(1,2,3));
    acfRegistry.put(b1, 1);
    Assertions.assertEquals(1, acfRegistry.get(b1));
    Assertions.assertEquals(1, acfRegistry.getMaxIntermediateCount());

    BitSet b2 = BitSetUtils.convertListToBitSet(List.of(1));
    acfRegistry.put(b2, 0);
    Assertions.assertEquals(0, acfRegistry.get(b2));
    Assertions.assertEquals(2, acfRegistry.getMaxIntermediateCount());

    acfRegistry.unify(0,1);

    Assertions.assertEquals(0, acfRegistry.get(b1));
    Assertions.assertEquals(0, acfRegistry.get(b2));

    acfRegistry.compress();
  }

  @Test
  void testAntichainForest5Idx() {
    CompactNFA<Integer> myNfa = new CompactNFA<>(Alphabets.integers(0,1));
    for(int i=0;i<4;i++) {
      myNfa.addState();
    }
    AntichainForest5Idx acfRegistry = new AntichainForest5Idx(myNfa.size());
    Assertions.assertEquals("OTF5IDX", acfRegistry.toString());
    Assertions.assertEquals(0, acfRegistry.getMaxIntermediateCount());

    Assertions.assertEquals(Registry.MISSING_ELEMENT, acfRegistry.get(new BitSet()));
    BitSet b1 = BitSetUtils.convertListToBitSet(List.of(1,2,3));
    acfRegistry.put(b1, 1);
    Assertions.assertEquals(1, acfRegistry.get(b1));
    Assertions.assertEquals(1, acfRegistry.getMaxIntermediateCount());

    BitSet b2 = BitSetUtils.convertListToBitSet(List.of(1));
    acfRegistry.put(b2, 0);
    Assertions.assertEquals(0, acfRegistry.get(b2));
    Assertions.assertEquals(2, acfRegistry.getMaxIntermediateCount());

    acfRegistry.unify(0,1);

    Assertions.assertEquals(0, acfRegistry.get(b1));
    Assertions.assertEquals(0, acfRegistry.get(b2));

    acfRegistry.compress();
  }
}
