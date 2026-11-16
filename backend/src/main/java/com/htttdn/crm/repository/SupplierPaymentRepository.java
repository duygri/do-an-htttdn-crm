package com.htttdn.crm.repository;

import com.htttdn.crm.entity.SupplierPayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, Long> {
    List<SupplierPayment> findByReceiptIdOrderByIdAsc(Long receiptId);

    Page<SupplierPayment> findBySupplierIdOrderByPaidAtDescIdDesc(Long supplierId, Pageable pageable);

    @Query("select coalesce(sum(p.amount),0) from SupplierPayment p where p.receipt.id=:receiptId")
    BigDecimal paidForReceipt(@Param("receiptId") Long receiptId);

    @Query("select coalesce(sum(p.amount),0) from SupplierPayment p where p.supplier.id=:supplierId")
    BigDecimal paidForSupplier(@Param("supplierId") Long supplierId);
}
