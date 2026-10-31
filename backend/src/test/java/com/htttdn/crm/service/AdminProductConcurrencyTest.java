package com.htttdn.crm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.htttdn.crm.dto.admin.AdminDtos.ProductRequest;
import com.htttdn.crm.dto.admin.AdminDtos.StockRequest;
import com.htttdn.crm.entity.Category;
import com.htttdn.crm.entity.Product;
import com.htttdn.crm.repository.AuditLogRepository;
import com.htttdn.crm.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;

// Standalone context: no application configuration, schedulers, or external database.
@SpringJUnitConfig(AdminProductConcurrencyTest.Config.class)
class AdminProductConcurrencyTest {
    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = ProductRepository.class)
    @Import({AdminProductService.class, StockService.class, AuditTrail.class})
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:adminProductConcurrency;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000", "sa", "");
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan(Product.class.getPackageName());
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of(
                    "hibernate.hbm2ddl.auto", "create-drop",
                    "hibernate.hbm2ddl.halt_on_error", "true",
                    "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
    }

    @Autowired AdminProductService service;
    @Autowired StockService stock;
    @Autowired ProductRepository products;
    @Autowired AuditLogRepository audits;
    @Autowired EntityManager em;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactionManager;

    @ParameterizedTest
    @ValueSource(strings = {"update", "archive", "restore", "stock"})
    void managerWriteWaitsForCheckoutAndRefreshesPreviouslyLoadedProduct(String operation) throws Exception {
        Long id = fixture(!operation.equals("restore"));
        var managerLoaded = new CountDownLatch(1);
        var startManagerWrite = new CountDownLatch(1);
        var checkoutHoldingLock = new CountDownLatch(1);
        var releaseCheckout = new CountDownLatch(1);
        var managerReturned = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var manager = executor.submit(() -> transaction().executeWithoutResult(status -> {
                // A request persistence context may already contain a stale product.
                assertEquals(10, em.find(Product.class, id).getStock());
                managerLoaded.countDown();
                await(startManagerWrite);
                switch (operation) {
                    case "update" -> service.update(id, request("Edited product", 10));
                    case "archive" -> service.archive(id);
                    case "restore" -> service.restore(id);
                    case "stock" -> service.stock(id, new StockRequest(12));
                    default -> throw new AssertionError(operation);
                }
                managerReturned.countDown();
            }));
            await(managerLoaded);
            var checkout = executor.submit(() -> transaction().executeWithoutResult(status -> {
                // Exercise checkout's actual stock lock and whole-row Hibernate update.
                Product product = stock.lockProduct(id).orElseThrow();
                product.setStock(product.getStock() - 3);
                product.setDescription("Committed by checkout");
                em.flush();
                checkoutHoldingLock.countDown();
                await(releaseCheckout);
            }));
            await(checkoutHoldingLock);
            startManagerWrite.countDown();
            // Observe a real DB lock wait, not an assumed thread scheduling delay.
            awaitBlockedOrReturned(managerReturned);
            boolean returnedBeforeCheckoutCommit = managerReturned.getCount() == 0;
            releaseCheckout.countDown();
            checkout.get(10, TimeUnit.SECONDS);
            manager.get(10, TimeUnit.SECONDS);

            Product saved = products.findById(id).orElseThrow();
            assertEquals(operation.equals("stock") ? 12 : 7, saved.getStock(),
                    "Manager write must not restore stock deducted by checkout");
            assertEquals(!operation.equals("archive"), saved.isActive());
            assertEquals(operation.equals("update") ? "Edited product" : "Original product", saved.getName());
            if (!operation.equals("update")) assertEquals("Committed by checkout", saved.getDescription());
            assertFalse(returnedBeforeCheckoutCommit, "Manager mutation must acquire the shared stock lock first");
        } finally {
            startManagerWrite.countDown();
            releaseCheckout.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void staleFullEditPayloadPreservesCommittedStockAndExplicitStockWritesRemainAudited() throws Exception {
        var servletRequest = new MockHttpServletRequest();
        servletRequest.setAttribute(AuditTrail.ACTOR_ATTRIBUTE, new AuditTrail.Actor(1L, "Manager", "MANAGER"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));
        try {
            Long id = service.create(request("Original product", 10)).getId();
            transaction().executeWithoutResult(status -> stock.lockProduct(id).orElseThrow().setStock(7));
            assertEquals(7, service.update(id, request("Edited product", 10)).getStock());
            service.archive(id);
            service.archive(id);
            service.restore(id);
            var restoredAt = products.findById(id).orElseThrow().getUpdatedAt();
            service.restore(id);
            assertEquals(restoredAt, products.findById(id).orElseThrow().getUpdatedAt());
            assertEquals(12, service.stock(id, new StockRequest(12)).getStock());
            service.stock(id, new StockRequest(12));

            var logs = audits.findAll().stream().filter(log -> log.getTargetId().equals(id)).toList();
            assertEquals(5, logs.size(), "CREATE/UPDATE/ARCHIVE/RESTORE/STOCK each audit once");
            var mapper = new ObjectMapper();
            for (String action : new String[]{"UPDATE", "ARCHIVE", "RESTORE", "STOCK"}) {
                var log = logs.stream().filter(item -> item.getAction().equals(action)).findFirst().orElseThrow();
                assertEquals(7, mapper.readTree(log.getBeforeData()).get("stock").asInt());
                assertEquals(action.equals("STOCK") ? 12 : 7, mapper.readTree(log.getAfterData()).get("stock").asInt());
            }
            assertTrue(products.findById(id).orElseThrow().isActive());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    private Long fixture(boolean active) {
        return transaction().execute(status -> {
            var category = new Category();
            category.setName("Inventory " + UUID.randomUUID());
            em.persist(category);
            var product = new Product();
            product.setName("Original product");
            product.setCategoryEntity(category);
            product.setPrice(BigDecimal.valueOf(100000));
            product.setStock(10);
            product.setActive(active);
            em.persist(product);
            return product.getId();
        });
    }

    private ProductRequest request(String name, int quantity) {
        return new ProductRequest(name, "Inventory edits", "Edited description", BigDecimal.valueOf(120000), quantity, null);
    }

    private TransactionTemplate transaction() { return new TransactionTemplate(transactionManager); }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS), "Timed out coordinating transactions");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private void awaitBlockedOrReturned(CountDownLatch returned) throws InterruptedException {
        var jdbc = new JdbcTemplate(dataSource);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (returned.getCount() != 0) {
            if (jdbc.queryForObject("select count(*) from information_schema.sessions where blocker_id is not null", Integer.class) > 0) return;
            assertTrue(System.nanoTime() < deadline, "Manager never reached a database lock wait");
            returned.await(10, TimeUnit.MILLISECONDS);
        }
    }
}
