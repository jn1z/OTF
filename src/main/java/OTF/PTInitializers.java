package OTF;

/* Copyright (C) 2013-2024 TU Dortmund University
 * This file is part of AutomataLib, http://www.automatalib.net/.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.BitSet;

import net.automatalib.automaton.fsa.impl.CompactDFA;
import net.automatalib.util.partitionrefinement.Block;
import net.automatalib.util.partitionrefinement.Hopcroft;
/**
 * This class provides several methods to initialize a {@link Hopcroft} partition refinement data structure from
 * common sources, e.g., automata.
 * */
public class PTInitializers {
    private static final int ARTIFICIAL_SINK_CLASS = 2;
    public static final int DFA_COMPONENT_CLASS = 3;

    private PTInitializers() {}

    private static void prefixSum(int[] array, int startInclusive, int endExclusive) {
        for (int i = startInclusive + 1; i < endExclusive; i++) {
            array[i] += array[i- 1];
        }
    }

    /**
     * Initializes the partition refinement data structure from a given deterministic automaton, initializing the
     * initial partition according to the given classification function.
     * Classifications:
     *      *   0/1 are boolean reject/accept
     *      *   2 is artificial sink state
     *      *   (3,...) are partial DFA states
     * <p>
     * This method can be used for automata with partially defined transition functions.
     *
     * @param pt
     *         the partition refinement data structure
     * @param dfa
     *         the input automaton
     */
    public static <I> void initDeterministic(Hopcroft pt,
                                             CompactDFA<I> dfa,
                                             BitSet finishedStates) {
        final int numStates = dfa.size();
        int numInputs = dfa.numInputs();

        int sinkId = numStates;
        int numStatesWithSink = numStates + 1;
        int posDataLow = numStatesWithSink;
        int predOfsDataLow = posDataLow + numStatesWithSink;
        int numTransitionsFull = numStatesWithSink * numInputs;
        int predDataLow = predOfsDataLow + numTransitionsFull + 1;

        // Usually PT combines everything into one array of size 2*(n + n*a)
        // This can lead to overflow for large automata and alphabets
        // We split out a second array of size n*a for the predData
        // This makes for a maximum size 2*(n + n*a) - n*a = 2*n + n*a
        int[] data = new int[predDataLow];
        int[] predData = new int[numTransitionsFull];
        Block[] blockForState = new Block[numStatesWithSink];

        Block[] blockClassification = new Block[DFA_COMPONENT_CLASS + numStates];

        int[] classifyArr = determineClassifyArr(dfa, finishedStates, numStates);

        int initId = dfa.getIntInitialState();
        int initClass = classifyArr[initId];

        pt.createBlock();
        blockForState[initId] = getOrCreateBlock(blockClassification, initClass, pt);

        int[] statesBuff = new int[numStatesWithSink];
        statesBuff[0] = initId;


        int reachableStates = computeReachable(pt, dfa, classifyArr, statesBuff, sinkId,
                predOfsDataLow, numInputs, blockForState, blockClassification, data, numStatesWithSink);

        // data[predOfsDataLow + j*numStatesWithSink+i] now contains the count of transitions to state i from input j

        pt.canonizeBlocks();

        // Make predOfsData cumulative
        prefixSum(data, predOfsDataLow, predDataLow);

        // data[predOfsDataLow + j*numStatesWithSink+i] now contains
        // the final predOfsData value plus the count of transitions to state i from input j

        updateBlocks(dfa, reachableStates, statesBuff, blockForState, data,
                posDataLow, predOfsDataLow, numInputs, sinkId, predData, numStatesWithSink);

        pt.setBlockData(data);
        pt.setPosData(data, posDataLow);
        pt.setPredOfsData(data, predOfsDataLow);
        pt.setPredData(predData);
        pt.setBlockForState(blockForState);
        pt.setSize(numStatesWithSink, numInputs);

        pt.removeEmptyBlocks();
    }

