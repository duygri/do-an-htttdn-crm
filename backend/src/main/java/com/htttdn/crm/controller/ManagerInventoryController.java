package com.htttdn.crm.controller;

import com.htttdn.crm.entity.Product;
import com.htttdn.crm.service.AuditTrail;
import com.htttdn.crm.service.InventoryQueryService;
import com.htttdn.crm.service.InventoryReportService;
import com.htttdn.crm.service.StocktakeService;
import com.htttdn.crm.util.Csv;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/manager")
public class ManagerInventoryController {
    private final InventoryQueryService inventory;
    private final StocktakeService stocktakes;
    private final InventoryReportService reports;

    public ManagerInventoryController(InventoryQueryService inventory, StocktakeService stocktakes,
                                      InventoryReportService reports) {
        this.inventory = inventory;
        this.stocktakes = stocktakes;
        this.reports = reports;
    }

    public record ThresholdRequest(Integer threshold) {}
    public record StocktakeRequest(String note, List<Long> productIds) {}

    @GetMapping("/inventory/movements")
    public Page<InventoryQueryService.MovementView> movements(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return inventory.movements(productId, type, from, to, page, size);
    }

    @GetMapping("/inventory/low-stock")
    public Page<InventoryQueryService.LowStockRow> lowStock(
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return inventory.lowStock(supplierId, category, page, size);
    }

    @PatchMapping("/products/{id}/stock-threshold")
    public Product updateThreshold(@PathVariable long id, @RequestBody ThresholdRequest request,
            @RequestAttribute(AuditTrail.ACTOR_ATTRIBUTE) AuditTrail.Actor actor) {
        return inventory.updateThreshold(id, request == null ? null : request.threshold(), actor);
    }

    @GetMapping("/reports/inventory")
    public InventoryReportService.View inventoryReport() {
        return reports.report();
    }

    @GetMapping("/inventory/movements.csv")
    public ResponseEntity<String> movementsCsv(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        List<String> rows = new java.util.ArrayList<>();
        rows.add(Csv.line("id", "createdAt", "productId", "productName", "type", "quantity",
            "stockBefore", "stockAfter", "actor", "note", "variantId", "size", "color"));
        inventory.exportMovements(productId, type, from, to).forEach(movement -> rows.add(Csv.line(
            movement.id(), movement.createdAt(), movement.productId(), movement.productName(), movement.type(),
            movement.quantity(), movement.stockBefore(), movement.stockAfter(), movement.actorName(), movement.note(), movement.variantId(), movement.size(), movement.color())));
        return Csv.response("the-kho.csv", rows);
    }

    @GetMapping("/reports/inventory.csv")
    public ResponseEntity<String> inventoryCsv() {
        List<String> rows = new java.util.ArrayList<>();
        rows.add(Csv.line("productId", "name", "category", "stock", "unitCost", "value"));
        reports.report().products().forEach(product -> rows.add(Csv.line(product.productId(), product.name(),
            product.category(), product.stock(), product.unitCost(), product.value())));
        return Csv.response("ton-kho.csv", rows);
    }

    @PostMapping("/stocktakes")
    public ResponseEntity<StocktakeService.Detail> createStocktake(@RequestBody(required = false) StocktakeRequest request,
            @RequestAttribute(AuditTrail.ACTOR_ATTRIBUTE) AuditTrail.Actor actor) {
        var created = stocktakes.create(actor, request == null ? null : request.note(),
            request == null ? null : request.productIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/stocktakes")
    public Page<StocktakeService.Summary> stocktakeList(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return stocktakes.list(page, size);
    }

    @GetMapping("/stocktakes/{id}")
    public StocktakeService.Detail stocktake(@PathVariable Long id) {
        return stocktakes.detail(id);
    }

    @PatchMapping("/stocktakes/{id}/items")
    public StocktakeService.Detail updateCounts(@PathVariable Long id,
            @RequestBody List<StocktakeService.CountRequest> counts) {
        return stocktakes.updateCounts(id, counts);
    }

    @PostMapping("/stocktakes/{id}/complete")
    public StocktakeService.Detail completeStocktake(@PathVariable Long id,
            @RequestAttribute(AuditTrail.ACTOR_ATTRIBUTE) AuditTrail.Actor actor) {
        return stocktakes.complete(id, actor);
    }

    @PostMapping("/stocktakes/{id}/cancel")
    public StocktakeService.Detail cancelStocktake(@PathVariable Long id,
            @RequestAttribute(AuditTrail.ACTOR_ATTRIBUTE) AuditTrail.Actor actor) {
        return stocktakes.cancel(id, actor);
    }
}
