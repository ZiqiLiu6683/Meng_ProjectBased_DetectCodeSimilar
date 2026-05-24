public class CsvColumnPicker {
    public String firstColumn(String line) {
        int comma = line.indexOf(',');
        if (comma < 0) {
            return line.trim();
        }
        return line.substring(0, comma).trim();
    }
}
