package com.htttdn.crm.service;

import com.htttdn.crm.entity.Category;
import com.htttdn.crm.entity.Product;
import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.repository.AuditLogRepository;
import com.htttdn.crm.repository.StockMovementRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:stocktake;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop","crm.care-reminders.enabled=false","app.notices.enabled=false"})
@Transactional
class StocktakeIntegrationTest {
    @Autowired StocktakeService stocktakes;
    @Autowired EntityManager em;
    @Autowired StockMovementRepository movements;
    @Autowired AuditLogRepository auditLogs;

    final AuditTrail.Actor actor = new AuditTrail.Actor(1L, "Quản lý kho", "MANAGER");

    Product product(String name, int stock, boolean active) {
        var category = new Category();
        category.setName("Áo");
        em.persist(category);
        var product = new Product();
        product.setCategoryEntity(category);
        product.setName(name);
        product.setPrice(BigDecimal.TEN);
        product.setStock(stock);
        product.setActive(active);
        em.persist(product);
        em.flush();
        return product;
    }

    @Test void completesWithCountedStockAndOneAdjustmentPerDifference() {
        var first = product("Áo đỏ", 10, true);
        var second = product("Áo xanh", 4, true);
        var created = stocktakes.create(actor, "Kiểm kê tháng 11", List.of(second.getId(), first.getId()));
        assertTrue(created.code().matches("KK-\\d+"));
        assertEquals("DRAFT", created.status());
        assertEquals(2, created.items().size());
        assertEquals(10, created.items().get(0).systemQuantity());
        assertEquals(4, created.items().get(1).systemQuantity());

        var counted = stocktakes.updateCounts(created.id(), List.of(
            new StocktakeService.CountRequest(created.items().get(0).id(), 7, "Mất một chiếc"),
            new StocktakeService.CountRequest(created.items().get(1).id(), 4, "")));
        assertEquals(-3, counted.items().get(0).difference());
        assertEquals(0, counted.items().get(1).difference());

        var completed = stocktakes.complete(created.id(), actor);
        assertEquals("COMPLETED", completed.status());
        assertNotNull(completed.completedAt());
        assertEquals(7, em.find(Product.class, first.getId()).getStock());
        assertEquals(4, em.find(Product.class, second.getId()).getStock());
        var stocktakeMovements = movements.findAll().stream()
            .filter(movement -> "STOCKTAKE".equals(movement.getMovementType())).toList();
        assertEquals(1, stocktakeMovements.size());
        assertEquals(-3, stocktakeMovements.getFirst().getQuantity());
        assertEquals(10, stocktakeMovements.getFirst().getStockBefore());
        assertEquals(7, stocktakeMovements.getFirst().getStockAfter());
        assertEquals("STOCKTAKE", stocktakeMovements.getFirst().getReferenceType());
        assertTrue(auditLogs.findAll().stream().anyMatch(log -> "STOCKTAKE_COMPLETE".equals(log.getAction())));

        assertEquals("STOCKTAKE_NOT_DRAFT", assertThrows(ApiException.class,
            () -> stocktakes.complete(created.id(), actor)).code());
        assertEquals("STOCKTAKE_NOT_DRAFT", assertThrows(ApiException.class,
            () -> stocktakes.updateCounts(created.id(), List.of())).code());
    }

    @Test void refusesToCompleteWhileAnyLineIsUncounted() {
        var product = product("Áo trắng", 8, true);
        var created = stocktakes.create(actor, null, List.of(product.getId()));
        assertEquals("STOCKTAKE_INCOMPLETE", assertThrows(ApiException.class,
            () -> stocktakes.complete(created.id(), actor)).code());
        assertEquals("DRAFT", stocktakes.detail(created.id()).status());
        assertEquals(8, em.find(Product.class, product.getId()).getStock());
        assertTrue(movements.findAll().isEmpty());
    }

    @Test void cancellingADraftNeverTouchesStock() {
        var product = product("Áo vàng", 6, true);
        var created = stocktakes.create(actor, "Kiểm tra đột xuất", List.of(product.getId()));
        stocktakes.updateCounts(created.id(), List.of(
            new StocktakeService.CountRequest(created.items().get(0).id(), 2, "")));
        var cancelled = stocktakes.cancel(created.id(), actor);
        assertEquals("CANCELLED", cancelled.status());
        assertEquals(6, em.find(Product.class, product.getId()).getStock());
        assertTrue(movements.findAll().isEmpty());
        assertTrue(auditLogs.findAll().stream().anyMatch(log -> "STOCKTAKE_CANCEL".equals(log.getAction())));
        assertEquals("STOCKTAKE_NOT_DRAFT", assertThrows(ApiException.class,
            () -> stocktakes.complete(created.id(), actor)).code());
    }

    @Test void emptyProductListCapturesActiveProductsWithStock() {
        var inStock = product("Áo xám", 3, true);
        product("Áo hết hàng", 0, true);
        product("Áo ngừng bán", 5, false);
        var created = stocktakes.create(actor, null, List.of());
        assertEquals(1, created.items().size());
        assertEquals(inStock.getId(), created.items().getFirst().productId());
        assertEquals(3, created.items().getFirst().systemQuantity());
    }

    @Test void rejectsForeignItemsNegativeCountsAndOtherStatuses() {
        var product = product("Áo tím", 5, true);
        var created = stocktakes.create(actor, null, List.of(product.getId()));
        var itemId = created.items().getFirst().id();
        assertEquals("INVALID_STOCKTAKE_ITEM", assertThrows(ApiException.class,
            () -> stocktakes.updateCounts(created.id(), List.of(
                new StocktakeService.CountRequest(itemId + 999, 1, null)))).code());
        assertEquals("INVALID_COUNT", assertThrows(ApiException.class,
            () -> stocktakes.updateCounts(created.id(), List.of(
                new StocktakeService.CountRequest(itemId, -1, null)))).code());
        assertEquals("STOCKTAKE_NOT_FOUND", assertThrows(ApiException.class,
            () -> stocktakes.detail(999_999L)).code());
        assertThrows(IllegalStateException.class, () -> stocktakes.create(null, null, List.of(product.getId())));
    }

    @Test void listsNewestFirstWithCountedProgress() {
        var product = product("Áo nâu", 5, true);
        var older = stocktakes.create(actor, "Phiếu cũ", List.of(product.getId()));
        stocktakes.updateCounts(older.id(), List.of(
            new StocktakeService.CountRequest(older.items().getFirst().id(), 5, null)));
        stocktakes.create(actor, "Phiếu mới", List.of(product.getId()));
        var page = stocktakes.list(0, 10);
        assertEquals(2, page.getTotalElements());
        assertEquals("DRAFT", page.getContent().getFirst().status());
        assertEquals(1, page.getContent().getFirst().itemCount());
        assertEquals(0, page.getContent().getFirst().countedCount());
        assertTrue(page.getContent().getFirst().id() > older.id());
        assertEquals(1, page.getContent().get(1).countedCount());
        assertEquals("INVALID_PAGINATION", assertThrows(ApiException.class,
            () -> stocktakes.list(-1, 10)).code());
    }
}
