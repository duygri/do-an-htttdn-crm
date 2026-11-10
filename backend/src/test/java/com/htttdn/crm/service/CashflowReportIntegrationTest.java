package com.htttdn.crm.service;

import com.htttdn.crm.entity.*;
import com.htttdn.crm.security.JwtTokenService;
import com.htttdn.crm.exception.ApiException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:revenuereport;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop","crm.care-reminders.enabled=false"})
@AutoConfigureMockMvc @Transactional
class CashflowReportIntegrationTest {
    @Autowired CashflowReportService report;@Autowired EntityManager em;@Autowired PaymentReconciliationService reconciliation;@Autowired MockMvc mvc;@Autowired JwtTokenService jwt;@Autowired SupplierReceiptService receipts;
    final Instant from=Instant.parse("2026-09-30T17:00:00Z"),to=Instant.parse("2026-10-02T17:00:00Z");
    User user(String role){var u=new User();u.setEmail(UUID.randomUUID()+"@revenue.test");u.setFullName(role);u.setPhone("0900000000");u.setRole(role);u.setPasswordHash("test-only");em.persist(u);return u;}
    Order order(String method,String status,String payment,long total,Instant paid){var o=new Order();o.setCustomer(user("CUSTOMER"));o.setOrderCode(Math.abs(UUID.randomUUID().getLeastSignificantBits()%900000000000L));o.setPaymentMethod(method);o.setPaymentStatus(payment);o.setStatus(status);o.setTotalAmount(BigDecimal.valueOf(total));o.paidAt=paid;em.persist(o);return o;}
    void item(Order o,int count,long unit){var c=new Category();c.setName("Category");em.persist(c);var p=new Product();p.setCategoryEntity(c);p.setName("Sản phẩm thử");p.setPrice(BigDecimal.valueOf(unit));em.persist(p);for(int i=0;i<count;i++){var line=new OrderItem();line.setOrder(o);line.setProduct(p);line.setQuantity(1);line.setUnitPrice(BigDecimal.valueOf(unit));em.persist(line);}}
    Supplier supplier(){var s=new Supplier();s.code="NCC-"+UUID.randomUUID();s.name="Nhà cung cấp thử";em.persist(s);return s;}
    Product product(){var c=new Category();c.setName("Category");em.persist(c);var p=new Product();p.setCategoryEntity(c);p.setName("Hàng nhập");p.setPrice(BigDecimal.TEN);em.persist(p);return p;}
    SupplierReceipt receipt(Supplier supplier,LocalDate date,long amount){var p=product();return receipts.create(new SupplierReceiptService.CreateRequest(supplier.id,"HDN-"+UUID.randomUUID(),date,null,List.of(new SupplierReceiptService.LineRequest(p.getId(),1,BigDecimal.valueOf(amount)))));}

