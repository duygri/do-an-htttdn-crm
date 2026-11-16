package com.htttdn.crm.service;

import com.htttdn.crm.entity.*;
import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.repository.StockMovementRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:stockledger;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop","crm.care-reminders.enabled=false"})
@Transactional
class StockLedgerIntegrationTest {
    @Autowired StockService stock;
    @Autowired StockMovementRepository movements;
    @Autowired EntityManager em;

    Product product(int quantity) {
        var category = new Category();
        category.setName("Category");
        em.persist(category);
        var product = new Product();
        product.setCategoryEntity(category);
        product.setName("Sản phẩm kho");
        product.setPrice(BigDecimal.TEN);
        product.setStock(quantity);
        em.persist(product);
        return product;
    }

    Order order(Product product, int quantity) {
        var customer = new User();
        customer.setEmail(UUID.randomUUID() + "@stock.test");
        customer.setFullName("Khách");
        customer.setPhone("0900000000");
        customer.setRole("CUSTOMER");
        customer.setPasswordHash("test-only");
        em.persist(customer);
        var order = new Order();
        order.setCustomer(customer);
        order.setOrderCode(Math.abs(UUID.randomUUID().getLeastSignificantBits() % 900000000000L));
        order.setStatus("PENDING");
        order.setPaymentMethod("COD");
        order.setPaymentStatus("COD");
        order.setTotalAmount(BigDecimal.valueOf(quantity * 10L));
        em.persist(order);
        var item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setUnitPrice(BigDecimal.TEN);
        order.getItems().add(item);
        em.persist(item);
        em.flush();
        return order;
    }

    List<StockMovement> ledger(Product product) {
        return movements.findAll().stream().filter(m -> m.getProduct().getId().equals(product.getId())).toList();
    }

    @Test void saleAndReleaseWriteTheLedgerExactlyOnce() {
        var product = product(10);
        var order = order(product, 3);
        stock.sell(order);
        assertEquals(7, product.getStock());
        var sale = ledger(product).getFirst();
        assertEquals("SALE", sale.getMovementType());
        assertEquals(-3, sale.getQuantity());
        assertEquals(10, sale.getStockBefore());
        assertEquals(7, sale.getStockAfter());
        assertEquals(order.getId(), sale.getReferenceId());
        assertEquals("ORDER", sale.getReferenceType());

        stock.release(order);
        assertEquals(10, product.getStock());
        var release = ledger(product).stream().filter(m -> m.getMovementType().equals("CANCEL_RELEASE")).findFirst().orElseThrow();
        assertEquals(3, release.getQuantity());
        assertEquals(7, release.getStockBefore());
        assertEquals(10, release.getStockAfter());

        stock.release(order);
        assertEquals(2, ledger(product).size());
    }

    @Test void adjustTracksDifferenceAndRejectsNegativeStock() {
        var product = product(10);
        stock.adjust(product, 3, "Kiểm tra");
        assertEquals(3, product.getStock());
        var adjust = ledger(product).getFirst();
        assertEquals("ADJUST", adjust.getMovementType());
        assertEquals(-7, adjust.getQuantity());
        assertEquals(10, adjust.getStockBefore());
        assertEquals(3, adjust.getStockAfter());

        stock.adjust(product, 3, "Không đổi");
        assertEquals(1, ledger(product).size());

        var invalid = assertThrows(ApiException.class, () -> stock.adjust(product, -1, "Âm"));
        assertEquals("STOCK_INVALID", invalid.code());
        assertEquals(3, product.getStock());

        var insufficient = assertThrows(ApiException.class, () -> stock.apply(product, -4, "ADJUST", "PRODUCT", product.getId(), "Vượt tồn"));
        assertEquals("INSUFFICIENT_STOCK", insufficient.code());
        assertEquals(3, product.getStock());
        assertEquals(1, ledger(product).size());
    }

    @Test void initialRecordsTheStartingQuantityWithoutChangingStock() {
        var product = product(6);
        stock.initial(product);
        assertEquals(6, product.getStock());
        var initial = ledger(product).getFirst();
        assertEquals("INITIAL", initial.getMovementType());
        assertEquals(6, initial.getQuantity());
        assertEquals(0, initial.getStockBefore());
        assertEquals(6, initial.getStockAfter());
    }
}
