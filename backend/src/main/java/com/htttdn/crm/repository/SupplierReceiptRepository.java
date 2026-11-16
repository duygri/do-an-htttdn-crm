package com.htttdn.crm.repository;

import com.htttdn.crm.entity.SupplierReceipt;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

public interface SupplierReceiptRepository extends JpaRepository<SupplierReceipt,Long>,JpaSpecificationExecutor<SupplierReceipt>{
    @Query("select r from SupplierReceipt r where r.invoiceDate>=:from and r.invoiceDate<=:to order by r.invoiceDate asc, r.id asc")
    List<SupplierReceipt> findInRange(@Param("from") LocalDate from,@Param("to") LocalDate to);

    @Query("select r from SupplierReceipt r where (:supplierId is null or r.supplier.id=:supplierId) and (:from is null or r.invoiceDate>=:from) and (:to is null or r.invoiceDate<=:to) order by r.invoiceDate desc, r.id desc")
    Page<SupplierReceipt> search(@Param("supplierId") Long supplierId,@Param("from") LocalDate from,@Param("to") LocalDate to,Pageable pageable);

    java.util.List<SupplierReceipt> findTop10BySupplierIdOrderByInvoiceDateDescIdDesc(Long supplierId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SupplierReceipt r where r.id=:id")
    java.util.Optional<SupplierReceipt> findLockedById(@Param("id") Long id);
}
