package OTF.Compress;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import OTF.Registry.Registry;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import net.automatalib.common.smartcollection.AbstractBasicLinkedListEntry;
import net.automatalib.common.smartcollection.IntrusiveLinkedList;
import net.automatalib.common.util.array.ArrayStorage;

public class AntichainForest5Idx implements Registry {

    static final int MISSING_ELEMENT = Registry.MISSING_ELEMENT;

    private final int nNFA;
    private final Index<Antichain> unionIndex;
    private final Map<BitSet, AntichainElement> cache;
    private final Int2ObjectRBTreeMap<AntichainElement> singletonAntichains;
    private final Int2ObjectRBTreeMap<Antichain> stateIdToAntichains;

    private int curIntermediateCount = 0; // used for metrics
    private int maxIntermediateCount = 0; // used for metrics

    public AntichainForest5Idx(int nNFA) {
        this.nNFA = nNFA;
        this.unionIndex = new Index<>(nNFA);
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

        // antichain look-up
        final Antichain antichain = this.find(elements);
        if (antichain == null) {
            return MISSING_ELEMENT;
        }

        // cache result
        final AntichainElement wrapper = new AntichainElement(elements, antichain.stateId);
        antichain.cachedElements.add(wrapper);
        this.cache.put(elements, wrapper);

        return wrapper.stateId;
    }

