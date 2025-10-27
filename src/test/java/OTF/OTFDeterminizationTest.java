package OTF;

import OTF.Compress.AntichainForest2;
import OTF.Compress.AntichainForest5;
import OTF.Compress.AntichainForest5Idx;
import OTF.Model.Threshold;
import OTF.Registry.AddressRegistry;
import OTF.Registry.Registry;
import net.automatalib.automaton.fsa.DFA;
import net.automatalib.automaton.fsa.impl.CompactNFA;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class OTFDeterminizationTest {
  private static Path getFilePath(String resourcePath) throws URISyntaxException {
    return Paths.get(Objects.requireNonNull(
        OTFDeterminizationTest.class.getClassLoader().getResource(resourcePath)).toURI());
  }
  @Test
  void testSmallBA() throws URISyntaxException {
    Path filePath = getFilePath("fold.ba"); // small example from RABIT
    assertTrue(filePath.toFile().exists());
    String filePathStr = filePath.toAbsolutePath().toString();
    CompactNFA<Integer> myNFA = BAFormat.getBAFile(filePath.toAbsolutePath().toString());
    Assertions.assertNotNull(myNFA);
    Assertions.assertEquals(6, myNFA.size());
    Assertions.assertEquals(4, myNFA.getInputAlphabet().size());
    CompactNFA<Integer> origNFA = BAFormat.getBAFile(filePathStr);

    OTFCommandLine.CCL(origNFA, false);
    // Note: this will throw an exception if SC and OTF return different answers

    origNFA = BAFormat.getBAFile(filePathStr);
    OTFCommandLine.CCL(origNFA, true);
    // Note: this will throw an exception if SC and OTF return different answers

    Threshold threshold = Threshold.maxSteps(5);
    Registry registry = new AntichainForest2();
    DFA<?, Integer> newOTF = OTFDeterminization.doOTF(myNFA.powersetView(), myNFA.getInputAlphabet(), threshold, registry);
    Assertions.assertEquals(7, newOTF.size());

    registry = new AntichainForest5();
    newOTF = OTFDeterminization.doOTF(myNFA.powersetView(), myNFA.getInputAlphabet(), threshold, registry);
    Assertions.assertEquals(7, newOTF.size());

    registry = new AntichainForest5Idx(myNFA.size());
    newOTF = OTFDeterminization.doOTF(myNFA.powersetView(), myNFA.getInputAlphabet(), threshold, registry);
    Assertions.assertEquals(7, newOTF.size());
  }

  @Test
  void testMediumBA() throws URISyntaxException {
    Path filePath = getFilePath("thm5.ba"); // medium example from Walnut
    assertTrue(filePath.toFile().exists());
    String filePathStr = filePath.toAbsolutePath().toString();
    CompactNFA<Integer> myNFA = BAFormat.getBAFile(filePathStr);
    Assertions.assertNotNull(myNFA);
    Assertions.assertEquals(1790, myNFA.size());
    Assertions.assertEquals(2, myNFA.getInputAlphabet().size());

    CompactNFA<Integer> origNFA = BAFormat.getBAFile(filePathStr);
    DFA<?, Integer> dfa = OTFCommandLine.CCL(origNFA, true);

    Assertions.assertEquals(12, dfa.size());
  }
}
