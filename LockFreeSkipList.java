import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicMarkableReference;
import java.util.Random;

public final class LockFreeSkipList<T> {
        public static final int MAX_LEVEL = 16;
        private static final Random rng = new Random();
        private final Node<T> head = new Node<T>(Integer.MIN_VALUE);
        private final Node<T> tail = new Node<T>(Integer.MAX_VALUE);
        private AtomicInteger counter;

        public LockFreeSkipList() {
                for (int i = 0; i < head.next.length; i++) {
                        head.next[i] = new AtomicMarkableReference<LockFreeSkipList.Node<T>>(tail, false);
                }
                counter = new AtomicInteger(0);
        }

        private static final class Node<T> {
                final T value;
                final int key;
                final AtomicMarkableReference<Node<T>>[] next;
                private final int topLevel;

                @SuppressWarnings("unchecked")
                public Node(int key) {
                        value = null;
                        this.key = key;
                        next = (AtomicMarkableReference<Node<T>>[])new AtomicMarkableReference[MAX_LEVEL + 1];
                        for (int i = 0; i < next.length; i++) {
                                next[i] = new AtomicMarkableReference<Node<T>>(null, false);
                        }
                        topLevel = MAX_LEVEL;
                }

                @SuppressWarnings("unchecked")
                public Node(T x, int height) {
                        value = x;
                        key = x.hashCode();
                        next = (AtomicMarkableReference<Node<T>>[])new AtomicMarkableReference[height + 1];
                        for (int i = 0; i < next.length; i++) {
                                next[i] = new AtomicMarkableReference<Node<T>>(null, false);
                        }
                        topLevel = height;
                }
        }

        private static int randomLevel() {
                int r = rng.nextInt();
                int level = 0;
                r &= (1 << MAX_LEVEL) - 1;
                while ((r & 1) != 0) {
                        r >>>= 1;
                        level++;
                }
                return level;
        }

        @SuppressWarnings("unchecked")
        public boolean add(T x) {
                int topLevel = randomLevel();
                int bottomLevel = 0;
                Node<T>[] preds = (Node<T>[]) new Node[MAX_LEVEL + 1];
                Node<T>[] succs = (Node<T>[]) new Node[MAX_LEVEL + 1];
                while (true) {
                        boolean found = find(x, preds, succs);
                        if (found) {
                                return false;
                        } else {
                                Node<T> newNode = new Node(x, topLevel);
                                for (int level = bottomLevel; level <= topLevel; level++) {
                                        Node<T> succ = succs[level];
                                        newNode.next[level].set(succ, false);
                                }
                                Node<T> pred = preds[bottomLevel];
                                Node<T> succ = succs[bottomLevel];
                                if (!pred.next[bottomLevel].compareAndSet(succ, newNode, false, false)) {
                                        continue;
                                }
                                for (int level = bottomLevel + 1; level <= topLevel; level++) {
                                        while (true) {
                                                pred = preds[level];
                                                succ = succs[level];
                                                if (pred.next[level].compareAndSet(succ, newNode, false, false))
                                                        break;
                                                find(x, preds, succs);
                                        }
                                }
                                counter.getAndIncrement();
                                return true;
                        }
                }
        }

        @SuppressWarnings("unchecked")
        public boolean remove(T x) {
                int bottomLevel = 0;
                Node<T>[] preds = (Node<T>[]) new Node[MAX_LEVEL + 1];
                Node<T>[] succs = (Node<T>[]) new Node[MAX_LEVEL + 1];
                Node<T> succ;
                while (true) {
                        boolean found = find(x, preds, succs);
                        if (!found) {
                                return false;
                        } else {
                                Node<T> nodeToRemove = succs[bottomLevel];
                                for (int level = nodeToRemove.topLevel; level >= bottomLevel+1; level --) {
                                        boolean[] marked = {false};
                                        succ = nodeToRemove.next[level].get(marked);
                                        while (!marked[0]) {
                                                nodeToRemove.next[level].compareAndSet(succ, succ, false, true);
                                                succ = nodeToRemove.next[level].get(marked);
                                        }
                                }
                                boolean[] marked = {false};
                                succ = nodeToRemove.next[bottomLevel].get(marked);
                                while (true) {
                                        boolean iMarkedIt = nodeToRemove.next[bottomLevel].compareAndSet(succ, succ, false, true);
                                        succ = succs[bottomLevel].next[bottomLevel].get(marked);
                                        if (iMarkedIt) {
                                                find(x, preds, succs);
                                                counter.getAndDecrement();
                                                return true;
                                        } else if (marked[0]) {
                                                return false;
                                        }
                                }
                        }
                }
        }


        private boolean find(T x, Node<T>[] preds, Node<T>[] succs) {
                int bottomLevel = 0;
                int key = x.hashCode();
                boolean[] marked = {false};
                boolean snip;
                Node<T> pred = null;
                Node<T> curr = null;
                Node<T> succ = null;
retry:
                while (true) {
                        pred = head;
                        for (int level = MAX_LEVEL; level >= bottomLevel; level--) {
                                curr = pred.next[level].getReference();
                                while (true) {
                                        succ = curr.next[level].get(marked);
                                        while (marked[0]) {
                                                snip = pred.next[level].compareAndSet(curr, succ, false, false);
                                                if (!snip) continue retry;
                                                curr = pred.next[level].getReference();
                                                succ = curr.next[level].get(marked);
                                        }
                                        if (curr.key < key) {
                                                pred = curr;
                                                curr = succ;
                                        } else {
                                                break;
                                        }
                                }
                                preds[level] = pred;
                                succs[level] = curr;
                        }
                        return (curr.key == key) && x.equals(curr.value);
                }
        }

        public boolean contains(T x) {
                int bottomLevel = 0;
                int v = x.hashCode();
                boolean[] marked = {false};
                Node<T> pred = head;
                Node<T> curr = null;
                Node<T> succ = null;
                for (int level = MAX_LEVEL; level >= bottomLevel; level--) {
                        curr = pred.next[level].getReference();
                        while (true) {
                                succ = curr.next[level].get(marked);
                                while (marked[0]) {
                                        curr = curr.next[level].getReference();
                                        succ = curr.next[level].get(marked);
                                }
                                if (curr.key < v) {
                                        pred = curr;
                                        curr = succ;
                                } else {
                                        break;
                                }
                        }
                }
                return (curr.key == v) && x.equals(curr.value);
        }

        public int size() {
                return counter.get();
        }
}

