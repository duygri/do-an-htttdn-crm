package com.htttdn.crm.repository;

import com.htttdn.crm.entity.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
    Optional<PaymentAttempt> findByProviderOrderCode(Long providerOrderCode);
    Optional<PaymentAttempt> findFirstByOrderIdOrderByIdDesc(Long orderId);
    boolean existsByProviderOrderCode(Long providerOrderCode);
}
