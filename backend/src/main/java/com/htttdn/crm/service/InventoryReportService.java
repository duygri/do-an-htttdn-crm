package com.htttdn.crm.service;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/** Inventory valuation using the latest supplier receipt cost per product. */
@Service
public class InventoryReportService {
    public record CategoryRow(String category, long units, BigDecimal value) {}
    public record ProductRow(Long productId, String name, String category, Long supplierId, int stock,
                             int threshold, BigDecimal unitCost, BigDecimal value, boolean lowStock) {}
    public record View(BigDecimal totalValue, long totalUnits, long missingCostProducts, long lowStockCount,
                       Instant generatedAt, List<CategoryRow> categories, List<ProductRow> products) {}

    private static final String LATEST_COST_SQL = """
        SELECT x.product_id, x.unit_cost FROM (
          SELECT sri.product_id, sri.unit_cost,
                 ROW_NUMBER() OVER (PARTITION BY sri.product_id
                   ORDER BY sr.invoice_date DESC, sr.id DESC, sri.id DESC) AS rn
          FROM supplier_receipt_items sri
          JOIN supplier_receipts sr ON sr.id = sri.receipt_id) x
        WHERE x.rn = 1
        """;

    private static final String PRODUCT_SQL = """
        SELECT p.product_id, p.name, COALESCE(c.name,'') AS category, p.supplier_id,
               p.quantity_remaining, COALESCE(p.low_stock_threshold,:fallback) AS threshold
        FROM products p
        LEFT JOIN categories c ON c.category_id = p.category_id
        WHERE p.active = true
        ORDER BY p.name ASC, p.product_id ASC
        """;

    private final NamedParameterJdbcTemplate db;
    private final SettingsService settings;

    public InventoryReportService(NamedParameterJdbcTemplate db, SettingsService settings) {
        this.db = db;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public View report() {
        Map<Long, BigDecimal> costs = new HashMap<>();
        db.query(LATEST_COST_SQL, Map.of(), (org.springframework.jdbc.core.RowCallbackHandler) rs ->
            costs.put(rs.getLong("product_id"), rs.getBigDecimal("unit_cost")));

        int fallback = settings.getInt("inventory.lowStockDefault");
        List<ProductRow> products = new ArrayList<>();
        Map<String, BigDecimal> categoryValues = new TreeMap<>();
        Map<String, Long> categoryUnits = new TreeMap<>();
        BigDecimal totalValue = BigDecimal.ZERO;
        long totalUnits = 0;
        long missingCostProducts = 0;
        long lowStockCount = 0;

        for (var row : db.queryForList(PRODUCT_SQL, Map.of("fallback", fallback))) {
            long productId = ((Number) row.get("product_id")).longValue();
            String name = String.valueOf(row.get("name"));
            String category = row.get("category") == null ? "" : String.valueOf(row.get("category"));
            Object rawSupplier = row.get("supplier_id");
            Long supplierId = rawSupplier == null ? null : ((Number) rawSupplier).longValue();
            int stock = ((Number) row.get("quantity_remaining")).intValue();
            int threshold = ((Number) row.get("threshold")).intValue();
            BigDecimal unitCost = costs.get(productId);
            if (unitCost == null) {
                missingCostProducts++;
                unitCost = BigDecimal.ZERO.setScale(2);
            } else {
                unitCost = unitCost.setScale(2, RoundingMode.HALF_UP);
            }
            BigDecimal value = unitCost.multiply(BigDecimal.valueOf(stock)).setScale(2, RoundingMode.HALF_UP);
            boolean lowStock = stock <= threshold;
            if (lowStock) lowStockCount++;
            totalValue = totalValue.add(value);
            totalUnits += stock;
            String label = category.isBlank() ? "Chưa phân loại" : category;
            categoryValues.merge(label, value, BigDecimal::add);
            categoryUnits.merge(label, (long) stock, Long::sum);
            products.add(new ProductRow(productId, name, category, supplierId, stock, threshold,
                unitCost, value, lowStock));
        }

        List<CategoryRow> categories = categoryValues.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> new CategoryRow(entry.getKey(), categoryUnits.getOrDefault(entry.getKey(), 0L),
                entry.getValue().setScale(2, RoundingMode.HALF_UP)))
            .toList();
        return new View(totalValue.setScale(2, RoundingMode.HALF_UP), totalUnits, missingCostProducts,
            lowStockCount, Instant.now(), categories, products);
    }
}
