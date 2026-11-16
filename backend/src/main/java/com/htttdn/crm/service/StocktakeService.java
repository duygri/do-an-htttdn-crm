package com.htttdn.crm.service;

import com.htttdn.crm.entity.Product;
import com.htttdn.crm.entity.Stocktake;
import com.htttdn.crm.entity.StocktakeItem;
import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.repository.ProductRepository;
import com.htttdn.crm.repository.StocktakeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class StocktakeService {
    public record Summary(Long id, String code, String status, int itemCount, int countedCount,
                          Instant createdAt, Instant completedAt) {}
    public record ItemView(Long id, Long productId, String productName, int systemQuantity,
                          Integer countedQuantity, Integer difference, String note) {}
    public record Detail(Long id, String code, String status, String note, Instant createdAt,
                         Instant completedAt, List<ItemView> items) {}
    public record CountRequest(Long itemId, Integer countedQuantity, String note) {}

    private static final int MAX_ITEMS = 2000;
    private final StocktakeRepository stocktakes;
    private final ProductRepository products;
    private final StockService stock;
    private final AuditTrail audit;

    public StocktakeService(StocktakeRepository stocktakes, ProductRepository products, StockService stock, AuditTrail audit) {
        this.stocktakes = stocktakes;
        this.products = products;
        this.stock = stock;
        this.audit = audit;
    }

    @Transactional
    public Detail create(AuditTrail.Actor actor, String note, List<Long> productIds) {
        requireActor(actor);
        if (note != null && note.length() > 1000)
            throw bad("INVALID_STOCKTAKE", "Ghi chú kiểm kê tối đa 1000 ký tự.");
        List<Product> chosen;
        if (productIds == null || productIds.isEmpty()) {
            var page = products.findAll((root, query, cb) -> cb.and(
                    cb.isTrue(root.get("active")), cb.greaterThan(root.get("stock"), 0)),
                PageRequest.of(0, MAX_ITEMS + 1, Sort.by("id")));
            if (page.getNumberOfElements() > MAX_ITEMS) throw tooLarge();
            chosen = page.getContent();
        } else {
            List<Long> ids = productIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
            if (ids.size() > MAX_ITEMS) throw tooLarge();
            var found = products.findAllById(ids);
            if (found.size() != ids.size())
                throw bad("INVALID_STOCKTAKE", "Có sản phẩm không tồn tại trong phiếu kiểm kê.");
            chosen = found.stream().sorted(Comparator.comparing(Product::getId)).toList();
        }
        if (chosen.isEmpty()) throw bad("INVALID_STOCKTAKE", "Không có sản phẩm nào để kiểm kê.");

        var stocktake = new Stocktake();
        stocktake.setNote(note);
        stocktake.setCreatedBy(actor.id());
        stocktake.setStatus("DRAFT");
        stocktake.setCreatedAt(Instant.now());
        for (Product product : chosen) {
            var item = new StocktakeItem();
            item.setStocktake(stocktake);
            item.setProduct(product);
            item.setSystemQuantity(product.getStock());
            stocktake.getItems().add(item);
        }
        stocktakes.saveAndFlush(stocktake);
        stocktake.setCode("KK-" + stocktake.getId());
        stocktakes.save(stocktake);
        return detailOf(stocktake);
    }

    @Transactional(readOnly = true)
    public Page<Summary> list(int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw bad("INVALID_PAGINATION", "Tham số phân trang không hợp lệ.");
        return stocktakes.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(page, size)).map(stocktake -> {
            var items = stocktake.getItems();
            int counted = (int) items.stream().filter(item -> item.getCountedQuantity() != null).count();
            return new Summary(stocktake.getId(), stocktake.getCode(), stocktake.getStatus(),
                items.size(), counted, stocktake.getCreatedAt(), stocktake.getCompletedAt());
        });
    }

    @Transactional(readOnly = true)
    public Detail detail(Long id) {
        return detailOf(load(id));
    }

    @Transactional
    public Detail updateCounts(Long id, List<CountRequest> requests) {
        var stocktake = load(id);
        requireDraft(stocktake);
        Map<Long, StocktakeItem> byId = new HashMap<>();
        stocktake.getItems().forEach(item -> byId.put(item.getId(), item));
        if (requests != null) for (CountRequest request : requests) {
            if (request == null || request.itemId() == null) continue;
            var item = byId.get(request.itemId());
            if (item == null) throw bad("INVALID_STOCKTAKE_ITEM", "Dòng kiểm kê không thuộc phiếu này.");
            if (request.countedQuantity() != null && request.countedQuantity() < 0)
                throw bad("INVALID_COUNT", "Số lượng kiểm kê không được âm.");
            if (request.note() != null && request.note().length() > 500)
                throw bad("INVALID_COUNT", "Ghi chú dòng kiểm kê tối đa 500 ký tự.");
            item.setCountedQuantity(request.countedQuantity());
            item.setDifference(request.countedQuantity() == null
                ? null : request.countedQuantity() - item.getSystemQuantity());
            if (request.note() != null) item.setNote(request.note());
        }
        stocktakes.save(stocktake);
        return detailOf(stocktake);
    }

    @Transactional
    public Detail complete(Long id, AuditTrail.Actor actor) {
        requireActor(actor);
        var stocktake = load(id);
        requireDraft(stocktake);
        if (stocktake.getItems().stream().anyMatch(item -> item.getCountedQuantity() == null))
            throw conflict("STOCKTAKE_INCOMPLETE", "Vui lòng nhập số đếm cho tất cả sản phẩm trước khi hoàn tất.");
        var sorted = stocktake.getItems().stream()
            .sorted(Comparator.comparing(item -> item.getProduct().getId())).toList();
        int adjustments = 0;
        for (StocktakeItem item : sorted) {
            var product = stock.lockProduct(item.getProduct().getId()).orElseThrow(() -> conflict(
                "STOCKTAKE_PRODUCT_MISSING", "Không tìm thấy sản phẩm trong phiếu kiểm kê."));
            int delta = item.getCountedQuantity() - product.getStock();
            if (delta == 0) continue;
            stock.apply(product, delta, "STOCKTAKE", "STOCKTAKE", stocktake.getId(),
                "Kiểm kê #" + stocktake.getCode());
            adjustments++;
        }
        stocktake.setStatus("COMPLETED");
        stocktake.setCompletedBy(actor.id());
        stocktake.setCompletedAt(Instant.now());
        stocktakes.save(stocktake);
        audit.recordAs(actor, "STOCKTAKE_COMPLETE", "STOCKTAKE", stocktake.getId(),
            Map.of("status", "DRAFT"), Map.of("status", "COMPLETED", "code", stocktake.getCode(),
                "adjustments", adjustments));
        return detailOf(stocktake);
    }

    @Transactional
    public Detail cancel(Long id, AuditTrail.Actor actor) {
        requireActor(actor);
        var stocktake = load(id);
        requireDraft(stocktake);
        stocktake.setStatus("CANCELLED");
        stocktakes.save(stocktake);
        audit.recordAs(actor, "STOCKTAKE_CANCEL", "STOCKTAKE", stocktake.getId(),
            Map.of("status", "DRAFT"), Map.of("status", "CANCELLED", "code", stocktake.getCode()));
        return detailOf(stocktake);
    }

    private Detail detailOf(Stocktake stocktake) {
        var items = stocktake.getItems().stream().map(item -> new ItemView(item.getId(),
            item.getProduct().getId(), item.getProduct().getName(), item.getSystemQuantity(),
            item.getCountedQuantity(), item.getDifference(), item.getNote())).toList();
        return new Detail(stocktake.getId(), stocktake.getCode(), stocktake.getStatus(),
            stocktake.getNote(), stocktake.getCreatedAt(), stocktake.getCompletedAt(), items);
    }

    private Stocktake load(Long id) {
        return stocktakes.findById(id).orElseThrow(() ->
            new ApiException(HttpStatus.NOT_FOUND, "STOCKTAKE_NOT_FOUND", "Không tìm thấy phiếu kiểm kê."));
    }

    private static void requireActor(AuditTrail.Actor actor) {
        if (actor == null) throw new IllegalStateException("Verified audit actor required");
    }

    private static void requireDraft(Stocktake stocktake) {
        if (!"DRAFT".equals(stocktake.getStatus()))
            throw conflict("STOCKTAKE_NOT_DRAFT", "Chỉ phiếu đang mở mới được cập nhật.");
    }

    private static ApiException tooLarge() {
        return bad("STOCKTAKE_TOO_LARGE", "Phiếu kiểm kê tối đa " + MAX_ITEMS + " sản phẩm.");
    }

    private static ApiException bad(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
