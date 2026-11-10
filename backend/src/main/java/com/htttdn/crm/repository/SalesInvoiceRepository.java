package com.htttdn.crm.repository;

import com.htttdn.crm.entity.SalesInvoice;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface SalesInvoiceRepository extends JpaRepository<SalesInvoice,Long>,JpaSpecificationExecutor<SalesInvoice>{
    Optional<SalesInvoice> findByOrderId(Long orderId);
    boolean existsByOrderId(Long orderId);

    @Query("select i from SalesInvoice i where (:keyword='' or lower(i.invoiceNumber) like lower(concat('%',:keyword,'%')) or lower(i.buyerName) like lower(concat('%',:keyword,'%'))) order by i.issuedAt desc, i.id desc")
    Page<SalesInvoice> search(@Param("keyword") String keyword,Pageable pageable);
}