    @Test void receiptsUseVietnamDaysAndSupplierCostsWithoutMultiplyingItems(){
        var paid=order("PAYOS","CANCELLED","PAID",100000,from);item(paid,3,100000);
        var cod=order("COD","DELIVERED","PAID",200000,null);cod.codCollectedAt=Instant.parse("2026-10-01T16:59:59Z");cod.codCollectedAmount=BigDecimal.valueOf(200000);item(cod,2,150000);
        order("COD","DELIVERED","COD",999999,null);order("PAYOS","PENDING","PENDING",999999,null);order("PAYOS","COMPLETED","PAID",50000,null);order("PAYOS","COMPLETED","PAID",0,from);
        var supplier=supplier();var first=receipt(supplier,LocalDate.parse("2026-10-01"),50000);var second=receipt(supplier,LocalDate.parse("2026-10-02"),70000);
        receipt(supplier,LocalDate.parse("2026-09-30"),40000);receipt(supplier,LocalDate.parse("2026-10-03"),60000);em.flush();
        assertEquals(0,BigDecimal.valueOf(50000).compareTo(first.getTotalAmount()));assertEquals(0,BigDecimal.valueOf(70000).compareTo(second.getTotalAmount()));

        var r=report.report(from,to,"day");
        assertEquals(0,BigDecimal.valueOf(300000).compareTo(r.collected()));assertEquals(0,BigDecimal.valueOf(120000).compareTo(r.purchaseCost()));assertEquals(0,BigDecimal.valueOf(180000).compareTo(r.difference()));assertEquals(2,r.collectedOrders());
        assertEquals(2,r.series().size());assertEquals("2026-10-01",r.series().getFirst().period());
        assertEquals(0,BigDecimal.valueOf(300000).compareTo(r.series().getFirst().collected()));assertEquals(0,BigDecimal.valueOf(50000).compareTo(r.series().getFirst().purchaseCost()));assertEquals(0,BigDecimal.valueOf(250000).compareTo(r.series().getFirst().difference()));
        assertEquals(0,BigDecimal.valueOf(-70000).compareTo(r.series().getLast().difference()));
        assertEquals(1,r.unknownMilestone().orders());assertEquals(0,BigDecimal.valueOf(50000).compareTo(r.unknownMilestone().amount()));
        assertEquals(1,r.topProducts().size());assertEquals(2,r.topProducts().getFirst().quantity());assertEquals(0,BigDecimal.valueOf(300000).compareTo(r.topProducts().getFirst().grossItemValue()));

        var month=report.report(from,to,"month");assertEquals(1,month.series().size());assertEquals("2026-10",month.series().getFirst().period());assertEquals(0,r.difference().compareTo(month.difference()));
        var quarter=report.report(from,to,"quarter");assertEquals(1,quarter.series().size());assertEquals("2026-Q4",quarter.series().getFirst().period());assertEquals(0,BigDecimal.valueOf(120000).compareTo(quarter.series().getFirst().purchaseCost()));
        var year=report.report(from,to,"year");assertEquals(1,year.series().size());assertEquals("2026",year.series().getFirst().period());assertEquals(0,r.collected().compareTo(year.collected()));
    }

    @Test void purchaseCostOnlyMakesThePeriodNegative(){
        receipt(supplier(),LocalDate.parse("2026-10-01"),100000);em.flush();
        var r=report.report(from,to,"day");
        assertEquals(0,r.collected().signum());assertEquals(0,BigDecimal.valueOf(-100000).compareTo(r.difference()));
    }

    @Test void emptyDaysAndDefaultsAndInvalidRanges(){
        var r=report.report(from,to,"day");assertEquals(2,r.series().size());assertEquals(0,r.collected().signum());assertEquals(0,r.purchaseCost().signum());
        assertEquals(12,report.report(null,null,"month").series().size());
        assertEquals(365,report.report(null,null,"day").series().size());
        assertThrows(ApiException.class,()->report.report(to,from,"day"));
        assertThrows(ApiException.class,()->report.report(from,from.plus(Duration.ofDays(367*5)),"day"));
        assertThrows(ApiException.class,()->report.report(from,to,"week"));
        assertThrows(ApiException.class,()->report.report(from,null,"day"));
    }

