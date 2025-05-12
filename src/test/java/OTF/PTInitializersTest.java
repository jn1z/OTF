package OTF;

import net.automatalib.alphabet.impl.Alphabets;
import net.automatalib.automaton.fsa.impl.CompactDFA;
import net.automatalib.util.partitionrefinement.Hopcroft;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

class PTInitializersTest {
  @Test
  void testSmallest() {
    Hopcroft pt = new Hopcroft();
    CompactDFA<Integer> myDFA = new CompactDFA<>(Alphabets.integers(0,1));
    myDFA.addState(true);
    myDFA.setInitial(0, true);

    BitSet finishedStates = BitSetUtils.convertListToBitSet(List.of(0,2));
    PTInitializers.initDeterministic(pt, myDFA, finishedStates);

    Assertions.assertEquals(2, pt.numStates); // sink state added
    Assertions.assertEquals(2, pt.numInputs);

    // inputs 0 and 1 have no predecessors for state 0, but each have 1 predecessor for state 1.
    int[] testPredData = new int[]{1,0,1,0};
    Assertions.assertArrayEquals(testPredData, pt.predData);
  }
}
