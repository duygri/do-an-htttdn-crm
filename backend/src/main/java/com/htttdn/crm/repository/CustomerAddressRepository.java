package com.htttdn.crm.repository;

import com.htttdn.crm.entity.CustomerAddress; import org.springframework.data.jpa.repository.*; import java.util.*;
public interface CustomerAddressRepository extends JpaRepository<CustomerAddress,Long> { List<CustomerAddress> findByCustomerIdOrderByDefaultAddressDescUpdatedAtDesc(Long customerId); Optional<CustomerAddress> findByIdAndCustomerId(Long id,Long customerId); List<CustomerAddress> findByCustomerId(Long customerId); }
