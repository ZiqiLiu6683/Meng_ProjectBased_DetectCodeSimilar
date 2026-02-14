public class A2 {
    private int step(int s, int x) {
        if (x > 0) return s + x;
        return s - x;
    }
    public int score(int[] a) {
        int s = 0;
        for (int i = 0; i < a.length; i++) {
            s = step(s, a[i]);
        }
        return s;
    }
}


