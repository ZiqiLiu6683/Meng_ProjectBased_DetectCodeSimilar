public class FormatBase {


    public int countPositive( int[] values ) {
        int total = 0;

        for ( int value : values ) {

            if ( value > 0 ) {
                total++;
            }
        }

        return total;
    }
}
