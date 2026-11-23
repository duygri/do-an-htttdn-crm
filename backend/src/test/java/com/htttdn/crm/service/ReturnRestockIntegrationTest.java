package com.htttdn.crm.service;

import com.htttdn.crm.entity.*;
import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.repository.*;
import com.htttdn.crm.security.JwtTokenService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Fixtures are committed (no class-level @Transactional) because the manager endpoint re-reads detail through raw JDBC. */
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:returnRestock;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
class ReturnRestockIntegrationTest {
    @Autowired ReturnManagementService service;
    @Autowired EntityManager em;
    @Autowired JwtTokenService jwt;
    @Autowired MockMvc mvc;
    @Autowired StockMovementRepository stockMovements;
    @Autowired OrderReturnItemRepository returnItems;
    @Autowired PlatformTransactionManager tx;
    User manager,customer;Order order;Product product;OrderItem line;

    @BeforeEach void setup(){
        inTx(() -> {
            manager=user("Manager","MANAGER");customer=user("Customer","CUSTOMER");
            product=product("Áo",new BigDecimal("100000"));
            order=new Order();order.setOrderCode(System.nanoTime());order.setCustomer(customer);order.setStatus("DELIVERED");
            order.setPaymentMethod("COD");order.setPaymentStatus("COD");order.setReturnStatus("REQUESTED");order.setReturnReason("Sai size");order.setTotalAmount(new BigDecimal("200000"));em.persist(order);
            line=new OrderItem();line.setOrder(order);line.setProduct(product);line.setQuantity(2);line.setUnitPrice(new BigDecimal("100000"));order.getItems().add(line);em.persist(line);
            em.persist(new OrderReturnItem(order.getId(),line.getId(),2,"REFUND"));
            em.flush();
        });
    }

    @AfterEach void clean(){ em.clear(); }

    /** Runs one unit of work in a committed transaction (StockService.apply/lockProduct require MANDATORY). */
    void inTx(Runnable work){ new TransactionTemplate(tx).executeWithoutResult(status -> work.run()); }
    User user(String n,String r){User u=new User();u.setFullName(n);u.setEmail(n+System.nanoTime()+"@test.local");u.setPasswordHash("hash");u.setRole(r);em.persist(u);return u;}
    Product product(String name,BigDecimal price){var c=new Category();c.setName("Đổi trả");em.persist(c);Product p=new Product();p.setName(name);p.setCategoryEntity(c);p.setPrice(price);p.setStock(10);p.setActive(true);em.persist(p);return p;}
    String token(User u){return "Bearer "+jwt.issue(u.getId(),u.getRole());}
    void approve(){inTx(() -> service.process(token(manager),order.getId(),new ReturnManagementService.Request("APPROVED","")));}
    void complete(String note,Boolean restock){inTx(() -> service.process(token(manager),order.getId(),new ReturnManagementService.Request("COMPLETED",note,restock)));}
    int stock(){return em.find(Product.class,product.getId()).getStock();}
    String returnStatus(){return em.find(Order.class,order.getId()).getReturnStatus();}
    String returnUrl(){return "/api/manager/orders/"+order.getId()+"/return";}

    @Test void completingWithRestockAddsStockOnceAndWritesMovement(){
        var before=stockMovements.count();
        approve();
        complete("Đã nhận hàng",true);
        complete("Đã nhận hàng",true);
        em.clear();
        assertEquals("COMPLETED",returnStatus());
        assertEquals(12,stock()); // 10 + 2
        assertEquals(before+1,stockMovements.count());
        assertNotNull(returnItems.findByOrderIdOrderByIdAsc(order.getId()).getFirst().getRestockedAt());
        var movement=stockMovements.findAll().stream().filter(m->"RETURN_RESTOCK".equals(m.getMovementType())).findFirst().orElseThrow();
        assertEquals(2,movement.getQuantity());assertEquals(10,movement.getStockBefore());assertEquals(12,movement.getStockAfter());
        assertEquals("ORDER",movement.getReferenceType());assertEquals(order.getId(),movement.getReferenceId());
    }

    @Test void completingWithoutRestockKeepsStockUntouched(){
        var before=stockMovements.count();
        approve();
        complete("Đã đổi tại cửa hàng",null);
        em.clear();
        assertEquals("COMPLETED",returnStatus());
        assertEquals(10,stock());
        assertEquals(before,stockMovements.count());
        assertNull(returnItems.findByOrderIdOrderByIdAsc(order.getId()).getFirst().getRestockedAt());
    }

