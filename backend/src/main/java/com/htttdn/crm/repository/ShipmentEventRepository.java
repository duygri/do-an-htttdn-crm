package com.htttdn.crm.repository;

import com.htttdn.crm.entity.ShipmentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ShipmentEventRepository extends JpaRepository<ShipmentEvent,Long> {
    List<ShipmentEvent> findByShipmentIdOrderByIdAsc(Long shipmentId);
}
