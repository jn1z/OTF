package OTF;

import java.util.*;

import OTF.Model.*;
import OTF.Registry.Registry;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.automaton.MutableDeterministic.FullIntAbstraction;
import net.automatalib.automaton.fsa.DFA;
import net.automatalib.automaton.fsa.impl.CompactDFA;
import net.automatalib.ts.AcceptorPowersetViewTS;
import net.automatalib.util.partitionrefinement.Block;
import net.automatalib.util.partitionrefinement.Hopcroft;

public class OTFDeterminization {
    public static boolean DEBUG = false;
    private static final long STATES_EXPLORED_PERIOD = 10000L;
    /**
     * Main OTF loop (Algorithm 1).
     * @param nfa - Original NFA
     * @param inputs - Input symbols
     * @param threshold - Threshold strategy for interrupts
     * @param registry - Registry, e.g., OTF-CCL
     * @return - (Partially) minimized DFA; output of Algorithm 1.
     * @param <I> - Input symbol type, e.g., Integer
     */
    public static <I> DFA<Integer, I> doOTF(
        AcceptorPowersetViewTS<BitSet, I, ?> nfa, Alphabet<I> inputs, Threshold threshold, Registry registry) {

        Deque<DeterminizeRecord<BitSet>> stack = new ArrayDeque<>();

        // Add union of initial states to DFA and to stack
        BitSet init = nfa.getInitialState();
        boolean initAcc = nfa.isAccepting(init);
        CompactDFA<I> out = new CompactDFA<>(inputs);
        int initOut = out.addInitialState(initAcc);

        registry.put(init, initOut);

        stack.push(new DeterminizeRecord<>(init, initOut));
        BitSet finishedStates = new BitSet();
        Deque<Integer> stateBuffer = new ArrayDeque<>();

        long statesExplored = 0;
        while (!stack.isEmpty()) {
            DeterminizeRecord<BitSet> curr = stack.pop();
            BitSet inState = curr.inputState();
            int outState = curr.outputAddress();
            boolean complete = true;
            for (I i : inputs) {
                BitSet succ = nfa.getSuccessor(inState, i);
                int outSucc = registry.get(succ);
                if (outSucc == Registry.MISSING_ELEMENT) {
                    complete = false;
                    final boolean succAcc = nfa.isAccepting(succ);
                    // add new state to DFA and to stack
                    if (stateBuffer.isEmpty()) {
                        outSucc = out.addState(succAcc);
                    } else {
                        outSucc = stateBuffer.pop();
                        out.setAccepting(outSucc, succAcc);
                    }
                    registry.put(succ, outSucc);
                    stack.push(new DeterminizeRecord<>(succ, outSucc));
                }
                out.setTransition(outState, inputs.getSymbolIndex(i), outSucc);
            }
            statesExplored++;

            finishedStates.set(outState);

            if (complete && threshold.test(out)) {
                final int oldStatesSoFar = DEBUG ? (out.size() - stateBuffer.size()) : 0;
                otfMinimization(inputs, out, finishedStates, stateBuffer, registry);
                final int statesSoFar = out.size() - stateBuffer.size();
                threshold.update(statesSoFar);
                if (DEBUG) {
                    System.out.println("DEBUG: Periodic minimization: " + oldStatesSoFar + " -> " + statesSoFar + " states added");
                }
            }
            if (DEBUG && statesExplored % STATES_EXPLORED_PERIOD == 0) {
                System.out.println("DEBUG: Explored " + statesExplored + " states - "
                + stack.size() + " states left in queue - " + (out.size() - stateBuffer.size()) + " states added");
            }
        }

        return out;
    }


    /**
     * Corresponds to the On-the-fly minimization section in Algorithm 1.
     * @param inputs - Input symbols
     * @param out - Output DFA
     * @param finishedStates - States that are finished with exploration
     * @param stateBuffer - Buffer for state re-use
     * @param registry - OTF registry
     * @param <I> - Input symbol type, e.g., Integer
     */
    public static <I> void otfMinimization(
        Alphabet<I> inputs, CompactDFA<I> out, BitSet finishedStates, Deque<Integer> stateBuffer, Registry registry) {
        final Hopcroft pt = new Hopcroft();
        final FullIntAbstraction<?, Boolean, Void> abs = out.fullIntAbstraction(inputs);

        PTInitializers.initDeterministic(pt, out, finishedStates);

        pt.computeCoarsestStablePartition();

        if (updateDFA(out, finishedStates, stateBuffer, registry, pt, abs)) {
            registry.compress();
        }
    }

    /**
     * Update DFA with minimized states (if applicable).
     * @param out - Output DFA
     * @param finishedStates - States that are finished with exploration
     * @param stateBuffer - Buffer for state re-use
     * @param registry - OTF registry
     * @param pt - data from Paige-Tarjan algorithm
     * @param abs - integer abstraction of the output DFA
     * @return if any updates occurred
     * @param <I> - Input symbol type, e.g., Integer
     */
    private static <I> boolean updateDFA(
        CompactDFA<I> out, BitSet finishedStates, Deque<Integer> stateBuffer,
        Registry registry, Hopcroft pt, FullIntAbstraction<?, Boolean, Void> abs) {
        // cache merged states so that we don't update incoming transitions of already merged states
        BitSet mergedStates = new BitSet();
        BitSet secondaries = new BitSet();

        boolean updated = false;

        int symbolNum = out.getInputAlphabet().size();

        // Each block is an equivalence class of states
        for (Block block : pt.blockList()) {
            int blockLowRep = pt.blockData[block.low]; // representative state, also the low block value

            if (blockLowRep >= abs.size()) {
                continue; // ignore artificial sink block
            }

            // Merge other states of block into representative state
            for (int b = block.low + 1; b < block.high; b++) {
                int equivState = pt.blockData[b];
                mergedStates.set(equivState);
                secondaries.set(equivState);
                int equivOffset = pt.predOfsDataLow + equivState;
                for (int j = 0; j < symbolNum; j++) {
                    for (int idx = pt.predOfsData[equivOffset]; idx < pt.predOfsData[equivOffset + 1]; idx++) {
                        // redirect old incoming transitions to new representative
                        int predState = pt.predData[idx]; // predecessor is an incoming state
                        if (!mergedStates.get(predState)) { // only need to redirect predecessor if not merged yet
                            out.setTransition(predState, j, blockLowRep);
                        }
                    }
                    // dead state; make transitions loop in order to not deal with partial automata
                    out.setTransition(equivState, j, equivState);
                    equivOffset += pt.numStates;
                }

                if (abs.getIntInitialState() == equivState) {
                    abs.setInitialState(blockLowRep); // Replace initial state with its representative
                }

                // clean up merged state
                stateBuffer.push(equivState); // mark for re-use
                finishedStates.clear(equivState); // remove from DFA component

                updated = true;
            }
            if (!secondaries.isEmpty()) {
                // Equivalent states were found. Unify into primary state (blockLowRep)
                registry.unify(blockLowRep, secondaries);
                secondaries.clear();
            }
        }
        return updated;
    }

}
