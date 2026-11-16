package com.htttdn.crm.service;

import com.htttdn.crm.entity.Product;
import com.htttdn.crm.entity.StockMovement;
import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.repository.ProductRepository;
import com.htttdn.crm.repository.StockMovementRepository;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/** Read models for the stock ledger and low-stock alerts. */
@Service
public class InventoryQueryService {
    private static final Set<String> TYPES = Set.of("SALE", "CANCEL_RELEASE", "ADJUST", "INITIAL", "STOCKTAKE");

    private final StockMovementRepository movements;
    private final ProductRepository products;
    private final NamedParameterJdbcTemplate db;
    private final SettingsService settings;
    private final StockService stock;
    private final AuditTrail audit;

    public InventoryQueryService(StockMovementRepository movements, ProductRepository products,
                                 NamedParameterJdbcTemplate db, SettingsService settings,
                                 StockService stock, AuditTrail audit) {
        this.movements = movements;
        this.products = products;
        this.db = db;
        this.settings = settings;
        this.stock = stock;
        this.audit = audit;
    }

    public record MovementView(Long id, Long productId, String productName, String type, int quantity,
                               int stockBefore, int stockAfter, String referenceType, Long referenceId,
                               String note, String actorName, Instant createdAt) {}

    public record LowStockRow(Long productId, String name, String category, Long supplierId, int stock,
                              int threshold, Instant updatedAt) {}

    @Transactional(readOnly = true)
    public Page<MovementView> movements(Long productId, String type, Instant from, Instant to, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw bad();
        String normalized = type == null || type.isBlank() ? null : type.trim().toUpperCase(Locale.ROOT);
        if (normalized != null && !TYPES.contains(normalized)) throw bad();
        if (from != null && to != null && from.isAfter(to)) throw bad();
        Specification<StockMovement> spec = (root, query, cb) -> {
            if (query.getResultType() != Long.class) root.fetch("product", JoinType.LEFT);
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (productId != null) predicates.add(cb.equal(root.get("product").get("id"), productId));
            if (normalized != null) predicates.add(cb.equal(root.get("movementType"), normalized));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var pageable = PageRequest.of(page, size,
            Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return movements.findAll(spec, pageable).map(this::view);
    }

    @Transactional(readOnly = true)
    public Page<LowStockRow> lowStock(Long supplierId, String category, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw bad();
        int fallback = settings.getInt("inventory.lowStockDefault");
        Map<String, Object> args = new HashMap<>();
        args.put("fallback", fallback);
        StringBuilder where = new StringBuilder(
            " WHERE p.active=true AND p.quantity_remaining <= COALESCE(p.low_stock_threshold,:fallback)");
        if (supplierId != null) {
            where.append(" AND p.supplier_id=:supplierId");
            args.put("supplierId", supplierId);
        }
        if (category != null && !category.isBlank()) {
            where.append(" AND c.name=:category");
            args.put("category", category.trim());
        }
        String from = " FROM products p LEFT JOIN categories c ON c.category_id=p.category_id";
        Long total = db.queryForObject("SELECT COUNT(*)" + from + where, args, Long.class);
        args.put("limit", size);
        args.put("offset", (long) page * size);
        String select = "SELECT p.product_id,p.name,COALESCE(c.name,'') AS category,p.supplier_id,"
            + "p.quantity_remaining,COALESCE(p.low_stock_threshold,:fallback) AS threshold,p.updated_at"
            + from + where + " ORDER BY p.quantity_remaining ASC,p.product_id ASC LIMIT :limit OFFSET :offset";
        List<LowStockRow> content = db.query(select, args, (rs, rowNum) -> new LowStockRow(
            rs.getLong("product_id"), rs.getString("name"), rs.getString("category"),
            rs.getObject("supplier_id") == null ? null : rs.getLong("supplier_id"),
            rs.getInt("quantity_remaining"), rs.getInt("threshold"),
            rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toInstant()));
        return new PageImpl<>(content, PageRequest.of(page, size), total == null ? 0 : total);
    }

    @Transactional
    public Product updateThreshold(Long productId, Integer threshold, AuditTrail.Actor actor) {
        if (actor == null) throw new IllegalStateException("Verified audit actor required");
        if (threshold != null && (threshold < 0 || threshold > 100000))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_THRESHOLD", "Ngưỡng tồn thấp phải từ 0 đến 100000.");
        Product product = stock.lockProduct(productId).orElseThrow(() -> new ApiException(
            HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm."));
        Integer before = product.getLowStockThreshold();
        product.setLowStockThreshold(threshold);
        product.setUpdatedAt(Instant.now());
        // The low-stock read model uses JDBC, so flush before any subsequent query in this transaction.
        products.saveAndFlush(product);
        if (!Objects.equals(before, threshold))
            audit.recordAs(actor, "STOCK_THRESHOLD", "PRODUCT", productId,
                Map.of("threshold", before == null ? "" : before),
                Map.of("threshold", threshold == null ? "" : threshold));
        return product;
    }

    private MovementView view(StockMovement m) {
        return new MovementView(m.getId(), m.getProduct().getId(), m.getProduct().getName(), m.getMovementType(),
            m.getQuantity(), m.getStockBefore(), m.getStockAfter(), m.getReferenceType(), m.getReferenceId(),
            m.getNote(), m.getActorName(), m.getCreatedAt());
    }

    private static ApiException bad() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILTER", "Bộ lọc hoặc phân trang không hợp lệ.");
    }
}
