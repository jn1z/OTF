package OTF.Compress;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import OTF.Registry.Registry;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import net.automatalib.common.smartcollection.AbstractBasicLinkedListEntry;
import net.automatalib.common.smartcollection.ElementReference;
import net.automatalib.common.smartcollection.IntrusiveLinkedList;

public class AntichainForest5 implements Registry {

    static final int MISSING_ELEMENT = Registry.MISSING_ELEMENT;

    private final List<Antichain> antichains;
    private final Map<BitSet, AntichainElement> cache;
    private final Int2ObjectRBTreeMap<AntichainElement> singletonAntichains;
    private final Int2ObjectRBTreeMap<Antichain> stateIdToAntichains;

    private int curIntermediateCount = 0; // used for metrics
    private int maxIntermediateCount = 0; // used for metrics

    public AntichainForest5() {
        this.antichains = new ArrayList<>();
        this.cache = new HashMap<>();
        this.singletonAntichains = new Int2ObjectRBTreeMap<>();
        this.stateIdToAntichains = new Int2ObjectRBTreeMap<>();
    }

    @Override
    public void put(BitSet elements, int stateId) {
        final AntichainElement wrapper = new AntichainElement(elements, stateId);
        cache.put(elements, wrapper);
        singletonAntichains.put(stateId, wrapper);

        this.curIntermediateCount++;
        if (this.curIntermediateCount > this.maxIntermediateCount) {
            this.maxIntermediateCount = this.curIntermediateCount;
        }
    }

    @Override
    public int get(BitSet elements) {
        // direct look-up
        final AntichainElement cached = this.cache.get(elements);
        if (cached != null) {
            return cached.stateId;
        }

        final AntichainElement wrapper = new AntichainElement(elements, -1);

        // antichain look-up
        final Antichain antichain = this.find(wrapper);
        if (antichain == null) {
            return MISSING_ELEMENT;
        }

        // cache result
        final int id = antichain.stateId;
        wrapper.stateId = id;
        antichain.cachedElements.add(wrapper);
        this.cache.put(elements, wrapper);

        return id;
    }

    private Antichain find(AntichainElement antichainElement) {
        for (Antichain antichain : antichains) {
            if (antichain.unionCardinality < antichainElement.cardinality) {
                return null;
            }
            if (antichain.coversElementsOf(antichainElement)) {
                if (antichain.containsSubsetOf(antichainElement)) {
                    return antichain;
                }
            }
        }

        return null;
    }

    @Override
    public void unify(int primary, int secondary) {
        final AntichainElement primarySingleton = singletonAntichains.remove(primary);
        final AntichainElement secondarySingleton = singletonAntichains.remove(secondary);

        final Antichain primaryAntichain = this.stateIdToAntichains.get(primary);
        final Antichain secondaryAntichain = this.stateIdToAntichains.get(secondary);

        // 4 cases, based on primary being singleton or antichain, and secondary being singleton or antichain
        if (primaryAntichain == null) {
            if (secondaryAntichain == null) {
                // unify both singletons
                unify(primary, primarySingleton, secondarySingleton);
            } else {
                // merge singleton into existing antichain
                unify(primary, secondaryAntichain, primarySingleton);
            }
        } else {
            if (secondaryAntichain == null) {
                // merge singleton into existing antichain
                unify(primary, primaryAntichain, secondarySingleton);
            } else {
                // merge both antichains
                unify(primaryAntichain, secondaryAntichain);
            }
        }

        this.curIntermediateCount--;
    }

    private void unify(int primaryStateId, AntichainElement primarySingleton, AntichainElement secondarySingleton) {
        final Antichain ac = new Antichain(primarySingleton, secondarySingleton, primaryStateId);
        this.antichains.add(ac);
        this.stateIdToAntichains.put(primaryStateId, ac);
    }

    private void unify(int primaryStateId, Antichain antichain, AntichainElement singleton) {
        if (primaryStateId != antichain.stateId) {
            this.stateIdToAntichains.remove(antichain.stateId);
            this.stateIdToAntichains.put(primaryStateId, antichain);
            antichain.stateId = primaryStateId;
            for (AntichainElement element : antichain.cachedElements) {
                element.stateId = primaryStateId;
            }
            for (AntichainElement element : antichain.antichain) {
                element.stateId = primaryStateId;
            }
        }

        antichain.add(singleton);
    }

    private void unify(Antichain primaryAntichain, Antichain secondaryAntichain) {
        primaryAntichain.merge(secondaryAntichain);

        this.antichains.remove(secondaryAntichain);
        this.stateIdToAntichains.remove(secondaryAntichain.stateId);
    }

    @Override
    public void compress() {
        for (Antichain antichain : this.antichains) {
            antichain.updateUnion();
        }

        Collections.sort(this.antichains);
    }

    @Override
    public int getMaxIntermediateCount() {
        return maxIntermediateCount;
    }

    @Override
    public String toString() {
        return "OTF5";
    }

    static boolean isSubset(long[] sub, long[] sup) {
        if (sup.length < sub.length) {
            return false;
        }

        for (int i = 0; i < sub.length; i++) {
            if ((sub[i] | sup[i]) != sup[i]) {
                return false;
            }
        }

        return true;
    }

    private static class Antichain implements Comparable<Antichain> {

        private final IntrusiveLinkedList<AntichainElement> antichain;
        private final IntrusiveLinkedList<AntichainElement> cachedElements;
        private final BitSet union;

        private int stateId;
        private int unionCardinality;
        private long[] unionSig;

