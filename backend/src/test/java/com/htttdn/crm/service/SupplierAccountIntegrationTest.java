package com.htttdn.crm.service;

import com.htttdn.crm.entity.*;
import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.security.JwtTokenService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:supplieraccounts;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop","crm.care-reminders.enabled=false"})
@AutoConfigureMockMvc
@Transactional
class SupplierAccountIntegrationTest {
    @Autowired SupplierAccountService accounts;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    @Autowired JwtTokenService jwt;

    User user(String role) {
        var user = new User();
        user.setEmail(UUID.randomUUID() + "@supplier-account.test");
        user.setFullName(role);
        user.setPhone("0900000000");
        user.setRole(role);
        user.setPasswordHash("test-only");
        em.persist(user);
        return user;
    }

    Supplier supplier() {
        var supplier = new Supplier();
        supplier.code = "NCC-" + UUID.randomUUID().toString().substring(0, 8);
        supplier.name = "Nhà cung cấp " + supplier.code;
        em.persist(supplier);
        return supplier;
    }

    Product product(Supplier supplier, String name, int stock) {
        var category = new Category();
        category.setName("Áo");
        em.persist(category);
        var product = new Product();
        product.setCategoryEntity(category);
        product.setName(name);
        product.setPrice(BigDecimal.TEN);
        product.setStock(stock);
        product.setSupplierId(supplier.id);
        em.persist(product);
        return product;
    }

    SupplierReceipt receipt(Supplier supplier, Product product, String total) {
        var receipt = new SupplierReceipt();
        receipt.setSupplier(supplier);
        receipt.setInvoiceNumber("HD-" + UUID.randomUUID());
        receipt.setInvoiceDate(LocalDate.of(2026, 11, 1));
        receipt.setTotalAmount(new BigDecimal(total));
        receipt.getItems().add(new SupplierReceiptItem(receipt, product, 1, new BigDecimal(total)));
        em.persist(receipt);
        em.flush();
        return receipt;
    }

    SupplierAccountService.PaymentRequest payment(Long receiptId, String amount) {
        return new SupplierAccountService.PaymentRequest(receiptId, new BigDecimal(amount),
            LocalDate.of(2026, 11, 10), "CASH", null);
    }

    @Test void recordsPartialPaymentsAndRejectsOverpayment() {
        var manager = user("MANAGER");
        var actor = new AuditTrail.Actor(manager.getId(), "Quản lý", "MANAGER");
        var supplier = supplier();
        var receipt = receipt(supplier, product(supplier, "Áo A", 5), "100000.00");

        accounts.pay(supplier.id, payment(receipt.getId(), "40000"), actor);
        var detail = accounts.detail(supplier.id);
        assertEquals(0, new BigDecimal("60000.00").compareTo(detail.outstanding()));
        assertEquals(0, new BigDecimal("40000.00").compareTo(detail.totalPaid()));

        accounts.pay(supplier.id, payment(receipt.getId(), "60000"), actor);
        assertEquals(0, BigDecimal.ZERO.compareTo(accounts.detail(supplier.id).outstanding()));

        var failure = assertThrows(ApiException.class,
            () -> accounts.pay(supplier.id, payment(receipt.getId(), "1"), actor));
        assertEquals("PAYMENT_EXCEEDS_OUTSTANDING", failure.code());
        assertEquals(2, accounts.payments(supplier.id, 0, 20).getTotalElements());
    }

    @Test void rejectsReceiptOfAnotherSupplierAndForeignRoles() {
        var manager = user("MANAGER");
        var actor = new AuditTrail.Actor(manager.getId(), "Quản lý", "MANAGER");
        var first = supplier();
        var second = supplier();
        var receipt = receipt(second, product(second, "Áo B", 3), "50000.00");

        var failure = assertThrows(ApiException.class,
            () -> accounts.pay(first.id, payment(receipt.getId(), "1000"), actor));
        assertEquals("SUPPLIER_RECEIPT_NOT_FOUND", failure.code());

        assertThrows(IllegalStateException.class, () -> accounts.pay(first.id, payment(receipt.getId(), "1000"), null));
    }

