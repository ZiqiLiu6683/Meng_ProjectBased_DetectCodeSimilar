public class TemperatureAlert {
    public boolean hasFever(double[] readings) {
        for (double reading : readings) {
            if (reading >= 38.0) {
                return true;
            }
        }
        return false;
    }
}
