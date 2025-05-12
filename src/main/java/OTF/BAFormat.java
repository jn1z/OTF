package OTF;

import net.automatalib.alphabet.impl.Alphabets;
import net.automatalib.automaton.fsa.impl.CompactNFA;
import net.automatalib.exception.FormatException;
import net.automatalib.serialization.ba.BAParsers;

import java.io.*;
import java.util.*;

public class BAFormat {
    /*
    We just use code from Automatalib and convert from a CompactNFA<String>
     */
    public static CompactNFA<Integer> convertBAFromCompactNFA(InputStream is) throws IOException, FormatException {
        final CompactNFA<String> automaton = BAParsers.nfa().readModel(is).model;
        int states = automaton.getStates().size();
        int aAlphSize = automaton.getInputAlphabet().size();
        CompactNFA<Integer> tv = new CompactNFA<>(Alphabets.integers(0,aAlphSize-1), states);
        Set<Integer> initialStates = automaton.getInitialStates();
        for(int i=0;i<states;i++) {
            tv.addState(automaton.isAccepting(i));
            if (initialStates.contains(i)) {
                tv.setInitial(i, true);
            }
        }
        for(int i=0;i<states;i++) {
            for(int a=0;a<aAlphSize;a++) {
                tv.addTransitions(i,a,automaton.getTransitions(i,automaton.getInputAlphabet().getSymbol(a)));
            }
        }
        return tv;
    }

    static CompactNFA<Integer> getBAFile(String filePath) {
        try (InputStream is = new FileInputStream(filePath)) {
            return convertBAFromCompactNFA(is);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