    private Antichain find(BitSet elements) {
        for (Antichain antichain : this.unionIndex.findSuperSets(elements)) {
            if (antichain.containsSubsetOf(elements)) {
                return antichain;
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
//        check();
    }

    private void unify(int primaryStateId, AntichainElement primarySingleton, AntichainElement secondarySingleton) {
        final Antichain ac = new Antichain(primarySingleton, secondarySingleton, primaryStateId, this.nNFA);

        ac.indexPos = this.unionIndex.insert(ac.union, ac);
        this.stateIdToAntichains.put(primaryStateId, ac);
    }

    private void unify(int primaryStateId, Antichain antichain, AntichainElement singleton) {
        if (primaryStateId != antichain.stateId) {
            this.stateIdToAntichains.remove(antichain.stateId);
            this.stateIdToAntichains.put(primaryStateId, antichain);
            antichain.updateStateId(primaryStateId);
        }

        antichain.add(singleton);
        this.unionIndex.override(antichain.union, antichain.indexPos);
    }

    private void unify(Antichain primaryAntichain, Antichain secondaryAntichain) {
        this.stateIdToAntichains.remove(secondaryAntichain.stateId);
        secondaryAntichain.updateStateId(primaryAntichain.stateId);

        // merge smaller AC into larger AC
        if (primaryAntichain.size() >= secondaryAntichain.size()) {
            primaryAntichain.merge(secondaryAntichain);

            this.unionIndex.delete(secondaryAntichain.indexPos);
            this.unionIndex.override(primaryAntichain.union, primaryAntichain.indexPos);
        } else {
            this.stateIdToAntichains.put(primaryAntichain.stateId, secondaryAntichain);
            secondaryAntichain.merge(primaryAntichain);

            this.unionIndex.delete(primaryAntichain.indexPos);
            this.unionIndex.override(secondaryAntichain.union, secondaryAntichain.indexPos);
        }
    }

    @Override
    public int getMaxIntermediateCount() {
        return maxIntermediateCount;
    }

    @Override
    public String toString() {
        return "OTF5IDX";
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

    private static class Antichain implements Signature {

        private final AntichainForest5Idx.Index<AntichainElement> index;
        private final IntrusiveLinkedList<AntichainElement> cachedElements;
        private final BitSet union;

        private int indexPos;
        private int stateId;

        Antichain(AntichainElement e1, AntichainElement e2, int stateId, int nNFA) {
            this.index = new AntichainForest5Idx.Index<>(nNFA);
            this.cachedElements = new IntrusiveLinkedList<>();

            if (e1.isSubsetOf(e2)) {
                e1.indexPos = this.index.insert(e1.elements, e1);
                this.cachedElements.add(e2);
            } else if (e2.isSubsetOf(e1)) {
                e2.indexPos = this.index.insert(e2.elements, e2);
                this.cachedElements.add(e1);
            } else {
                e1.indexPos = this.index.insert(e1.elements, e1);
                e2.indexPos = this.index.insert(e2.elements, e2);
            }

            e1.stateId = stateId;
            e2.stateId = stateId;

            this.union = (BitSet) e1.elements.clone();
            this.union.or(e2.elements);

            this.stateId = stateId;
        }

        void add(AntichainElement element) {

            final boolean containsSubSet = this.index.containsSubSet(element.elements);

            if (containsSubSet) {
                this.cachedElements.add(element);
            } else {
                final Iterable<AntichainElement> superSets = this.index.findSuperSets(element.elements);
                for (AntichainElement superSet : superSets) {
                    this.index.delete(superSet.indexPos);
                    this.cachedElements.add(superSet);
                }
                element.indexPos = this.index.insert(element.elements, element);
            }

            element.stateId = this.stateId;
            this.union.or(element.elements);
        }

        void merge(Antichain that) {

            this.cachedElements.concat(that.cachedElements);
            this.union.or(that.union);

            for (AntichainElement element : that.index.elements()) {

                final boolean constainsSubSet = this.index.containsSubSet(element.elements);

                if (constainsSubSet) {
                    this.cachedElements.add(element);
                } else {
                    final Iterable<AntichainElement> superSets = this.index.findSuperSets(element.elements);
                    for (AntichainElement superSet : superSets) {
                        this.index.delete(superSet.indexPos);
                        this.cachedElements.add(superSet);
                    }
                    element.indexPos = this.index.insert(element.elements, element);
                }
            }
        }

        void updateStateId(int newId) {
            this.stateId = newId;
            for (AntichainElement cachedElement : this.cachedElements) {
                cachedElement.stateId = newId;
            }
            for (AntichainElement element : this.index.elements()) {
                element.stateId = newId;
            }
        }

        boolean containsSubsetOf(BitSet elements) {
            return this.index.containsSubSet(elements);
        }

        int size() {
            return this.index.size();
        }

        @Override
        public long[] getSig() {
            return union.toLongArray();
        }

        @Override
        public String toString() {
            return "Antichain{" + "union=" + union + ", idx=" + indexPos + ", stateId=" + stateId + '}';
        }
    }

    private static class AntichainElement extends AbstractBasicLinkedListEntry<AntichainElement, AntichainElement> implements Signature {

        private final BitSet elements;
        private final int cardinality;
        private final long[] sig;
        private int indexPos;
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

        @Override
        public long[] getSig() {
            return sig;
        }
    }

    private static class Index<T extends Signature> {

        private static final int THRESHOLD = 8;

        final ArrayStorage<T> data;
        final BitSet[] index;
        final BitSet openLocations;
        int maxId;
        int size;

        public Index(int maxDim) {
            this.data = new ArrayStorage<>();
            this.index = new BitSet[maxDim];
            this.openLocations = new BitSet();
            this.maxId = 0;
            this.size = 0;

            for (int i = 0; i < maxDim; i++) {
                this.index[i] = new BitSet();
            }
        }

        public int insert(BitSet sig, T data) {
            int id;

            if (openLocations.isEmpty()) {
                id = maxId++;

                for (int i = sig.nextSetBit(0); i >= 0; i = sig.nextSetBit(i + 1)) {
                    this.index[i].set(id);
                }

            } else {
                id = openLocations.nextSetBit(0);
                openLocations.clear(id);

                override(sig, id);
            }

            this.data.ensureCapacity(id + 1);
            this.data.set(id, data);
            this.size++;

            return id;
        }

        public void override(BitSet sig, int id) {
            for (int i = 0; i < index.length; i++) {
                index[i].set(id, sig.get(i));
            }
        }

        public Iterable<T> findSuperSets(BitSet sig) {

            if (maxId == 0) {
                return Collections.emptyList();
            } else if (sig.isEmpty()) {
                return elements();
            } else {
                final int pos = sig.nextSetBit(0);
                final BitSet mask = (BitSet) this.index[pos].clone();
                mask.andNot(this.openLocations);

                for (int i = sig.nextSetBit(pos + 1); i >= 0; i = sig.nextSetBit(i + 1)) {
                    mask.and(this.index[i]);
                }

                if (mask.isEmpty()) {
                    return Collections.emptyList();
                }

                final List<T> result = new ArrayList<>(mask.cardinality());
                for (int i = mask.nextSetBit(0); i >= 0; i = mask.nextSetBit(i + 1)) {
                    result.add(data.get(i));
                }

                return result;
            }
        }

        public boolean containsSubSet(BitSet sig) {
            if (sig.isEmpty()) {
                return false;
            } else {
                if (size <= THRESHOLD) {
                    final long[] arr = sig.toLongArray();

                    for (int i = 0; i < maxId; i++) {
                        final T t = data.get(i);
                        if (t != null && isSubset(t.getSig(), arr)) {
                            return true;
                        }
                    }

                    return false;
                } else {
                    final BitSet mask = (BitSet) this.openLocations.clone();

                    for (int i = sig.nextClearBit(0); i >= 0 && i < index.length; i = sig.nextClearBit(i + 1)) {
                        mask.or(this.index[i]);
                    }

                    final int idx = mask.nextClearBit(0);

                    return idx >= 0 && idx < maxId;
                }
            }
        }

        public void delete(int i) {
            this.openLocations.set(i);
            this.size--;
            this.data.set(i, null);
        }

        public Iterable<T> elements() {
            return () -> this.data.stream().filter(Objects::nonNull).iterator();
        }

        public int size() {
            return size;
        }
    }

    interface Signature {
        long[] getSig();
    }
}
