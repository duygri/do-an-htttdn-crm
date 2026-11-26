package com.htttdn.crm.repository;

import com.htttdn.crm.entity.PointTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {
    @Query("select coalesce(sum(p.points),0) from PointTransaction p where p.customer.id=:customerId")
    long balance(@Param("customerId") Long customerId);

    java.util.Optional<PointTransaction> findByCustomerIdAndRequestKey(Long customerId, String requestKey);

    boolean existsByOrderIdAndTxnType(Long orderId, String txnType);

    List<PointTransaction> findTop50ByCustomerIdOrderByCreatedAtDescIdDesc(Long customerId);
}
