package com.htttdn.crm.controller;

import com.htttdn.crm.entity.Product;
import com.htttdn.crm.service.AuditTrail;
import com.htttdn.crm.service.InventoryQueryService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/manager")
public class ManagerInventoryController {
    private final InventoryQueryService inventory;

    public ManagerInventoryController(InventoryQueryService inventory) {
        this.inventory = inventory;
    }

    public record ThresholdRequest(Integer threshold) {}

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
}
