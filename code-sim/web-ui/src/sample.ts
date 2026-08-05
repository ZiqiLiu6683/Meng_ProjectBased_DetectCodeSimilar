// Fixed example inputs only. No analysis response is bundled with the frontend:
// the demo action submits these exact sources to /api/analyze so every displayed
// verdict, range, tag, and decision path comes from the current real pipeline.
export const DEMO_LEFT = `class Left {
  int score(int x) {
    int t = x + 1;
    return t * t;
  }

  int total(int[] a) {
    int s = 0;
    for (int i = 0; i < a.length; i++) {
      s += a[i];
    }
    return s;
  }
}`;

export const DEMO_RIGHT = `class Right {
  int score(int x) {
    int t = x + 1;
    return sq(t);
  }

  int sq(int y) {
    return y * y;
  }

  int total(int[] a) {
    return sum(a, 0);
  }

  int sum(int[] a, int i) {
    if (i >= a.length) return 0;
    return a[i] + sum(a, i + 1);
  }
}`;
