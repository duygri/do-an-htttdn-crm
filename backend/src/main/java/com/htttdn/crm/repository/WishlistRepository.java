package com.htttdn.crm.repository;

import com.htttdn.crm.entity.WishlistItem; import org.springframework.data.jpa.repository.*; import java.util.*;
public interface WishlistRepository extends JpaRepository<WishlistItem,Long> {
    List<WishlistItem> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    Optional<WishlistItem> findByCustomerIdAndProductId(Long customerId,Long productId);
    boolean existsByCustomerIdAndProductId(Long customerId,Long productId);
}
