package com.htttdn.crm.service;

import com.htttdn.crm.dto.admin.AdminDtos.StatusRequest;
import com.htttdn.crm.entity.Order;
import com.htttdn.crm.repository.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Set;

@Service
public class AdminOrderService extends AdminServiceSupport {
    private static final Set<String> STATUSES = Set.of("PENDING_PAYMENT", "CONFIRMED", "SHIPPED", "DELIVERED", "COMPLETED", "CANCELLED");
    private final OrderRepository orders;
    public AdminOrderService(OrderRepository orders) { this.orders = orders; }
    public Page<Order> list(String status, int page, int size) { return status == null ? orders.findAll(page(page, size)) : orders.findByStatus(status, page(page, size)); }
    public Order get(Long id) { return orders.findById(id).orElseThrow(); }
    @Transactional public Order updateStatus(Long id, StatusRequest request) { if (!STATUSES.contains(request.status())) throw new IllegalArgumentException("Invalid order status"); Order order = get(id); order.setStatus(request.status()); order.setUpdatedAt(Instant.now()); return orders.save(order); }
}
