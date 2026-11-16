package com.htttdn.crm.service;

import com.htttdn.crm.entity.Order;
import com.htttdn.crm.entity.Product;
import com.htttdn.crm.entity.StockMovement;
import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.repository.ProductRepository;
import com.htttdn.crm.repository.StockMovementRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.Optional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

/** Single entry point for every change to products.quantity_remaining; each change writes a ledger row. */
@Service
public class StockService {
    private final ProductRepository products;
    private final EntityManager em;
    private final StockMovementRepository movements;

    public StockService(ProductRepository products, EntityManager em, StockMovementRepository movements) {
        this.products = products;
        this.em = em;
        this.movements = movements;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Product> lockProduct(Long id) {
        var product = products.findLockedById(id);
        // Orders/carts may have loaded this product before waiting for its lock.
        // Refresh after the lock so a managed stale stock value cannot overwrite a committed update.
        product.ifPresent(p -> em.refresh(p, LockModeType.PESSIMISTIC_WRITE));
        return product;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void apply(Product product, int delta, String type, String referenceType, Long referenceId, String note) {
        int before = product.getStock();
        int after;
        try {
            after = Math.addExact(before, delta);
        } catch (ArithmeticException e) {
            throw new ApiException(HttpStatus.CONFLICT, "STOCK_INVALID", "Số lượng tồn không hợp lệ.");
        }
        if (after < 0)
            throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "Sản phẩm " + product.getName() + " không đủ tồn kho.");
        product.setStock(after);
        product.setUpdatedAt(Instant.now());
        products.save(product);
        var movement = new StockMovement();
        movement.setProduct(product);
        movement.setMovementType(type);
        movement.setQuantity(delta);
        movement.setStockBefore(before);
        movement.setStockAfter(after);
        movement.setReferenceType(referenceType);
        movement.setReferenceId(referenceId);
        movement.setNote(note);
        var actor = AuditTrail.actor();
        if (actor != null) {
            movement.setActorId(actor.id());
            movement.setActorName(actor.name());
        }
        movements.save(movement);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void sell(Order order) {
        order.getItems().forEach(item -> apply(item.getProduct(), -item.getQuantity(), "SALE", "ORDER",
            order.getId(), "Bán đơn #" + order.getOrderCode()));
    }

    // Caller holds the order write lock until the enclosing transaction commits.
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(Order order) {
        if (order.isStockReleased()) return;
        Map<Long, Integer> quantities = new TreeMap<>();
        order.getItems().forEach(item -> quantities.merge(item.getProduct().getId(), item.getQuantity(), Math::addExact));
        quantities.forEach((id, quantity) -> {
            Product product = lockProduct(id).orElseThrow(() -> new ApiException(
                HttpStatus.CONFLICT, "STOCK_RELEASE_FAILED", "Không tìm thấy sản phẩm để hoàn kho. Vui lòng kiểm tra đơn hàng."));
            apply(product, quantity, "CANCEL_RELEASE", "ORDER", order.getId(), "Hủy đơn #" + order.getOrderCode());
        });
        order.setStockReleased(true);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void adjust(Product product, int newQuantity, String note) {
        if (newQuantity < 0)
            throw new ApiException(HttpStatus.BAD_REQUEST, "STOCK_INVALID", "Số lượng tồn không được âm.");
        int delta = newQuantity - product.getStock();
        if (delta == 0) return;
        apply(product, delta, "ADJUST", "PRODUCT", product.getId(), note);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void initial(Product product) {
        var movement = new StockMovement();
        movement.setProduct(product);
        movement.setMovementType("INITIAL");
        movement.setQuantity(product.getStock());
        movement.setStockBefore(0);
        movement.setStockAfter(product.getStock());
        movement.setReferenceType("PRODUCT");
        movement.setReferenceId(product.getId());
        movement.setNote("Tồn kho khởi tạo");
        var actor = AuditTrail.actor();
        if (actor != null) {
            movement.setActorId(actor.id());
            movement.setActorName(actor.name());
        }
        movements.save(movement);
    }
}
