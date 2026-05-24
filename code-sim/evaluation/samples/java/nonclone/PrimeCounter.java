public class PrimeCounter {
    public int countPrimes(int limit) {
        int count = 0;
        for (int value = 2; value <= limit; value++) {
            boolean prime = true;
            for (int factor = 2; factor * factor <= value; factor++) {
                if (value % factor == 0) {
                    prime = false;
                }
            }
            if (prime) {
                count++;
            }
        }
        return count;
    }
}
