package com.htttdn.crm.repository;

import com.htttdn.crm.entity.OrderReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrderReturnItemRepository extends JpaRepository<OrderReturnItem,Long> {
    List<OrderReturnItem> findByOrderIdOrderByIdAsc(Long orderId);
}
