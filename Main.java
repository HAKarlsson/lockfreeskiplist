public class Main {
        public static void main(String[]args) {
                LockFreeSkipList<Integer> l = new LockFreeSkipList<>();

                if (l.add(3)) {
                        System.out.println("Add 3 successful");
                }
                if (l.remove(3)) {
                        System.out.println("Remove 3 successful");
                }
                if (l.add(3)) {
                        System.out.println("Add 3 successful");
                }
                if (l.contains(3)) {
                        System.out.println("Contains 3");
                }
        }
}
