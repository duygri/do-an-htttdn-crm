package com.htttdn.crm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.htttdn.crm.entity.*;
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
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:inventoryreport;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop","crm.care-reminders.enabled=false"})
@AutoConfigureMockMvc @Transactional
class InventoryReportIntegrationTest {
    @Autowired InventoryReportService reports;
    @Autowired StocktakeService stocktakes;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    @Autowired JwtTokenService jwt;
    @Autowired ObjectMapper json;

    User user(String role) {
        var user = new User();
        user.setEmail(UUID.randomUUID() + "@inventory-report.test");
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

    Product product(String name, int stock, String categoryName) {
        var category = new Category();
        category.setName(categoryName);
        em.persist(category);
        var product = new Product();
        product.setCategoryEntity(category);
        product.setName(name);
        product.setPrice(BigDecimal.TEN);
        product.setStock(stock);
        em.persist(product);
        return product;
    }

    void receipt(Supplier supplier, Product product, LocalDate date, String unitCost) {
        var receipt = new SupplierReceipt();
        receipt.setSupplier(supplier);
        receipt.setInvoiceNumber("HD-" + UUID.randomUUID());
        receipt.setInvoiceDate(date);
        receipt.setTotalAmount(new BigDecimal(unitCost));
        receipt.getItems().add(new SupplierReceiptItem(receipt, product, 1, new BigDecimal(unitCost)));
        em.persist(receipt);
    }

    @Test void valuesStockAtLatestReceiptCostAndFlagsMissingCosts() {
        var supplier = supplier();
        var shirt = product("Áo A", 5, "Áo");
        var trousers = product("Quần B", 2, "Quần");
        receipt(supplier, shirt, LocalDate.of(2026, 1, 1), "10000");
        receipt(supplier, shirt, LocalDate.of(2026, 2, 1), "12000");
        em.flush();

        var view = reports.report();
        assertEquals(2, view.products().size());
        assertEquals(new BigDecimal("60000.00"), view.totalValue());
        assertEquals(7, view.totalUnits());
        assertEquals(1, view.missingCostProducts());
        assertEquals(2, view.lowStockCount());
        var valued = view.products().stream().filter(row -> row.name().equals("Áo A")).findFirst().orElseThrow();
        assertEquals(new BigDecimal("12000.00"), valued.unitCost());
        assertEquals(new BigDecimal("60000.00"), valued.value());
        assertTrue(valued.lowStock());
        var missing = view.products().stream().filter(row -> row.name().equals("Quần B")).findFirst().orElseThrow();
        assertEquals(BigDecimal.ZERO.setScale(2), missing.unitCost());
        assertEquals(new BigDecimal("0.00"), missing.value());
        assertEquals(2, view.categories().size());
        assertEquals(5, view.categories().stream().filter(row -> row.category().equals("Áo")).findFirst().orElseThrow().units());
    }

    @Test void reportEndpointRequiresManagerRole() throws Exception {
        product("Áo C", 3, "Áo");
        em.flush();
        var manager = jwt.issue(user("MANAGER").getId(), "MANAGER");
        mvc.perform(get("/api/manager/reports/inventory").header("Authorization", "Bearer " + manager))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.products.length()").value(1))
            .andExpect(jsonPath("$.missingCostProducts").value(1));
        mvc.perform(get("/api/manager/reports/inventory")
                .header("Authorization", "Bearer " + jwt.issue(user("CUSTOMER").getId(), "CUSTOMER")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/manager/reports/inventory")).andExpect(status().isUnauthorized());
    }

    @Test void stocktakeWorkflowOverHttpCompletesAndAdjustsStock() throws Exception {
        var product = product("Áo D", 9, "Áo");
        em.flush();
        var manager = jwt.issue(user("MANAGER").getId(), "MANAGER");

        var created = mvc.perform(post("/api/manager/stocktakes")
                .header("Authorization", "Bearer " + manager)
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Đợt 1\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andReturn();
        var body = json.readTree(created.getResponse().getContentAsString());
        long id = body.get("id").asLong();
        long itemId = body.get("items").get(0).get("id").asLong();

        mvc.perform(get("/api/manager/stocktakes").header("Authorization", "Bearer " + manager))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/manager/stocktakes/" + id).header("Authorization", "Bearer " + manager))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("KK-" + id));

        mvc.perform(patch("/api/manager/stocktakes/" + id + "/items")
                .header("Authorization", "Bearer " + manager)
                .contentType(MediaType.APPLICATION_JSON)
                .content("[{\"itemId\":" + itemId + ",\"countedQuantity\":6,\"note\":\"Lệch\"}]"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].difference").value(-3));

        mvc.perform(post("/api/manager/stocktakes/" + id + "/complete")
                .header("Authorization", "Bearer " + manager))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"));
        assertEquals(6, em.find(Product.class, product.getId()).getStock());

        mvc.perform(post("/api/manager/stocktakes/" + id + "/cancel")
                .header("Authorization", "Bearer " + manager))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("STOCKTAKE_NOT_DRAFT"));

        mvc.perform(get("/api/manager/stocktakes/999999").header("Authorization", "Bearer " + manager))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("STOCKTAKE_NOT_FOUND"));
        mvc.perform(post("/api/manager/stocktakes")
                .header("Authorization", "Bearer " + jwt.issue(user("CUSTOMER").getId(), "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/manager/stocktakes").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test void stocktakeCanBeCancelledWithoutChangingStock() throws Exception {
        var product = product("Áo E", 4, "Áo");
        em.flush();
        var manager = jwt.issue(user("MANAGER").getId(), "MANAGER");
        var created = mvc.perform(post("/api/manager/stocktakes")
                .header("Authorization", "Bearer " + manager)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productIds\":[" + product.getId() + "]}"))
            .andExpect(status().isCreated()).andReturn();
        long id = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(post("/api/manager/stocktakes/" + id + "/cancel")
                .header("Authorization", "Bearer " + manager))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertEquals(4, em.find(Product.class, product.getId()).getStock());
        assertTrue(stocktakes.detail(id).items().getFirst().countedQuantity() == null);
    }
}
