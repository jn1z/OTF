package OTF.Model;

import java.util.function.Predicate;

import net.automatalib.automaton.concept.FiniteRepresentation;

public interface Threshold extends Predicate<FiniteRepresentation> {

    String getName();

    String getParam();

    int getCrossings();

    default void update(int outSize) {}

    static Threshold adaptiveSteps(int steps) {
        return new Threshold() {
            int lastSize = steps;
            int crossings = 0;
            int step = 0;
            int adaptiveSteps = steps;

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
                int newSteps = (int) Math.ceil((double)adaptiveSteps * (double)size / (double) lastSize);
                if (newSteps < steps) {
                    adaptiveSteps = steps;
                } else adaptiveSteps = Math.min(newSteps, adaptiveSteps + steps);
                lastSize = size;
                //System.out.println("New adaptive steps: " + adaptiveSteps);
            }
        };
    }

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
