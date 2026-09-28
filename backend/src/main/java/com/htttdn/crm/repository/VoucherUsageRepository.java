package com.htttdn.crm.repository;
import com.htttdn.crm.entity.VoucherUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface VoucherUsageRepository extends JpaRepository<VoucherUsage,Long> {
    Optional<VoucherUsage> findByOrderId(Long orderId);
}
