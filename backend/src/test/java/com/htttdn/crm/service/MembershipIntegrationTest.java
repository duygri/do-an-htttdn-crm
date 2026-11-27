package com.htttdn.crm.service;
import com.htttdn.crm.entity.*;import com.htttdn.crm.exception.ApiException;
import jakarta.persistence.EntityManager;import org.junit.jupiter.api.Test;import org.junit.jupiter.params.ParameterizedTest;import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.boot.test.mock.mockito.MockBean;import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;import java.util.*;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
@SpringBootTest(properties={"spring.datasource.url=${CRM_COMPLETION_QA_URL:jdbc:h2:mem:membership;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}","spring.datasource.driver-class-name=${CRM_COMPLETION_QA_DRIVER:org.h2.Driver}","spring.datasource.username=${CRM_COMPLETION_QA_USER:sa}","spring.datasource.password=${CRM_COMPLETION_QA_PASSWORD:}","spring.jpa.database-platform=${CRM_COMPLETION_QA_DIALECT:org.hibernate.dialect.H2Dialect}","spring.jpa.hibernate.ddl-auto=${CRM_COMPLETION_QA_DDL:create-drop}","crm.care-reminders.enabled=false"})
@Transactional
class MembershipIntegrationTest {
 @Autowired EntityManager em;@Autowired MembershipService membership;@Autowired OrderService orders;@Autowired CustomerProfileService profiles;
 @MockBean PayosService payos;
 User customer(){var c=new User();c.setFullName("Membership QA");c.setEmail(UUID.randomUUID()+"@example.test");c.setPasswordHash("test");c.setRole("CUSTOMER");em.persist(c);return c;}
 Order history(User c,String status,String amount){var o=new Order();o.setCustomer(c);o.setOrderCode(Math.abs(UUID.randomUUID().getMostSignificantBits()%1000000000000L));o.setStatus(status);o.setTotalAmount(new BigDecimal(amount));em.persist(o);return o;}
 Product product(){var category=new Category();category.setName("Membership QA");em.persist(category);var p=new Product();p.setCategoryEntity(category);p.setName("QA tier product");p.setPrice(new BigDecimal("200000"));p.setSalePrice(new BigDecimal("100000"));p.setStock(20);p.setActive(true);p.setSizes("M,L");p.setColors("Blue");em.persist(p);return p;}
 StoreVoucher voucher(String min){var v=new StoreVoucher();v.setCode("QA"+UUID.randomUUID().toString().substring(0,8));v.setDiscountType("PERCENTAGE");v.setDiscountValue(BigDecimal.TEN);v.setMinOrderAmount(new BigDecimal(min));v.setActive(true);em.persist(v);return v;}
 @Test void aggregatesOnlyCompletedWithoutJoiningItemsAndKeepsReturns(){
  var c=customer();history(c,"PENDING","100000000");history(c,"DELIVERED","100000000");history(c,"CANCELLED","100000000");var o=history(c,"COMPLETED","5000000");o.setReturnStatus("COMPLETED");var product=product();
  for(String size:List.of("M","L")){var line=new OrderItem();line.setOrder(o);line.setProduct(product);line.setQuantity(1);line.setSize(size);line.setColor("Blue");line.setUnitPrice(new BigDecimal("2500000"));em.persist(line);o.getItems().add(line);}em.flush();
  assertEquals("GOLD",membership.forCustomer(c.getId()).tier());assertEquals(membership.forCustomer(c.getId()),profiles.profile(c.getId()).membership());
 }
 @ParameterizedTest @ValueSource(strings={"COD","PAYOS"}) void quoteAndOrderUseSalePriceThenTierThenVoucher(String method){
  var c=customer();history(c,"COMPLETED","10000000");var p=product();var v=voucher("100000");em.flush();
  var items=List.of(new OrderService.RequestedItem(p.getId(),2,"M","Blue"));
  var quote=orders.preview(c,new OrderService.PreviewRequest(items,v.getCode()));
  assertEquals(0,quote.subtotal().compareTo(new BigDecimal("200000")));assertEquals(0,quote.memberDiscountAmount().compareTo(new BigDecimal("20000")));assertEquals(0,quote.discountAmount().compareTo(new BigDecimal("18000")));
  when(payos.createPaymentLink(any())).thenReturn(new PayosService.Link("fake","https://example.test/pay","fake"));
  var result=orders.create(c,new OrderService.CheckoutRequest("QA address",method,v.getCode(),items));
  assertEquals(0,result.order().totalAmount().compareTo(quote.totalAmount()));assertEquals("DIAMOND",result.order().memberTier());assertEquals(10,result.order().memberDiscountPercent());
  history(c,"COMPLETED","5000000");em.flush();assertEquals(0,result.order().totalAmount().compareTo(new BigDecimal("162000")));
 }
 @Test void voucherMinimumUsesPostTierAmountAndPreviewNeverReserves(){
  var c=customer();history(c,"COMPLETED","10000000");var p=product();var v=voucher("95000");em.flush();var items=List.of(new OrderService.RequestedItem(p.getId(),1,"M","Blue"));
  assertThrows(ApiException.class,()->orders.preview(c,new OrderService.PreviewRequest(items,v.getCode())));assertEquals(0,v.getUsedCount());assertEquals(20,p.getStock());
 }
 @Test void oldOrderDefaultsAndNewCustomerAreBronze(){var c=customer();var o=history(c,"DELIVERED","100000");em.flush();assertNull(o.memberTier);assertEquals(0,o.memberDiscountAmount.signum());assertEquals("BRONZE",membership.forCustomer(c.getId()).tier());}
 @Test void previouslyIssuedPointsVoucherRemainsValidAndCanMakeOrderFree(){
  var c=customer();history(c,"COMPLETED","10000000");var p=product();var v=voucher("0");v.setCode("DIEM-"+UUID.randomUUID().toString().substring(0,8));v.setOwnerCustomerId(c.getId());v.setUsageLimit(1);v.setPerCustomerLimit(1);v.setDiscountType("FIXED_AMOUNT");v.setDiscountValue(new BigDecimal("90000"));em.flush();
  var items=List.of(new OrderService.RequestedItem(p.getId(),1,"M","Blue"));var quote=orders.preview(c,new OrderService.PreviewRequest(items,v.getCode()));assertEquals(0,quote.totalAmount().signum());assertEquals(0,v.getUsedCount());
  var result=orders.create(c,new OrderService.CheckoutRequest("QA address","PAYOS",v.getCode(),items));assertEquals(0,result.order().totalAmount().signum());assertEquals("PAID",result.order().paymentStatus());verify(payos,never()).createPaymentLink(any());assertEquals(1,v.getUsedCount());
 }
}
