package OTF.Simulation;
import java.util.*;

import OTF.NFATrim;
import it.unimi.dsi.fastutil.ints.*;
import net.automatalib.automaton.fsa.impl.CompactNFA;


public class ParallelSimulation {
	/**
	 * Compute backward and forward simulation relations; may also alter NFA.
	 * Alterations are to force one initial state, and at most two final states.
	 * This is to increase simulations detected, as per:
	 *   Lorenzo Clemente and Richard Mayr.
	 *   Efficient reduction of nondeterministic automata with application to language inclusion testing.
	 *   In: Logical Methods in Computer Science 15 (2019).
	 * Simulation is done in parallel for performance, although that's easy to change.
	 * @param nfa - Original NFA, may be altered.
	 * @param simRels - ArrayList of simulation relations:
	 *                 the i-th BitSet will represent all states that are subsets of state i.
	 * @param parallel - whether to calculate simulations in parallel (for performance)
	 * @return - Original/altered NFA, returned for convenience.
	 * @param <I> - Input symbol type, e.g., Integer
	 */
	public static <I> CompactNFA<I> fullyComputeRels(CompactNFA<I> nfa, ArrayList<BitSet> simRels, boolean parallel) {
		// doing this before reversal, thus effectively same as unifying final states in reversed DFA
		// may create one additional state
		unifyInitialStatesWithoutTrim(nfa);
		CompactNFA<I> newNFA = NFATrim.trim(nfa);

		// determine reverse bisimulation equalities
		// -----------------------------------------
		CompactNFA<I> reversedNFA = NFATrim.reverse(newNFA);
		// doing this before double reversal, thus effectively same as unifying final states in un-reversed NFA
		// may create one additional state
		unifyInitialStatesWithoutTrim(reversedNFA);
		Set<IntIntPair> relRev = NaiveSimulation.computeDirectSimulation(reversedNFA, true, parallel);
		// If any equalities are detected, quotient them out now. This avoids potential issues in reordering of states during reversal
		Set<IntIntPair> relEqRev = new HashSet<>();
		determineRelEqualities(relRev, relEqRev, new HashSet<>());
		if (!relEqRev.isEmpty()) {
			int[] origToRep = findRepresentatives(relEqRev, reversedNFA.size());
			int[] origToNew = renumberRepresentatives(origToRep);
			reversedNFA = getMinimizedNFA(reversedNFA, origToRep, origToNew);
		}

		// determine forward bisimulation equalities
		// -----------------------------------------
		newNFA = NFATrim.reverse(reversedNFA);
		Set<IntIntPair> rel = NaiveSimulation.computeDirectSimulation(newNFA, true, parallel);
		Set<IntIntPair> relEq = new HashSet<>();
		Set<IntIntPair> relNeq = new HashSet<>();
		determineRelEqualities(rel, relEq, relNeq);
		if (!relEq.isEmpty()) {
			int[] origToRep = findRepresentatives(relEq, newNFA.size());
			int[] origToNew = renumberRepresentatives(origToRep);
			relNeq = updateNeq(relNeq, origToRep, origToNew);
			newNFA = getMinimizedNFA(newNFA, origToRep, origToNew);
		}
		simSupersets(new HashSet<>(), relNeq, simRels, newNFA.size());
		return newNFA;
	}

	/**
	 * Determine all strict supersets, for use in get and put.
	 * Example: 1=>{37, 122, 134} indicates {1} is a superset of states {37}, {122}, and {134}.
	 */
	static int simSupersets(
			Set<IntIntPair> relEq, Set<IntIntPair> relNeq, ArrayList<BitSet> relSupers, int nfaSize) {
		relSupers.ensureCapacity(nfaSize);
		for(int i=0;i<nfaSize;i++) {
			relSupers.add(null);
		}
		int totalRelations = 0;
		for (IntIntPair relP : relNeq) {
			int left = relP.leftInt();
			int right = relP.rightInt();
			if (left < right || !relEq.contains(new IntIntImmutablePair(right, left))) {
				BitSet b = relSupers.get(right);
				if (b == null) {
					b = new BitSet();
					relSupers.set(right,b);
				}
				b.set(left);
				totalRelations++;
			}
		}
		return totalRelations;
	}

	public static void determineRelEqualities(
			Set<IntIntPair> rel, Set<IntIntPair> relEq, Set<IntIntPair> relNeq) {
		for (IntIntPair relP : rel) {
			int left = relP.leftInt();
			int right = relP.rightInt();
			if (left != right) {
				IntIntPair p = new IntIntImmutablePair(right, left);
				if (rel.contains(p)) {
					if (left < right) {
						// only need half of the equalities.
						relEq.add(relP);
					}
					continue;
				}
				relNeq.add(relP);
			}
		}
	}

	/**
	 * Remove equality relations from relNeq, since we've already reduced then.
	 * @param relNeq - original relNeq relations
	 * @param origToRep
	 * @param origToNew
	 * @return - new relNeq relations
	 */
	private static Set<IntIntPair> updateNeq(Set<IntIntPair> relNeq, int[] origToRep, int[] origToNew) {
		Set<IntIntPair> newRelNeq = new HashSet<>();
		for (IntIntPair rel: relNeq) {
			int first = rel.firstInt();
			int second = rel.secondInt();
			if (first != origToRep[first] || second != origToRep[second]) {
				// redundant relation
				continue;
			}
			newRelNeq.add(new IntIntImmutablePair(origToNew[first], origToNew[second]));
		}
		return newRelNeq;
	}

