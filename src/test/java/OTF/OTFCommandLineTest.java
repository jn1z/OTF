package OTF;

import net.automatalib.alphabet.impl.Alphabets;
import net.automatalib.automaton.fsa.impl.CompactDFA;
import net.automatalib.automaton.fsa.impl.CompactNFA;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class OTFCommandLineTest {
  private static final List<String> ALL_ALGS =
      List.of("sc", "scs", "brz", "brzs", "ccl", "ccls", "brz-ccl", "brz-ccls");
  @Test
  void testTrivialAutomata() {
    CompactNFA<Integer> nfa;
    CompactDFA<Integer> dfa;
    for(String alg: ALL_ALGS) {
      // all-accepting NFA
      nfa = new CompactNFA<>(Alphabets.integers(0,0), 1);
      nfa.addState(true);
      nfa.setInitial(0, true);
      nfa.addTransition(0,0,0);
      dfa = OTFCommandLine.allAlgorithms(alg, nfa);
      Assertions.assertEquals(1, dfa.size());

      // non-total NFA
      nfa = new CompactNFA<>(Alphabets.integers(0,0), 1);
      nfa.addState(true);
      nfa.setInitial(0, true);
      dfa = OTFCommandLine.allAlgorithms(alg, nfa);
      Assertions.assertEquals(2, dfa.size());
    }
  }
}
