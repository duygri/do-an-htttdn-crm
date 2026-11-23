package com.htttdn.crm.repository;

import com.htttdn.crm.entity.OrderRefund;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface OrderRefundRepository extends JpaRepository<OrderRefund,Long> {
    List<OrderRefund> findByOrderIdOrderByIdAsc(Long orderId);
    Optional<OrderRefund> findByOrderIdAndRequestKey(Long orderId,String requestKey);
}