        Antichain(AntichainElement e1, AntichainElement e2, int stateId) {
            this.antichain = new IntrusiveLinkedList<>();
            this.cachedElements = new IntrusiveLinkedList<>();

            if (e1.isSubsetOf(e2)) {
                this.antichain.add(e1);
                this.cachedElements.add(e2);
            } else if (e2.isSubsetOf(e1)) {
                this.antichain.add(e2);
                this.cachedElements.add(e1);
            } else if (e1.cardinality <= e2.cardinality) {
                this.antichain.add(e1);
                this.antichain.add(e2);
            } else {
                this.antichain.add(e2);
                this.antichain.add(e1);
            }

            e1.stateId = stateId;
            e2.stateId = stateId;

            this.union = (BitSet) e1.elements.clone();
            this.union.or(e2.elements);

            this.stateId = stateId;
        }

        void add(AntichainElement element) {

            ElementReference iter = this.antichain.getFrontReference();

            while (iter != null) {
                final AntichainElement ac = this.antichain.get(iter);
                if (ac.isSubsetOf(element)) {
                    this.cachedElements.add(element);
                    break;
                } else if (ac.cardinality > element.cardinality) {
                    this.antichain.insertBefore(element, iter);
                    break;
                }
                iter = this.antichain.succ(iter);
            }

            if (iter == null) {
                this.antichain.add(element);
            } else {
                while (iter != null) {
                    final AntichainElement ac = this.antichain.get(iter);
                    final ElementReference succ = this.antichain.succ(iter);
                    if (element.isSubsetOf(ac)) {
                        this.antichain.remove(iter);
                        this.cachedElements.add(ac);
                    }
                    iter = succ;
                }
            }

            element.stateId = this.stateId;

            this.union.or(element.elements);
        }

        void merge(Antichain that) {

            for (AntichainElement element : that.cachedElements) {
                element.stateId = this.stateId;
            }
            // concat afterwards, otherwise list is empty
            this.cachedElements.concat(that.cachedElements);
            this.union.or(that.union);

            // filter this elements
            ElementReference thisIter = this.antichain.getFrontReference();

            while (thisIter != null) {
                final AntichainElement thisElement = this.antichain.get(thisIter);
                // cache succ before potentially overriding their references
                final ElementReference thisNext = this.antichain.succ(thisIter);
                if (that.containsSubsetOf(thisElement)) {
                    this.antichain.remove(thisIter);
                    this.cachedElements.add(thisElement);
                }
                thisIter = thisNext;
            }

            // filter that elements
            ElementReference thatIter = that.antichain.getFrontReference();

            while (thatIter != null) {
                final AntichainElement thatElement = that.antichain.get(thatIter);
                thatElement.stateId = this.stateId;
                // cache succ before potentially overriding their references
                final ElementReference thatNext = this.antichain.succ(thatIter);
                if (this.containsSubsetOf(thatElement)) {
                    that.antichain.remove(thatIter);
                    this.cachedElements.add(thatElement);
                }
                thatIter = thatNext;
            }

            // merge antichains
            ElementReference thisRef = this.antichain.getFrontReference();
            ElementReference thatRef = that.antichain.getFrontReference();

            while (thisRef != null && thatRef != null) {
                final AntichainElement thatElement = that.antichain.get(thatRef);
                final AntichainElement thisElement = this.antichain.get(thisRef);

                // cache succs before potentially overriding their references
                final ElementReference thatNext = that.antichain.succ(thatRef);
                final ElementReference thisNext = this.antichain.succ(thisRef);

                thatElement.stateId = this.stateId;

                if (thatElement.cardinality < thisElement.cardinality) {
                    that.antichain.remove(thatRef);
                    this.antichain.insertBefore(thatElement, thisRef);
                    thatRef = thatNext;
                } else {
                    thisRef = thisNext;
                }
            }

            // append remaining that elements
            this.antichain.concat(that.antichain);
        }

        void updateUnion() {
            final int cardinality = this.unionCardinality;
            this.unionCardinality = this.union.cardinality();
            if (this.unionCardinality > cardinality) {
                this.unionSig = this.union.toLongArray();
            }
        }

        boolean coversElementsOf(AntichainElement element) {
            if (element.cardinality > this.unionCardinality) {
                return false;
            }

            return isSubset(element.sig, this.unionSig);
        }

        boolean containsSubsetOf(AntichainElement element) {
            ElementReference iter = antichain.getFrontReference();

            while (iter != null) {
                final AntichainElement ac = antichain.get(iter);
                if (ac.isSubsetOf(element)) {
                    return true;
                }
                iter = antichain.succ(iter);
            }

            return false;
        }

        @Override
        public int compareTo(Antichain that) {
            // descending
            return Integer.compare(that.unionCardinality, this.unionCardinality);
        }
    }

    private static class AntichainElement extends AbstractBasicLinkedListEntry<AntichainElement, AntichainElement> {

        private final BitSet elements;
        private final int cardinality;
        private final long[] sig;
        private int stateId;

        AntichainElement(BitSet elements, int stateId) {
            this.elements = elements;
            this.cardinality = elements.cardinality();
            this.sig = elements.toLongArray();
            this.stateId = stateId;
        }

        boolean isSubsetOf(AntichainElement that) {
            if (this.cardinality > that.cardinality) {
                return false;
            }

            return isSubset(this.sig, that.sig);
        }

        @Override
        public AntichainElement getElement() {
            return this;
        }
    }
}
