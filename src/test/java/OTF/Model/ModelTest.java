package OTF.Model;

import net.automatalib.automaton.concept.FiniteRepresentation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ModelTest {
  @Test
  void testThresholds() {
    Threshold t = Threshold.maxSteps(1);
    Assertions.assertEquals("1", t.getParam());
    Assertions.assertEquals("step", t.getName());
    Assertions.assertFalse(t.test(null));
    Assertions.assertEquals(0, t.getCrossings());
    Assertions.assertTrue(t.test(null)); // trigger after 1 step
    Assertions.assertEquals(1, t.getCrossings());
    t.update(0);

    t = Threshold.adaptiveSteps(1);
    Assertions.assertEquals("1", t.getParam());
    Assertions.assertEquals("adaptiveStep", t.getName());
    Assertions.assertFalse(t.test(null));
    Assertions.assertEquals(0, t.getCrossings());
    Assertions.assertTrue(t.test(null)); // trigger after 1 step
    Assertions.assertEquals(1, t.getCrossings());
    t.update(1);
    Assertions.assertEquals("1", t.getParam());
    Assertions.assertFalse(t.test(null));

    t = Threshold.maxInc(1);
    Assertions.assertEquals("1", t.getParam());
    Assertions.assertEquals("inc", t.getName());
    FiniteRepresentation fR = () -> 0;
    Assertions.assertFalse(t.test(fR));
    Assertions.assertEquals(0, t.getCrossings());

    fR = () -> 5;
    Assertions.assertTrue(t.test(fR));
    Assertions.assertEquals(1, t.getCrossings());
  }
}
