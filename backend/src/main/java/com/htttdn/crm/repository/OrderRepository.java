package com.htttdn.crm.repository;

import com.htttdn.crm.entity.*; import org.springframework.data.jpa.repository.*; import org.springframework.data.domain.*; import org.springframework.data.repository.query.Param; import java.util.*;

public interface OrderRepository extends JpaRepository<Order,Long> {
    Page<Order> findByStatus(String status,Pageable p); long countByStatus(String status);
    Page<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId,Pageable p);
    Optional<Order> findByIdAndCustomerId(Long id,Long customerId); Optional<Order> findByOrderCode(Long orderCode);
    @Query("select case when count(o)>0 then true else false end from Order o join o.items i where o.customer.id=:customerId and i.product.id=:productId and o.status in :statuses")
    boolean existsEligiblePurchase(@Param("customerId") Long customerId,@Param("productId") Long productId,@Param("statuses") Collection<String> statuses);
    @Query("select o from Order o where o.status='PENDING_PAYMENT' and o.expiresAt is not null and o.expiresAt<:now") List<Order> findExpiredPending(@Param("now") java.time.Instant now);
    @Query("select coalesce(sum(o.totalAmount),0) from Order o where o.status <> 'CANCELLED'") java.math.BigDecimal revenue();
}
