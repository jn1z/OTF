package OTF.Compress;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import OTF.BitSetUtils;
import OTF.Registry.Registry;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import net.automatalib.common.smartcollection.AbstractBasicLinkedListEntry;
import net.automatalib.common.smartcollection.ElementReference;
import net.automatalib.common.smartcollection.IntrusiveLinkedList;

public class AntichainForest2 implements Registry {

    static final int MISSING_ELEMENT = Registry.MISSING_ELEMENT;

    private final Map<BitSet, AntichainElement> bitsetToWrapper;
    private final Int2ObjectRBTreeMap<AntichainElement> singletonAntichains;
    private final Int2ObjectRBTreeMap<ElementReference> stateIdToAntichain;
    private final IntrusiveLinkedList<Antichain> antichains;

    private int curIntermediateCount = 0; // used for metrics
    private int maxIntermediateCount = 0; // used for metrics

    public AntichainForest2() {
        this.bitsetToWrapper = new HashMap<>();
        this.singletonAntichains = new Int2ObjectRBTreeMap<>();
        this.stateIdToAntichain = new Int2ObjectRBTreeMap<>();
        this.antichains = new IntrusiveLinkedList<>();
    }

    @Override
    public void put(BitSet elements, int stateId) {
        final AntichainElement wrapper = new AntichainElement(elements, stateId);
        bitsetToWrapper.put(elements, wrapper);
        singletonAntichains.put(stateId, wrapper);

        this.curIntermediateCount++;
        if (this.curIntermediateCount > this.maxIntermediateCount) {
            this.maxIntermediateCount = this.curIntermediateCount;
        }
    }

    @Override
    public int get(BitSet elements) {

        // direct look-up
        final AntichainElement singleWrapper = this.bitsetToWrapper.get(elements);
        if (singleWrapper != null) {
            return singleWrapper.stateId;
        }

        // antichain look-up
        final Antichain antichain = this.find(elements);
        if (antichain == null) {
            return MISSING_ELEMENT;
        }

        // cache result
        final int id = antichain.stateId;
        AntichainElement wrapper = new AntichainElement(elements, id);
        this.bitsetToWrapper.put(elements, wrapper);
        antichain.add(wrapper);

        return id;
    }

    private Antichain find(BitSet elements) {

        final int card = elements.cardinality();

        for (Antichain antichain : antichains) {
            if (antichain.unionCardinality < card) {
                break;
            }
            if (BitSetUtils.isSubset(elements, antichain.union)) {
                for (AntichainElement ac : antichain.elements) {
                    if (ac.cardinality > card) {
                        break;
                    }
                    if (BitSetUtils.isSubset(ac.elements, elements)) {
                        return antichain;
                    }
                }
            }
        }

        return null;
    }

    @Override
    public void unify(int primary, int secondary) {
        AntichainElement primaryElt = singletonAntichains.remove(primary);
        AntichainElement secondaryElt = singletonAntichains.remove(secondary);

        ElementReference primaryAntichain = this.stateIdToAntichain.get(primary);
        ElementReference secondaryAntichain = this.stateIdToAntichain.get(secondary);

        // 4 cases, based on primary being 1-elt or Antichain, and secondary being 1-elt or Antichain
        if (primaryAntichain == null) {
            if (secondaryAntichain == null) {
                // unify both singletons
                unify(primary, primaryElt, secondaryElt);
            } else {
                // merge singleton into existing antichain
                unify(primary, secondaryAntichain, primaryElt);
            }
        } else {
            if (secondaryAntichain == null) {
                // merge singleton into existing antichain
                unify(primary, primaryAntichain, secondaryElt);
            } else {
                // merge both antichains
                unify(primaryAntichain, secondaryAntichain);
            }
        }

        this.curIntermediateCount--;
    }

    private void unify(int primaryStateId, AntichainElement primaryElt, AntichainElement secondaryElt) {
        final Antichain ac = new Antichain(primaryElt, secondaryElt, primaryStateId);

        ElementReference iter = this.antichains.getFrontReference();
        while (iter != null && ac.unionCardinality < this.antichains.get(iter).unionCardinality) {
            iter = this.antichains.succ(iter);
        }

        final ElementReference reference;
        if (iter == null) {
            reference = this.antichains.referencedAdd(ac);
        } else {
            reference = this.antichains.insertBefore(ac, iter);
        }

        this.stateIdToAntichain.put(primaryStateId, reference);
    }

    private void unify(int primaryStateId, ElementReference reference, AntichainElement elt) {

        final Antichain antichain = this.antichains.get(reference);

        if (primaryStateId != antichain.stateId) {
            this.stateIdToAntichain.remove(antichain.stateId);
            this.stateIdToAntichain.put(primaryStateId, reference);
            antichain.stateId = primaryStateId;
            for (AntichainElement element : antichain.elements) {
                element.stateId = primaryStateId;
            }
        }

        final int increase = antichain.add(elt);

        if (increase > 0) {
            moveBackwardIfNecessary(reference, antichain);
        }
    }