    private static <I> int[] determineClassifyArr(CompactDFA<I> dfa, BitSet f, int numStates) {
        int[] classifyArr = new int[numStates];
        for (int q = f.nextClearBit(0); q >= 0 && q < numStates; q = f.nextClearBit(q + 1)) {
            classifyArr[q] = DFA_COMPONENT_CLASS + q; // unfinished states
        }
        // All that remains are accepting states.
        for (int q = f.nextSetBit(0); q >= 0; q = f.nextSetBit(q + 1)) {
            if (dfa.isAccepting(q)) {
                classifyArr[q] = 1;
            }
        }
        return classifyArr;
    }

    private static <I> void updateBlocks(
            CompactDFA<I> dfa, int reachableStates, int[] statesBuff, Block[] blockForState, int[] data,
            int posDataLow, int predOfsDataLow, int numInputs, int sinkId, int[] predData, int numStatesWithSink) {
        for (int i = 0; i < reachableStates; i++) {
            final int stateId = statesBuff[i];
            final Block b = blockForState[stateId];
            final int pos = --b.low;
            data[pos] = stateId;
            data[posDataLow + stateId] = pos;

            int predOfsBase = predOfsDataLow;

            for (int j = 0; j < numInputs; j++) {
                final int succId;
                if (stateId == sinkId) { // state is new artificial sink
                    succId = sinkId;
                } else {
                    final int succ = dfa.getSuccessor(stateId, j);
                    if (succ < 0) {
                        succId = sinkId;
                    } else {
                        succId = succ;
                    }
                }

                predData[--data[predOfsBase + succId]] = stateId; // decrement predOfsData, set predData
                predOfsBase += numStatesWithSink;
            }
        }
    }

    private static <I> int computeReachable(
        Hopcroft pt, CompactDFA<I> dfa, int[] classifyArr, int[] statesBuff, int sinkId, int predOfsDataLow,
        int numInputs, Block[] blockForState, Block[] blockClassification,
        int[] data, int numStatesWithSink) {
        int statesPtr = 0;
        int reachableStates = 1;

        boolean partial = false;
        while (statesPtr < reachableStates) {
            int currId = statesBuff[statesPtr++];
            if (currId == sinkId) {
                continue;
            }

            int predCountBase = predOfsDataLow;

            for (int i = 0; i < numInputs; i++) {
                int succ = dfa.getSuccessor(currId, i);
                int succId;
                if (succ < 0) {
                    succId = sinkId;
                    partial = true;
                } else {
                    succId = succ;
                }
                final Block succBlock = blockForState[succId];
                if (succBlock == null) {
                    final int succClass;
                    if (succ < 0) {
                        succClass = ARTIFICIAL_SINK_CLASS;
                    } else {
                        succClass = classifyArr[succ];
                    }
                    // unroll getOrCreateBlock in inner loop
                    Block block = blockClassification[succClass];
                    if (block == null) {
                        block = pt.createBlock();
                        block.high = 1;
                        blockClassification[succClass] = block;
                    } else {
                        block.high++;
                    }
                    blockForState[succId] = block;
                    statesBuff[reachableStates++] = succId;
                }
                data[predCountBase + succId]++; // predOfsData
                predCountBase += numStatesWithSink;
            }
        }

        if (partial) {
            int predCountIdx = predOfsDataLow + sinkId;
            for (int i = 0; i < numInputs; i++) {
                data[predCountIdx]++; // predOfsData - sink state has all its symbols pointing to itself
                predCountIdx += numStatesWithSink;
            }
        }
        return reachableStates;
    }

    private static Block getOrCreateBlock(Block[] blockClassification, int classification, Hopcroft pt) {
        Block block = blockClassification[classification];
        if (block == null) {
            block = pt.createBlock();
            block.high = 1;
            blockClassification[classification] = block;
        } else {
            block.high++;
        }
        return block;
    }
}