    // Opt-in only: restock=false must behave exactly like the legacy 2-arg request.
    @Test void explicitFalseRestockIsIgnored(){
        approve();
        complete("Khách đổi tại cửa hàng",false);
        em.clear();
        assertEquals("COMPLETED",returnStatus());
        assertEquals(10,stock());
        assertNull(returnItems.findByOrderIdOrderByIdAsc(order.getId()).getFirst().getRestockedAt());
    }

    @Test void restockWithoutReturnItemsFailsWithoutCompleting(){
        inTx(() -> { returnItems.deleteAll(); returnItems.flush(); });
        approve();
        var error=assertThrows(ApiException.class,() -> complete("Đã nhận hàng",true));
        assertEquals("RETURN_ITEMS_REQUIRED",error.code());
        assertEquals(HttpStatus.CONFLICT,error.status());
        em.clear();
        assertEquals("APPROVED",returnStatus());
        assertEquals(10,stock());
    }

    @Test void restockOnlyAppliesToCompletingNotApproval(){
        inTx(() -> service.process(token(manager),order.getId(),new ReturnManagementService.Request("APPROVED","",true)));
        em.clear();
        assertEquals(10,stock());
        assertEquals("APPROVED",returnStatus());
    }

    @Test void managerEndpointAcceptsRestockFlagAndExposesRestockedAt() throws Exception {
        mvc.perform(patch(returnUrl()).header("Authorization",token(manager)).contentType("application/json")
                .content("{\"status\":\"APPROVED\",\"note\":\"Tiếp nhận\"}")).andExpect(status().isOk());
        mvc.perform(patch(returnUrl()).header("Authorization",token(manager)).contentType("application/json")
                .content("{\"status\":\"COMPLETED\",\"note\":\"Đã nhận lại hàng\",\"restock\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.returnStatus").value("COMPLETED"))
            .andExpect(jsonPath("$.returnItems[0].quantity").value(2))
            .andExpect(jsonPath("$.returnItems[0].restockedAt").isNotEmpty());
        em.clear();
        assertEquals(12,stock());
    }

    @Test void managerEndpointWithoutRestockLeavesStockUntouched() throws Exception {
        mvc.perform(patch(returnUrl()).header("Authorization",token(manager)).contentType("application/json")
                .content("{\"status\":\"APPROVED\",\"note\":\"Tiếp nhận\"}")).andExpect(status().isOk());
        mvc.perform(patch(returnUrl()).header("Authorization",token(manager)).contentType("application/json")
                .content("{\"status\":\"COMPLETED\",\"note\":\"Đổi tại cửa hàng\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.returnStatus").value("COMPLETED"))
            .andExpect(jsonPath("$.returnItems[0].restockedAt").isEmpty());
        em.clear();
        assertEquals(10,stock());
    }

    // Regression: the legacy 4-arg StockService has no OrderReturnItemRepository; it must fail loudly, never no-op.
    @Test void legacyFourArgStockServiceFailsLoudlyInsteadOfSilentlySkippingRestock(){
        var legacy=new StockService(mock(ProductRepository.class),mock(EntityManager.class),mock(StockMovementRepository.class),mock(InternalNoticeService.class));
        var pending=new OrderReturnItem(1L,2L,2,"REFUND"); // non-restocked line, so a silent no-op would look like success
        var order=new Order();order.setOrderCode(99L);
        var error=assertThrows(IllegalStateException.class,() -> legacy.restockReturn(order,java.util.List.of(pending)));
        assertEquals("OrderReturnItemRepository chưa được cấu hình.",error.getMessage());
        assertNull(pending.getRestockedAt());
    }

    @Test void managerEndpointReportsMissingReturnItemsAsConflict() throws Exception {
        inTx(() -> { returnItems.deleteAll(); returnItems.flush(); });
        mvc.perform(patch(returnUrl()).header("Authorization",token(manager)).contentType("application/json")
                .content("{\"status\":\"APPROVED\",\"note\":\"Tiếp nhận\"}")).andExpect(status().isOk());
        mvc.perform(patch(returnUrl()).header("Authorization",token(manager)).contentType("application/json")
                .content("{\"status\":\"COMPLETED\",\"note\":\"Đã nhận lại hàng\",\"restock\":true}"))
            .andExpect(status().isConflict());
        em.clear();
        assertEquals("APPROVED",returnStatus());
        assertEquals(10,stock());
    }
}