    @Test void detailReportsPurchaseTotalsProductsAndLatestReceipt() {
        var supplier = supplier();
        var shirt = product(supplier, "Áo C", 4);
        var first = receipt(supplier, shirt, "30000.00");
        var second = receipt(supplier, shirt, "20000.00");
        second.setInvoiceDate(LocalDate.of(2026, 11, 5));
        em.flush();
        var actor = new AuditTrail.Actor(user("MANAGER").getId(), "Quản lý", "MANAGER");
        accounts.pay(supplier.id, payment(first.getId(), "10000"), actor);

        var detail = accounts.detail(supplier.id);
        assertEquals(supplier.code, detail.code());
        assertEquals(1, detail.productCount());
        assertEquals(2, detail.receiptCount());
        assertEquals(0, new BigDecimal("50000.00").compareTo(detail.totalPurchase()));
        assertEquals(0, new BigDecimal("10000.00").compareTo(detail.totalPaid()));
        assertEquals(0, new BigDecimal("40000.00").compareTo(detail.outstanding()));
        assertEquals(LocalDate.of(2026, 11, 5), detail.lastReceiptDate());
        assertEquals(1, detail.products().size());
        assertEquals("Áo C", detail.products().get(0).name());
        assertEquals(4, detail.products().get(0).stock());
        var secondRow = detail.receipts().stream().filter(row -> row.id().equals(second.getId())).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("20000.00").compareTo(secondRow.outstanding()));
        var firstRow = detail.receipts().stream().filter(row -> row.id().equals(first.getId())).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("10000.00").compareTo(firstRow.paidAmount()));
        assertEquals(0, new BigDecimal("20000.00").compareTo(firstRow.outstanding()));
    }

    @Test void rejectsInvalidPayments() {
        var supplier = supplier();
        var receipt = receipt(supplier, product(supplier, "Áo D", 1), "10000.00");
        var actor = new AuditTrail.Actor(user("MANAGER").getId(), "Quản lý", "MANAGER");

        var zero = assertThrows(ApiException.class,
            () -> accounts.pay(supplier.id, new SupplierAccountService.PaymentRequest(receipt.getId(), BigDecimal.ZERO,
                LocalDate.of(2026, 11, 10), "CASH", null), actor));
        assertEquals("INVALID_PAYMENT", zero.code());

        var blankMethod = assertThrows(ApiException.class,
            () -> accounts.pay(supplier.id, new SupplierAccountService.PaymentRequest(receipt.getId(), BigDecimal.ONE,
                LocalDate.of(2026, 11, 10), " ", null), actor));
        assertEquals("INVALID_PAYMENT", blankMethod.code());

        var unknownSupplier = assertThrows(ApiException.class,
            () -> accounts.detail(999999L));
        assertEquals("SUPPLIER_NOT_FOUND", unknownSupplier.code());
        assertThrows(ApiException.class, () -> accounts.payments(999999L, 0, 20));
    }

    @Test void detailAndPaymentEndpointsRequireManagerRole() throws Exception {
        var supplier = supplier();
        var product = product(supplier, "Áo E", 2);
        var receipt = receipt(supplier, product, "40000.00");
        em.flush();
        var manager = jwt.issue(user("MANAGER").getId(), "MANAGER");
        var customer = jwt.issue(user("CUSTOMER").getId(), "CUSTOMER");

        mvc.perform(get("/api/manager/suppliers/" + supplier.id).header("Authorization", "Bearer " + manager))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(supplier.code))
            .andExpect(jsonPath("$.outstanding").value(40000.00))
            .andExpect(jsonPath("$.products.length()").value(1));
        mvc.perform(get("/api/manager/suppliers/" + supplier.id).header("Authorization", "Bearer " + customer))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/manager/suppliers/" + supplier.id)).andExpect(status().isUnauthorized());

        mvc.perform(post("/api/manager/suppliers/" + supplier.id + "/payments")
                .header("Authorization", "Bearer " + manager)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"receiptId\":" + receipt.getId() + ",\"amount\":15000,\"paidAt\":\"2026-11-10\",\"method\":\"CASH\",\"note\":\"Tiền mặt\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.amount").value(15000.00))
            .andExpect(jsonPath("$.method").value("CASH"));
        mvc.perform(get("/api/manager/suppliers/" + supplier.id + "/payments")
                .header("Authorization", "Bearer " + manager))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].receiptNumber").value(receipt.getInvoiceNumber()));
    }

    @Test void archivedSupplierBlocksAssignmentAndReceiptsButKeepsPayables() throws Exception {
        var supplier = supplier();
        var product = product(supplier, "Áo F", 3);
        var receipt = receipt(supplier, product, "25000.00");
        em.flush();
        var manager = jwt.issue(user("MANAGER").getId(), "MANAGER");

        mvc.perform(patch("/api/manager/suppliers/" + supplier.id + "/status")
                .header("Authorization", "Bearer " + manager)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));
        mvc.perform(patch("/api/manager/suppliers/" + supplier.id + "/status")
                .header("Authorization", "Bearer " + manager)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
            .andExpect(status().isOk());

        mvc.perform(patch("/api/manager/products/" + product.getId() + "/supplier")
                .header("Authorization", "Bearer " + manager)
                .contentType(MediaType.APPLICATION_JSON).content("{\"supplierId\":" + supplier.id + "}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SUPPLIER_INACTIVE"));
        mvc.perform(post("/api/manager/supplier-receipts")
                .header("Authorization", "Bearer " + manager)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"supplierId\":" + supplier.id + ",\"invoiceNumber\":\"HD-NEW\",\"invoiceDate\":\"2026-11-12\",\"items\":[{\"productId\":" + product.getId() + ",\"quantity\":1,\"unitCost\":1000}]}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SUPPLIER_INACTIVE"));

        mvc.perform(get("/api/manager/suppliers/" + supplier.id).header("Authorization", "Bearer " + manager))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));
        mvc.perform(post("/api/manager/suppliers/" + supplier.id + "/payments")
                .header("Authorization", "Bearer " + manager)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"receiptId\":" + receipt.getId() + ",\"amount\":5000,\"paidAt\":\"2026-11-12\",\"method\":\"BANK_TRANSFER\"}"))
            .andExpect(status().isCreated());

        var csv = mvc.perform(get("/api/manager/suppliers/" + supplier.id + "/reports/summary.csv")
                .header("Authorization", "Bearer " + manager))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/csv"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("cong-no-ncc.csv")))
            .andReturn().getResponse().getContentAsString();
        assertTrue(csv.startsWith("\uFEFF"));
        assertTrue(csv.contains("receiptId,invoiceNumber,invoiceDate,totalAmount,paidAmount,outstanding"));
        assertTrue(csv.contains(receipt.getInvoiceNumber() + ",2026-11-01,25000.00,5000.00,20000.00"));
    }
}
