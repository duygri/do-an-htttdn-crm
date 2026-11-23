package com.htttdn.crm.repository;

import com.htttdn.crm.entity.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ShipmentRepository extends JpaRepository<Shipment,Long> {
    Optional<Shipment> findByOrderId(Long orderId);
    Optional<Shipment> findByOrderIdAndTrackingCode(Long orderId,String trackingCode);
}