    private void unify(ElementReference primaryAntichain, ElementReference secondaryAntichain) {

        Antichain primaryAC = this.antichains.get(primaryAntichain);
        Antichain secondaryAC = this.antichains.get(secondaryAntichain);

        final int increase = primaryAC.add(secondaryAC);

        this.antichains.remove(secondaryAntichain);
        this.stateIdToAntichain.remove(secondaryAC.stateId);

        if (increase > 0) {
            moveBackwardIfNecessary(primaryAntichain, primaryAC);
        }
    }

    @Override
    public int getMaxIntermediateCount() {
        return maxIntermediateCount;
    }

    @Override
    public String toString() {
        return "OTF1.5";
    }

    private void moveBackwardIfNecessary(ElementReference reference, Antichain antichain) {
        final ElementReference succ = this.antichains.pred(reference);
        ElementReference iter = succ;
        while (iter != null && antichain.unionCardinality > this.antichains.get(iter).unionCardinality) {
            iter = this.antichains.pred(iter);
        }

        if (iter != succ) {
            this.antichains.remove(reference);

            ElementReference ref;
            if (iter == null) {
                ref = this.antichains.pushFront(antichain);
            } else {
                ref = this.antichains.insertAfter(antichain, iter);
            }

            this.stateIdToAntichain.put(antichain.stateId, ref);
        }
    }

    private static class Antichain extends AbstractBasicLinkedListEntry<Antichain, Antichain> {

        private final IntrusiveLinkedList<AntichainElement> elements;
        private final BitSet union;

        private int stateId;
        private int unionCardinality;

        Antichain(AntichainElement e1, AntichainElement e2, int stateId) {
            this.elements = new IntrusiveLinkedList<>();

            if (e1.cardinality <= e2.cardinality) {
                this.elements.add(e1);
                this.elements.add(e2);
            } else {
                this.elements.add(e2);
                this.elements.add(e1);
            }

            e1.stateId = stateId;
            e2.stateId = stateId;

            this.union = (BitSet) e1.elements.clone();
            this.union.or(e2.elements);

            this.unionCardinality = this.union.cardinality();

            this.stateId = stateId;
        }

        int add(AntichainElement element) {
            final Iterator<ElementReference> iter = this.elements.referenceIterator();

            while (iter.hasNext()) {
                final ElementReference ref = iter.next();
                if (this.elements.get(ref).cardinality > element.cardinality) {
                    this.elements.insertBefore(element, ref);
                    break;
                } else if (!iter.hasNext()) {
                    this.elements.insertAfter(element, ref);
                }
            }

            element.stateId = this.stateId;

            final int oldCard = unionCardinality;
            this.union.or(element.elements);
            this.unionCardinality = this.union.cardinality();

            return this.unionCardinality - oldCard;
        }

        int add(Antichain that) {
            ElementReference thisRef = this.elements.getFrontReference();
            ElementReference thatRef = that.elements.getFrontReference();

            while (thisRef != null && thatRef != null) {
                final AntichainElement thatElement = that.elements.get(thatRef);
                final AntichainElement thisElement = this.elements.get(thisRef);

                // cache succs before potentially overriding their references
                ElementReference thatNext = that.elements.succ(thatRef);
                ElementReference thisNext = this.elements.succ(thisRef);

                if (thisElement.cardinality > thatElement.cardinality) {
                    this.elements.insertBefore(thatElement, thisRef);
                    thatElement.stateId = this.stateId;
                    this.union.or(thatElement.elements);
                    thatRef = thatNext;
                } else {
                    thisRef = thisNext;
                }
            }

            while (thatRef != null) {
                final AntichainElement thatElement = that.elements.get(thatRef);

                // cache succ before potentially overriding their references
                ElementReference thatNext = that.elements.succ(thatRef);
                this.elements.add(thatElement);
                thatElement.stateId = this.stateId;
                this.union.or(thatElement.elements);
                thatRef = thatNext;
            }

            final int oldCard = unionCardinality;
            this.unionCardinality = this.union.cardinality();

            return this.unionCardinality - oldCard;
        }

        @Override
        public Antichain getElement() {
            return this;
        }
    }

    private static class AntichainElement extends AbstractBasicLinkedListEntry<AntichainElement, AntichainElement> {

        private final BitSet elements;
        private final int cardinality;
        private int stateId;

        AntichainElement(BitSet elements, int stateId) {
            this.elements = elements;
            this.cardinality = elements.cardinality();
            this.stateId = stateId;
        }

        @Override
        public AntichainElement getElement() {
            return this;
        }
    }

}
