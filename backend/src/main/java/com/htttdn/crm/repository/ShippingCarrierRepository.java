package com.htttdn.crm.repository;

import com.htttdn.crm.entity.ShippingCarrier;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ShippingCarrierRepository extends JpaRepository<ShippingCarrier,String> {
    List<ShippingCarrier> findByActiveTrueOrderBySortOrderAscCodeAsc();
}