	/**
	 * Convert NFA to minimized NFA, based on representatives.
	 * @param nfa - original NFA
	 * @param origToRep - original states to their minimal representative.
	 * @param origToNew - renumbered states.
	 * @return - minimized NFA
	 * @param <I> - Input symbol type, e.g., Integer
	 */
	static <I> CompactNFA<I> getMinimizedNFA(CompactNFA<I> nfa, int[] origToRep, int[] origToNew ) {
		Set<Integer> initialStates = nfa.getInitialStates();
		int newStateLen = 0;
    for (int k : origToNew) {
      newStateLen = Math.max(newStateLen, k);
    }
		newStateLen++; // we want the state length, not the maximum state
		CompactNFA<I> newNFA = new CompactNFA<>(nfa.getInputAlphabet(), newStateLen);
		for(int q=0;q<newStateLen;q++) {
			newNFA.addState();
		}
		for(int q=0;q<origToNew.length;q++) {
			int repState = origToRep[q];
			int newState = origToNew[q];
			// initial states are special cases -- the merged state may be initial
			if (initialStates.contains(q)) {
				newNFA.setInitial(newState, true);
			}
			if (repState != q) {
				// merged state, ignore
				continue;
			}
			newNFA.setAccepting(newState, nfa.isAccepting(q));

			// point transitions to new states
			for (I j : nfa.getInputAlphabet()) {
				Collection<Integer> b = nfa.getSuccessors(q, j);
				if (b == null || b.isEmpty()) {
					continue;
				}
				for (Integer tState : b) {
					int newTState = origToNew[tState];
					newNFA.addTransition(newState, j, newTState);
				}
			}
		}
		return newNFA;
	}


	/**
	 * From equivalence classes, map each element to its smallest representative
	 * @param relEq - equivalence relations
	 * @param nNFA - nfa size
	 * @return - array mapping original state to its smallest representative
	 */
	static int[] findRepresentatives(Set<IntIntPair> relEq, int nNFA) {
		// Initialize an array where each element is its own representative
		int[] representativeArray = new int[nNFA];
		for (int i = 0; i < nNFA; i++) {
			representativeArray[i] = i;
		}

		// Step 1: Group elements using equality pairs
		for (IntIntPair pair : relEq) {
			int a = pair.firstInt();
			int b = pair.secondInt();

			// Find representatives for a and b
			int repA = findRepresentative(representativeArray, a);
			int repB = findRepresentative(representativeArray, b);

			// Union the groups by setting the representative to the smaller value
			if (repA != repB) {
				int newRep = Math.min(repA, repB);
				representativeArray[repA] = newRep;
				representativeArray[repB] = newRep;

				// Path compression to point all elements in the group to the new representative
				for (int i = 0; i < nNFA; i++) {
					if (findRepresentative(representativeArray, i) == repA || findRepresentative(representativeArray, i) == repB) {
						representativeArray[i] = newRep;
					}
				}
			}
		}

		// Step 2: Finalize representatives for each element
		for (int i = 0; i < nNFA; i++) {
			representativeArray[i] = findRepresentative(representativeArray, i);
		}

		return representativeArray;
	}

	// Helper function to find the representative with path compression
	static int findRepresentative(int[] representativeArray, int x) {
		if (representativeArray[x] != x) {
			representativeArray[x] = findRepresentative(representativeArray, representativeArray[x]);
		}
		return representativeArray[x];
	}

	// Renumbers the representative array to consecutive integers
	static int[] renumberRepresentatives(int[] representativeArray) {
		Int2IntMap renumberMap = new Int2IntOpenHashMap();
		int newNumber = 0;

		int[] renumberedArray = new int[representativeArray.length];
		for (int i = 0; i < representativeArray.length; i++) {
			int rep = representativeArray[i];

			// Assign a new number if this representative hasn't been mapped yet
			if (!renumberMap.containsKey(rep)) {
				renumberMap.put(rep, newNumber++);
			}

			// Set the renumbered value in the new array
			renumberedArray[i] = renumberMap.get(rep);
		}

		return renumberedArray;
	}

	/**
	 * Unify initial states (which double as final states after reversal) for better simulation.
	 * For more details, see:
	 *   Lorenzo Clemente and Richard Mayr,
	 *   Efficient reduction of nondeterministic automata with application to language inclusion testing.
	 *   In: Logical Methods in Computer Science 15 (2019).
	 */
	public static <I> void unifyInitialStatesWithoutTrim(CompactNFA<I> nfa) {
		boolean multipleInits = nfa.getInitialStates().size() > 1;
		if (!multipleInits) {
			return;
		}

		// multiple initial states. Unify into one new state
		boolean isAccepting = false;
		IntList previousInitials = new IntArrayList(nfa.getInitialStates());
		for (int q : previousInitials) {
			isAccepting = isAccepting || nfa.isAccepting(q);
			nfa.setInitial(q, false);
		}
		int unifiedState = nfa.addInitialState(isAccepting);

		// point previous initial states to unified state
		for (int q : previousInitials) {
			for (I a : nfa.getInputAlphabet()) {
				for (int t : nfa.getSuccessors(q, a)) {
					nfa.addTransition(unifiedState, a, t);
				}
			}
		}
	}
}
