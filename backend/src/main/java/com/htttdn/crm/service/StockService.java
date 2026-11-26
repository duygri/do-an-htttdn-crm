package com.htttdn.crm.service;

import com.htttdn.crm.entity.Order;
import com.htttdn.crm.entity.OrderItem;
import com.htttdn.crm.entity.OrderReturnItem;
import com.htttdn.crm.entity.Product;
import com.htttdn.crm.entity.StockMovement;
import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.repository.OrderReturnItemRepository;
import com.htttdn.crm.repository.ProductRepository;
import com.htttdn.crm.repository.StockMovementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
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
    private final InternalNoticeService notices;
    private final OrderReturnItemRepository returnItems;

    /** Production wiring (explicit so the Spring-injected and the legacy unit-test constructor can coexist). */
    @Autowired
    public StockService(ProductRepository products, EntityManager em, StockMovementRepository movements, InternalNoticeService notices, OrderReturnItemRepository returnItems) {
        this.products = products;
        this.em = em;
        this.movements = movements;
        this.notices = notices;
        this.returnItems = returnItems;
    }

    /** Legacy 4-arg constructor kept for unit tests; restockReturn fails loudly through it because there is no repository. */
    public StockService(ProductRepository products, EntityManager em, StockMovementRepository movements, InternalNoticeService notices) {
        this(products, em, movements, notices, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Product> lockProduct(Long id) {
        var product = products.findLockedById(id);
        // Orders/carts may have loaded this product before waiting for its lock.
        // Refresh after the lock so a managed stale stock value cannot overwrite a committed update.
        product.ifPresent(p -> {em.refresh(p, LockModeType.PESSIMISTIC_WRITE);p.getVariants().forEach(em::refresh);});
        return product;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void apply(Product product, int delta, String type, String referenceType, Long referenceId, String note) {
        if(product.isVariantsAllocated()) throw new ApiException(HttpStatus.CONFLICT,"VARIANT_REQUIRED","Vui lòng chọn biến thể để điều chỉnh tồn kho.");
        applyCommon(product, delta, type, referenceType, referenceId, note, null);
    }

    private void applyCommon(Product product, int delta, String type, String referenceType, Long referenceId, String note, Long variantId) {
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
        product.setStockVersion(Math.incrementExact(product.getStockVersion()));
        product.setUpdatedAt(Instant.now());
        products.save(product);
        var movement = new StockMovement();
        movement.setProduct(product);
        movement.setVariantId(variantId);
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
        if(variantId==null)notices.notifyLowStock(product, before, after);
    }

    public com.htttdn.crm.entity.ProductVariant resolve(Product p, String size, String color) {
        if(!p.isVariantsAllocated()) return null;
        return p.getVariants().stream().filter(v -> v.isActive() && size!=null && color!=null && v.getSize().equalsIgnoreCase(size.trim()) && v.getColor().equalsIgnoreCase(color.trim())).findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,"INVALID_VARIANT","Biến thể không hợp lệ."));
    }
    private Long restorationVariant(Product p, OrderItem line){
        if(line.getVariantId()!=null || !p.isVariantsAllocated())return line.getVariantId();
        var matches=p.getVariants().stream().filter(v->line.getSize()!=null&&line.getColor()!=null&&v.getSize().equalsIgnoreCase(line.getSize().trim())&&v.getColor().equalsIgnoreCase(line.getColor().trim())).toList();
        if(matches.size()!=1)throw new ApiException(HttpStatus.CONFLICT,"LEGACY_VARIANT_UNRESOLVED","Đơn cũ cần quản lý đối soát màu/size trước khi hoàn kho.");
        return matches.getFirst().getId();
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void applyVariant(Product p, Long variantId, int delta, String type, String ref, Long id, String note) {
        if(variantId==null){apply(p,delta,type,ref,id,note);return;}
        var v=p.getVariants().stream().filter(x->x.getId().equals(variantId)).findFirst()
            .orElseThrow(()->new ApiException(HttpStatus.CONFLICT,"INVALID_VARIANT","Biến thể không thuộc sản phẩm."));
        long after=(long)v.getStock()+delta;
        if(after<0 || after>Integer.MAX_VALUE) throw new ApiException(HttpStatus.CONFLICT,"INSUFFICIENT_STOCK","Biến thể không đủ tồn kho.");
        int before=v.getStock();
        v.setStock((int)after);
        applyCommon(p,delta,type,ref,id,note,variantId);
        notices.notifyVariantLowStock(p,v,before,(int)after);
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void sell(Order order) {
        order.getItems().forEach(item -> applyVariant(item.getProduct(), item.getVariantId(), -item.getQuantity(), "SALE", "ORDER",
            order.getId(), "Bán đơn #" + order.getOrderCode()));
    }

    // Caller holds the order write lock until the enclosing transaction commits.
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(Order order) {
        if (order.isStockReleased()) return;
        var sorted=order.getItems().stream().sorted(java.util.Comparator.comparing(i->i.getProduct().getId())).toList();
        Map<Long,Product> locked=new TreeMap<>();
        for(var item:sorted) if(!locked.containsKey(item.getProduct().getId())) locked.put(item.getProduct().getId(),lockProduct(item.getProduct().getId()).orElseThrow(()->new ApiException(HttpStatus.CONFLICT,"STOCK_RELEASE_FAILED","Không tìm thấy sản phẩm để hoàn kho.")));
        for(var entry:locked.entrySet()) {
            Map<Long,Integer> grouped=new TreeMap<>();
            for(var item:sorted)if(item.getProduct().getId().equals(entry.getKey())){
                Long variant=restorationVariant(entry.getValue(),item);
                grouped.merge(variant==null?0L:variant,item.getQuantity(),Math::addExact);
            }
            grouped.forEach((variant,quantity)->applyVariant(entry.getValue(),variant==0?null:variant,quantity,"CANCEL_RELEASE","ORDER",order.getId(),"Hủy đơn #"+order.getOrderCode()));
        }
        order.setStockReleased(true);
    }

    // Opt-in restock of returned lines; restocked_at marks each line so a replay never doubles stock.
    @Transactional(propagation = Propagation.MANDATORY)
    public void restockReturn(Order order, List<OrderReturnItem> items) {
        // Fail before any stock/ledger mutation so an unwired service can never silently skip the restock.
        if (returnItems == null) throw new IllegalStateException("OrderReturnItemRepository chưa được cấu hình.");
        items.stream().filter(i->i.getRestockedAt()==null).map(i->order.getItems().stream().filter(l->l.getId().equals(i.getOrderItemId())).findFirst().orElseThrow().getProduct().getId()).distinct().sorted().forEach(this::lockProduct);
        Map<Long,Product> returnProducts=new TreeMap<>();
        for (OrderReturnItem item : items) {
            if (item.getRestockedAt() != null) continue;
            OrderItem line = order.getItems().stream().filter(i -> i.getId() != null && i.getId().equals(item.getOrderItemId())).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "RETURN_ITEM_NOT_FOUND", "Không tìm thấy sản phẩm đổi trả trong đơn."));
            Product product = returnProducts.computeIfAbsent(line.getProduct().getId(), key -> lockProduct(key).orElseThrow(() -> new ApiException(
                HttpStatus.CONFLICT, "STOCK_RELEASE_FAILED", "Không tìm thấy sản phẩm để nhập kho.")));
            applyVariant(product, restorationVariant(product,line), item.getQuantity(), "RETURN_RESTOCK", "ORDER", order.getId(), "Nhập kho đổi trả đơn #" + order.getOrderCode());
            item.setRestockedAt(Instant.now());
            returnItems.save(item);
        }
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
        movement.setVariantId(null);
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
