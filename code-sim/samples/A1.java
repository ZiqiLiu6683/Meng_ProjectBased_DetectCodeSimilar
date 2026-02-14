public class A1 {
    public int score(int[] a) {
        int s = 0;
        for (int i = 0; i < a.length; i++) {
            if (a[i] > 0) s += a[i];
            else s -= a[i];
        }
        return s;
    }
}
