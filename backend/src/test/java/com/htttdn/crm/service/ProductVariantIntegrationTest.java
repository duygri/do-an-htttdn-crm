package com.htttdn.crm.service;
import com.htttdn.crm.entity.*;
import com.htttdn.crm.exception.ApiException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=${CRM_COMPLETION_QA_URL:jdbc:h2:mem:variants;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}","spring.datasource.driver-class-name=${CRM_COMPLETION_QA_DRIVER:org.h2.Driver}","spring.datasource.username=${CRM_COMPLETION_QA_USER:sa}","spring.datasource.password=${CRM_COMPLETION_QA_PASSWORD:}","spring.jpa.database-platform=${CRM_COMPLETION_QA_DIALECT:org.hibernate.dialect.H2Dialect}","spring.jpa.hibernate.ddl-auto=${CRM_COMPLETION_QA_DDL:create-drop}","crm.care-reminders.enabled=false"})
@Transactional
class ProductVariantIntegrationTest {
 @Autowired EntityManager em; @Autowired ProductVariantService variants; @Autowired StockService stock;
 @Autowired InventoryQueryService inventory;
 @Test void lowStockChecksEachVariantEvenWhenProductTotalIsAboveThreshold(){
  var p=allocate(product());em.flush();
  var rows=inventory.lowStock(null,null,0,100).getContent().stream().filter(r->r.productId().equals(p.getId())).toList();
  assertEquals(1,rows.size());assertEquals("Blue",rows.getFirst().color());assertEquals("L",rows.getFirst().size());assertEquals(4,rows.getFirst().stock());
 }
 private AuditTrail.Actor actor(){return new AuditTrail.Actor(1L,"Manager","MANAGER");}
 private Product product(){var c=new Category();c.setName("Variants "+java.util.UUID.randomUUID());em.persist(c);var p=new Product();p.setName("Variant shirt");p.setCategoryEntity(c);p.setPrice(BigDecimal.TEN);p.setSizes("M,L");p.setColors("Black,Blue");p.setStock(10);em.persist(p);em.flush();return p;}
 private Product allocate(Product p){return variants.allocate(p.getId(),new ProductVariantService.Allocation(10,0,List.of(new ProductVariantService.AllocationLine("M","Black",6),new ProductVariantService.AllocationLine("L","Blue",4))),actor());}
 @Test void allocationPreservesTotalAndRejectsReplay(){var p=allocate(product());assertEquals(10,p.getStock());assertEquals(1,p.getStockVersion());assertEquals(10,p.getVariants().stream().mapToInt(ProductVariant::getStock).sum());assertThrows(ApiException.class,()->allocate(p));}
 @Test void refusesStaleAllocation(){var p=product();stock.apply(p,-1,"SALE","ORDER",1L,"test");assertThrows(ApiException.class,()->allocate(p));}
 @Test void rejectsNormalizedDuplicatesAndWrongTotal(){var p=product();assertThrows(ApiException.class,()->variants.allocate(p.getId(),new ProductVariantService.Allocation(10,0,List.of(new ProductVariantService.AllocationLine("M","Black",5),new ProductVariantService.AllocationLine("m","black",5))),actor()));assertFalse(p.isVariantsAllocated());}
 @Test void batchUpdatesBothTotalsAndLedger(){var p=allocate(product());var v=p.getVariants().getFirst();variants.batch(p.getId(),new ProductVariantService.Batch(10,1,List.of(new ProductVariantService.BatchLine(v.getId(),2)),"Count corrected"),actor());assertEquals(6,p.getStock());assertEquals(2,v.getStock());var m=em.createQuery("select m from StockMovement m where m.product.id=:id",StockMovement.class).setParameter("id",p.getId()).getSingleResult();assertEquals(v.getId(),m.getVariantId());assertEquals(10,m.getStockBefore());assertEquals(6,m.getStockAfter());}
 @Test void commonAdjustmentBlockedAndInactiveNotSellable(){var p=allocate(product());assertThrows(ApiException.class,()->stock.adjust(p,3,"test"));var v=p.getVariants().getFirst();v.setActive(false);assertThrows(ApiException.class,()->stock.resolve(p," m "," black "));}
 @Test void metadataCannotRemoveActiveVariantOptions(){var p=allocate(product());assertThrows(ApiException.class,()->p.setSizes("L"));assertThrows(ApiException.class,()->p.setColors("Blue"));}
 @Test void legacyCancellationMatchesWithoutBackfillingAndIsIdempotent(){var p=allocate(product());var o=new Order();o.setOrderCode(123L);var i=new OrderItem();i.setProduct(p);i.setOrder(o);i.setQuantity(2);i.setSize(" m ");i.setColor("black");o.getItems().add(i);stock.release(o);assertNull(i.getVariantId());assertEquals(12,p.getStock());assertEquals(8,p.getVariants().getFirst().getStock());stock.release(o);assertEquals(12,p.getStock());}
 @Test void unresolvedLegacyCancellationRequiresReconciliation(){var p=allocate(product());var o=new Order();var i=new OrderItem();i.setProduct(p);i.setQuantity(2);o.getItems().add(i);assertThrows(ApiException.class,()->stock.release(o));assertFalse(o.isStockReleased());assertEquals(10,p.getStock());}
 @Test void sellingUsesVariantBucketAndPreventsVariantOversell(){var p=allocate(product());var v=p.getVariants().getFirst();var o=new Order();o.setOrderCode(1234L);var line=new OrderItem();line.setProduct(p);line.setVariantId(v.getId());line.setQuantity(2);o.getItems().add(line);stock.sell(o);assertEquals(8,p.getStock());assertEquals(4,v.getStock());line.setQuantity(5);assertThrows(ApiException.class,()->stock.sell(o));assertEquals(8,p.getStock());assertEquals(4,v.getStock());}
 @Test void allocationDoesNotAssignHistoricalVariants(){
  var p=product();var u=new User();u.setFullName("Legacy buyer");u.setEmail("legacy-"+java.util.UUID.randomUUID()+"@example.test");u.setPasswordHash("test");u.setRole("CUSTOMER");em.persist(u);
  var o=new Order();o.setCustomer(u);o.setOrderCode(987654321L);o.setDeliveryAddress("Test address");o.setTotalAmount(BigDecimal.TEN);em.persist(o);
  var line=new OrderItem();line.setOrder(o);line.setProduct(p);line.setQuantity(1);line.setUnitPrice(BigDecimal.TEN);o.getItems().add(line);em.persist(line);em.flush();
  allocate(p);assertNull(line.getVariantId());
  variants.reconcile(p.getId(),line.getId(),p.getVariants().getFirst().getId(),"Verified archived receipt",actor());
  assertEquals(p.getVariants().getFirst().getId(),line.getVariantId());
  assertEquals(1,em.createQuery("select count(a) from AuditLog a where a.action='LEGACY_VARIANT_RECONCILE'",Long.class).getSingleResult());
 }
 @Test void batchValidatesEveryLineBeforeMutation(){var p=allocate(product());assertThrows(ApiException.class,()->variants.batch(p.getId(),new ProductVariantService.Batch(10,1,List.of(new ProductVariantService.BatchLine(p.getVariants().getFirst().getId(),0),new ProductVariantService.BatchLine(-1L,2)),"test"),actor()));assertEquals(10,p.getStock());assertEquals(6,p.getVariants().getFirst().getStock());}
}