    @Test void onlyManagersCanReadTheReport()throws Exception{
        for(String role:new String[]{"CUSTOMER","ADMIN"})mvc.perform(get("/api/manager/reports/revenue").header("Authorization","Bearer "+jwt.issue(user(role).getId(),role))).andExpect(status().isForbidden());
        mvc.perform(get("/api/manager/reports/revenue")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/manager/reports/revenue").header("Authorization","Bearer "+jwt.issue(user("MANAGER").getId(),"MANAGER"))).andExpect(status().isOk()).andExpect(jsonPath("$.series.length()").value(12));
        mvc.perform(get("/api/manager/reports/cashflow").header("Authorization","Bearer "+jwt.issue(user("MANAGER").getId(),"MANAGER"))).andExpect(status().isOk());
    }

    @Test void firstConfirmationHasAnImmutableMilestoneAndLegacyPaidIsNotBackfilled(){
        var o=order("PAYOS","PENDING","PENDING",100000,null);var p=new Payment();p.setOrder(o);p.setAmount(o.getTotalAmount());em.persist(p);reconciliation.paid(o,p,null);Instant at=o.paidAt;assertNotNull(at);reconciliation.paid(o,p,null);assertEquals(at,o.paidAt);
        var legacy=order("PAYOS","COMPLETED","PAID",100000,null);var old=new Payment();old.setOrder(legacy);old.setAmount(legacy.getTotalAmount());old.setStatus("PAID");em.persist(old);reconciliation.paid(legacy,old,null);assertNull(legacy.paidAt);
    }

    @Test void managerIssuesOneSnapshotInvoiceAndCustomerCanReadIt()throws Exception{
        var customer=user("CUSTOMER");var o=new Order();o.setCustomer(customer);o.setOrderCode(778899L);o.setStatus("DELIVERED");o.setPaymentMethod("COD");o.setPaymentStatus("PAID");o.setTotalAmount(BigDecimal.valueOf(300000));o.setDiscountAmount(BigDecimal.valueOf(50000));o.setDeliveryAddress("1 Đường thử");em.persist(o);
        var c=new Category();c.setName("Category");em.persist(c);var p=new Product();p.setCategoryEntity(c);p.setName("Áo thử");p.setPrice(BigDecimal.valueOf(150000));em.persist(p);
        for(int i=0;i<2;i++){var line=new OrderItem();line.setOrder(o);line.setProduct(p);line.setQuantity(1);line.setUnitPrice(BigDecimal.valueOf(150000));line.setSize("M");o.getItems().add(line);em.persist(line);}
        em.flush();
        var manager=jwt.issue(user("MANAGER").getId(),"MANAGER");
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        String first=mvc.perform(post("/api/manager/orders/"+o.getId()+"/invoice").header("Authorization","Bearer "+manager).contentType("application/json").content("{\"note\":\"Xuất hóa đơn\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.invoiceNumber").value("HD-778899")).andExpect(jsonPath("$.items.length()").value(2)).andReturn().getResponse().getContentAsString();
        assertEquals(0,new BigDecimal("300000").compareTo(mapper.readTree(first).path("subtotal").decimalValue()));
        assertEquals(0,new BigDecimal("300000").compareTo(mapper.readTree(first).path("totalAmount").decimalValue()));
        String second=mvc.perform(post("/api/manager/orders/"+o.getId()+"/invoice").header("Authorization","Bearer "+manager).contentType("application/json").content("{}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(mapper.readTree(first).path("id"),mapper.readTree(second).path("id"));
        mvc.perform(get("/api/manager/invoices").param("q","HD-778899").header("Authorization","Bearer "+manager)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/orders/"+o.getId()+"/invoice").header("Authorization","Bearer "+jwt.issue(customer.getId(),"CUSTOMER"))).andExpect(status().isOk()).andExpect(jsonPath("$.invoiceNumber").value("HD-778899"));
        mvc.perform(get("/api/orders/"+o.getId()+"/invoice").header("Authorization","Bearer "+jwt.issue(user("CUSTOMER").getId(),"CUSTOMER"))).andExpect(status().isNotFound());
        mvc.perform(post("/api/manager/orders/"+o.getId()+"/invoice").header("Authorization","Bearer "+jwt.issue(user("ADMIN").getId(),"ADMIN")).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        var cancelled=order("COD","CANCELLED","CANCELLED",10000,null);item(cancelled,1,10000);em.flush();
        mvc.perform(post("/api/manager/orders/"+cancelled.getId()+"/invoice").header("Authorization","Bearer "+manager).contentType("application/json").content("{}")).andExpect(status().isConflict());
    }
}
