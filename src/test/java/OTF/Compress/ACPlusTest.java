package OTF.Compress;

import OTF.BitSetUtils;
import OTF.SmartBitSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class ACPlusTest {
  @Test
  void testSingleACPlus() {
    SmartBitSet b = BitSetUtils.convertListToSmartBitSet(List.of(1, 2, 3));
    ACPlus acPlus = new ACPlus(1, b);
    Assertions.assertEquals(1, acPlus.getStateId());
    acPlus.setStateId(2);
    Assertions.assertEquals(2, acPlus.getStateId());

    Assertions.assertEquals("NodeID2:{1,2,3},:{1,2,3}", acPlus.toString().replace(" ",""));
    acPlus.clear();
    Assertions.assertEquals("NodeID2::{}", acPlus.toString().replace(" ",""));

    Assertions.assertFalse(acPlus.searchable);
    acPlus.determineSearchable();
    Assertions.assertFalse(acPlus.searchable);


/*
    Assertions.assertEquals(1, acPlus.acElts.getEltsSize());
    Assertions.assertEquals(b, acPlus.acElts.allElts().get(0));

    Assertions.assertEquals(b, acPlus.acUnion);*/
  }
}
