package OTF.Simulation;

import java.io.Serial;
import java.util.BitSet;
import java.util.concurrent.RecursiveTask;

public final class ParSimTask extends RecursiveTask<Boolean> {
  private final int p1, p2, q1, q2;
  private final int nStates, nSymbols;
  private final int[][][] succ;
  private final FixedBitSet[] relation; // Need to be careful, since BitSet is not threadsafe

  // Minimum subproblem size to justify more splitting
  private static final int MIN_SUBPROBLEM_SIZE = 20;

  // Factor for subdividing
  private static final int SUBDIVISION_FACTOR = 16;
  @Serial
  private static final long serialVersionUID = 12345L;

  // Threshold for subdividing tasks in fork-join
  private static int thresholdForkJoin(int nStates) {
    return Math.max(MIN_SUBPROBLEM_SIZE, nStates / SUBDIVISION_FACTOR);
  }

  ParSimTask(int p1, int p2, int q1, int q2,
             int nSymbols,
             int[][][] succ,
             FixedBitSet[] relation) {
    this.p1 = p1;
    this.p2 = p2;
    this.q1 = q1;
    this.q2 = q2;
    this.nStates = relation.length;
    this.nSymbols = nSymbols;
    this.succ = succ;
    this.relation = relation;
  }

  @Override
  protected Boolean compute() {
    // Split problem into sub-problem quadrants.

    // If it's too small in either dimension, do a single-thread refinement.
    if (!shouldSplit(p1, p2, q1, q2, nStates)) {
      return NaiveSimulation.singleRefine(p1, p2, q1, q2, nSymbols, succ, relation);
    }

    // Compute midpoints, aligning to multiples of 32 for safe parallel updates.
    int pMid = midpointAligned(p1, p2);
    int qMid = midpointAligned(q1, q2);

    // If the alignment fails to split properly (mid == start), just do single-thread.
    if (pMid <= p1 || qMid <= q1) {
      return NaiveSimulation.singleRefine(p1, p2, q1, q2, nSymbols, succ, relation);
    }

    // Since we're splitting at long boundaries, we can safely use threads with FixedBitSet elements
    ParSimTask t1 = new ParSimTask(p1, pMid, q1, qMid, nSymbols, succ, relation);
    ParSimTask t2 = new ParSimTask(p1, pMid, qMid, q2, nSymbols, succ, relation);
    ParSimTask t3 = new ParSimTask(pMid, p2, q1, qMid, nSymbols, succ, relation);
    ParSimTask t4 = new ParSimTask(pMid, p2, qMid, q2, nSymbols, succ, relation);
    t2.fork();
    t3.fork();
    t4.fork();
    boolean r1 = t1.compute();
    boolean r2 = t2.join();
    boolean r3 = t3.join();
    boolean r4 = t4.join();
    return r1 || r2 || r3 || r4;
  }

  // Decide if we should subdivide or do single-thread refinement
  private static boolean shouldSplit(int p1, int p2, int q1, int q2, int nStates) {
    int threshold = thresholdForkJoin(nStates);
    return ((p2 - p1 > threshold) && (q2 - q1 > threshold));
  }

  // Compute a midpoint aligned down to the nearest multiple of 64
  private static int midpointAligned(int lo, int hi) {
    int mid = lo + (hi - lo) / 2;   // standard midpoint
    // round down to 64-bit word boundary, good for longs in BitSet
    mid &= ~63;   // equivalent to mid & -64
    return mid;
  }
}
