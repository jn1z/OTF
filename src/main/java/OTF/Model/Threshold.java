package OTF.Model;

import java.util.function.Predicate;

import net.automatalib.automaton.concept.FiniteRepresentation;

/**
 * Thresholds decide when to interrupt exploration to run an OTF minimization pass.
 */
public interface Threshold extends Predicate<FiniteRepresentation> {
    int DEFAULT_THRESHOLD_SIZE = 5000;

    String getName();

    String getParam();

    int getCrossings();

    default void update(int outSize) {}

    /**
     * adaptiveSteps(steps)
     * cadence adapts with workload; grows with larger updates, shrinks with smaller ones,
     *  but never below baseline steps, and increases are capped to at most steps per update.
     * @param steps - baseline steps
     */
    static Threshold adaptiveSteps(int steps) {
        return new Threshold() {
            int lastSize = steps; // previous update() size; seeded with baseline to avoid div-by-zero
            int crossings = 0; // number of times we've triggered
            int step = 0;// number of steps since last trigger
            int adaptiveSteps = steps; // current cadence target (>= steps), may go up/down

            @Override
            public boolean test(FiniteRepresentation finiteRepresentation) {
                step++;
                if (step > adaptiveSteps) {
                    step = 0;
                    crossings++;
                    return true;
                }
                return false;
            }

            @Override
            public String getName() {
                return "adaptiveStep";
            }

            @Override
            public String getParam() {
                return String.valueOf(steps);
            }

            @Override
            public int getCrossings() {
                return crossings;
            }

            @Override
            public void update(int size) {
                // Scale cadence proportional to size/lastSize:
                //   newSteps ≈ adaptiveSteps * (size / lastSize)
                // Increases are capped to +steps; decreases are allowed but clamped to >= steps.
                // Negative/zero size collapses to baseline 'steps'.
                int newSteps = (int) Math.ceil((double)adaptiveSteps * (double)size / (double) lastSize);
                if (newSteps < steps) {
                    adaptiveSteps = steps; // never below baseline
                } else adaptiveSteps = Math.min(newSteps, adaptiveSteps + steps); // cap growth to at most +steps
                lastSize = size;
            }
        };
    }

    /**
     * maxSteps(steps): fixed cadence; triggers roughly every k calls.
     * @param steps - steps before triggering
     */
    static Threshold maxSteps(int steps) {
        return new Threshold() {
            int crossings = 0;
            int step = 0;

            @Override
            public boolean test(FiniteRepresentation finiteRepresentation) {
                step++;
                if (step > steps) {
                    step = 0;
                    crossings++;
                    return true;
                }
                return false;
            }

            @Override
            public String getName() {
                return "step";
            }

            @Override
            public String getParam() {
                return String.valueOf(steps);
            }

            @Override
            public int getCrossings() {
                return crossings;
            }
        };
    }

    /**
     * maxInc(steps): triggers on size jumps > steps between successive metastates.
     * @param steps - baseline steps
     * @return
     */
    static Threshold maxInc(int steps) {
        return new Threshold() {
            int crossings = 0;
            int size = 0;

            @Override
            public boolean test(FiniteRepresentation finiteRepresentation) {
                int newSize = finiteRepresentation.size();
                if (newSize > size + steps) {
                    size = newSize;
                    crossings++;
                    return true;
                }
                return false;
            }

            @Override
            public String getName() {
                return "inc";
            }

            @Override
            public String getParam() {
                return String.valueOf(steps);
            }

            @Override
            public int getCrossings() {
                return crossings;
            }
        };
    }

    static Threshold noop() {
        return new Threshold() {
            @Override
            public boolean test(FiniteRepresentation finiteRepresentation) {
                return false;
            }

            @Override
            public String getName() {
                return "noop";
            }

            @Override
            public String getParam() {
                return "";
            }

            @Override
            public int getCrossings() {
                return 0;
            }
        };
    }
}
