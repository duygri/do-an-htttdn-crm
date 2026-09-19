package com.htttdn.crm.repository;

import com.htttdn.crm.entity.StoreVoucher; import org.springframework.data.jpa.repository.*; import java.util.*;
public interface StoreVoucherRepository extends JpaRepository<StoreVoucher,Long> { Optional<StoreVoucher> findByCodeIgnoreCase(String code); }
