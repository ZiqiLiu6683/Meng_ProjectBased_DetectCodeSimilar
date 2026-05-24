import java.util.Map;

public class InventoryLookup {
    public boolean inStock(Map<String, Integer> inventory, String sku) {
        return inventory.getOrDefault(sku, 0) > 0;
    }
}
