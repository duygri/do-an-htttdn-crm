package com.htttdn.crm.service;

import com.htttdn.crm.entity.*;
import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.repository.*;
import com.htttdn.crm.security.JwtTokenService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:returnItems;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc @Transactional
class ReturnItemsIntegrationTest {
    @Autowired EntityManager em; @Autowired OrderService orders; @Autowired JwtTokenService jwt;
    @Autowired OrderReturnItemRepository returnItems; @Autowired ProductRepository products;
    @Autowired MockMvc mvc; @Autowired ManagerOrderQuery managerQuery;
    User customer; com.htttdn.crm.entity.Order order; OrderItem shirt, pants; Product p1, p2;
    @BeforeEach void setup(){
        customer=user("Customer","CUSTOMER");
        p1=product("Áo",new BigDecimal("100000")); p2=product("Quần",new BigDecimal("200000"));
        order=new Order();order.setOrderCode(System.nanoTime());order.setCustomer(customer);order.setStatus("DELIVERED");order.setPaymentMethod("COD");order.setPaymentStatus("COD");order.setTotalAmount(new BigDecimal("400000"));em.persist(order);
        shirt=item(order,p1,2,new BigDecimal("100000")); pants=item(order,p2,1,new BigDecimal("200000")); em.flush();
    }
    User user(String n,String r){User u=new User();u.setFullName(n);u.setEmail(n+System.nanoTime()+"@test.local");u.setPasswordHash("hash");u.setRole(r);em.persist(u);return u;}
    Category category;
    Product product(String name,BigDecimal price){if(category==null){category=new Category();category.setName("Đổi trả");em.persist(category);}Product p=new Product();p.setName(name);p.setCategoryEntity(category);p.setPrice(price);p.setStock(10);p.setActive(true);em.persist(p);return p;}
    OrderItem item(com.htttdn.crm.entity.Order o,Product p,int q,BigDecimal price){OrderItem i=new OrderItem();i.setOrder(order);i.setProduct(p);i.setQuantity(q);i.setUnitPrice(price);o.getItems().add(i);em.persist(i);return i;}
    String token(){return "Bearer "+jwt.issue(customer.getId(),"CUSTOMER");}

    @Test void storesChosenItemQuantitiesAndResolutions(){
        var view=orders.requestReturn(customer,order.getId(),"Áo chật",List.of(new OrderService.ReturnLineInput(shirt.getId(),1,"EXCHANGE")));
        assertEquals("REQUESTED",view.returnStatus());
        assertEquals(1,view.returnItems().size());
        assertEquals(1,view.returnItems().getFirst().quantity());
        assertEquals("EXCHANGE",view.returnItems().getFirst().resolution());
        assertEquals(1,returnItems.findByOrderIdOrderByIdAsc(order.getId()).size());
    }
    @Test void omittedItemsFallsBackToWholeOrder(){
        var view=orders.requestReturn(customer,order.getId(),"Đổi cả đơn");
        assertEquals(2,view.returnItems().size());
        assertEquals(3,view.returnItems().stream().mapToInt(OrderService.ReturnItemView::quantity).sum());
    }
    @Test void rejectsOverQuantityForeignItemAndBadResolution(){
        assertThrows(ApiException.class,()->orders.requestReturn(customer,order.getId(),"x",List.of(new OrderService.ReturnLineInput(shirt.getId(),3,"REFUND"))));
        assertEquals("NONE",order.getReturnStatus());
        assertThrows(ApiException.class,()->orders.requestReturn(customer,order.getId(),"x",List.of(new OrderService.ReturnLineInput(999999L,1,"REFUND"))));
        assertThrows(ApiException.class,()->orders.requestReturn(customer,order.getId(),"x",List.of(new OrderService.ReturnLineInput(shirt.getId(),1,"BOGUS"))));
        assertThrows(ApiException.class,()->orders.requestReturn(customer,order.getId(),"x",List.of(new OrderService.ReturnLineInput(shirt.getId(),1,"REFUND"),new OrderService.ReturnLineInput(shirt.getId(),1,"REFUND"))));
    }
    @Test void customerViewCarriesLineIds(){
        var view=orders.detail(customer,order.getId());
        assertNotNull(view.items().getFirst().id());
        assertEquals(2,view.items().getFirst().quantity());
    }

    // Regression: the whole-order fallback must persist real order-item ids so View/Manager JSON can resolve them.
    @Test void wholeOrderFallbackResolvesProductsForCustomerAndManagerJson() throws Exception {
        orders.requestReturn(customer,order.getId(),"Đổi cả đơn");
        em.flush(); em.clear();
        mvc.perform(get("/api/orders/"+order.getId()).header("Authorization",token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").isNumber())
            .andExpect(jsonPath("$.returnItems.length()").value(2))
            .andExpect(jsonPath("$.returnItems[0].orderItemId").value(shirt.getId()))
            .andExpect(jsonPath("$.returnItems[0].productId").value(p1.getId()))
            .andExpect(jsonPath("$.returnItems[0].name").value("Áo"))
            .andExpect(jsonPath("$.returnItems[0].quantity").value(2))
            .andExpect(jsonPath("$.returnItems[0].resolution").value("REFUND"));
        var manager=managerQuery.detail(order.getId());
        assertEquals(2,manager.returnItems().size());
        assertEquals(p1.getId(),manager.returnItems().get(0).productId());
        assertEquals("Áo",manager.returnItems().get(0).name());
        assertNull(manager.returnItems().get(0).restockedAt());
    }
    @Test void legacyReasonOnlyBodyStillReturnsWholeOrder() throws Exception {
        mvc.perform(post("/api/orders/"+order.getId()+"/return").header("Authorization",token())
                .contentType("application/json").content("{\"reason\":\"Hàng lỗi\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.returnStatus").value("REQUESTED"))
            .andExpect(jsonPath("$.returnItems.length()").value(2))
            .andExpect(jsonPath("$.returnItems[0].orderItemId").value(shirt.getId()))
            .andExpect(jsonPath("$.returnItems[1].orderItemId").value(pants.getId()));
        assertEquals(2,returnItems.findByOrderIdOrderByIdAsc(order.getId()).size());
    }
}
