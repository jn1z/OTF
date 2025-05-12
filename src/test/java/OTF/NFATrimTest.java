package OTF;

import net.automatalib.alphabet.impl.Alphabets;
import net.automatalib.automaton.fsa.impl.CompactDFA;
import net.automatalib.automaton.fsa.impl.CompactNFA;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

public class NFATrimTest {
  @Test
  void tempEmpty() {
    Assertions.assertNotNull(new NFATrim()); // trivial exercise of constructor

    CompactNFA<Integer> myNFA = new CompactNFA<>(Alphabets.integers(0,1));
    Assertions.assertEquals(0, myNFA.size());
    CompactNFA<Integer> myNFA2 = NFATrim.trim(myNFA);
    Assertions.assertEquals(0, myNFA2.size());

    myNFA2 = NFATrim.reverse(myNFA2);
    Assertions.assertEquals(0, myNFA2.size());

    myNFA2 = NFATrim.bisim(myNFA2);
    Assertions.assertEquals(0, myNFA2.size());

    CompactDFA<Integer> myDFA = new CompactDFA<>(Alphabets.integers(0,1));
    myNFA2 = NFATrim.reverse(myDFA, myDFA.getInputAlphabet());
    Assertions.assertEquals(0, myNFA2.size());
  }

  @Test
  void testSmallTrim() {
    CompactNFA<Integer> myNFA = new CompactNFA<>(Alphabets.integers(0,1));
    for(int i=0;i<3;i++) { myNFA.addState(true); }
    myNFA.addTransition(0,0, 1);
    Assertions.assertEquals(3, myNFA.size());

    CompactNFA<Integer> myNFA2 = NFATrim.trim(myNFA);
    Assertions.assertEquals(0, myNFA2.size()); // no initial states

    myNFA.setInitial(0, true);
    myNFA2 = NFATrim.trim(myNFA);
    Assertions.assertEquals(2, myNFA2.size()); // state 2 isn't reachable

    myNFA2 = NFATrim.reverse(myNFA);
    myNFA2 = NFATrim.trim(myNFA2);
    Assertions.assertEquals(2, myNFA2.size()); // same behavior

    myNFA2 = NFATrim.bisim(myNFA2);
    Assertions.assertEquals(2, myNFA2.size()); // no difference
  }

  @Test
  void testReverseNFA() {
    CompactNFA<Integer> myNFA = new CompactNFA<>(Alphabets.integers(0,1));
    for(int i=0;i<3;i++) { myNFA.addState(true); }
    myNFA.addTransition(0,0, 1);
    Assertions.assertEquals(3, myNFA.size());

    CompactNFA<Integer> myNFA2 = NFATrim.reverse(myNFA);
    Assertions.assertEquals(3, myNFA2.size()); // hasn't been reduced
    // initial and final states flip
    for(int i=0;i<3;i++) {
      Assertions.assertFalse(myNFA2.isAccepting(i));
      Assertions.assertTrue(myNFA2.getInitialStates().contains(i));
    }

    myNFA2 = NFATrim.reverse(myNFA2); // reverse of reverse
    Assertions.assertEquals(3, myNFA2.size()); // hasn't been reduced
    Assertions.assertEquals(1,myNFA2.getTransitions(0).size());
    Assertions.assertTrue(myNFA2.getTransitions(0).contains(1));
  }

  /*@Test
  void testReverseDFA() {
    CompactDFA<Integer> myDFA = new CompactDFA<>(Alphabets.integers(0,1));
    for(int i=0;i<3;i++) { myDFA.addState(true); }
    myDFA.addTransition(0,0, 1);
    Assertions.assertEquals(3, myDFA.size());

    CompactNFA<Integer> myNFA2 = NFATrim.reverse(myDFA);
    Assertions.assertEquals(3, myNFA2.size()); // hasn't been reduced
    // initial and final states flip
    for(int i=0;i<3;i++) {
      Assertions.assertFalse(myNFA2.isAccepting(i));
      Assertions.assertTrue(myNFA2.getInitialStates().contains(i));
    }

    myNFA2 = NFATrim.reverse(myNFA2); // reverse of reverse
    Assertions.assertEquals(3, myNFA2.size()); // hasn't been reduced
    Assertions.assertEquals(1,myNFA2.getTransitions(0).size());
    Assertions.assertTrue(myNFA2.getTransitions(0).contains(1));
  }*/
}
