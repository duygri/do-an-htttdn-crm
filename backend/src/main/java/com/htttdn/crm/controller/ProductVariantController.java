package com.htttdn.crm.controller;
import com.htttdn.crm.entity.Product;
import com.htttdn.crm.service.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/manager/products")
public class ProductVariantController {
 @org.springframework.beans.factory.annotation.Autowired private com.htttdn.crm.repository.ProductRepository products;
 @GetMapping("/variants.csv") @org.springframework.transaction.annotation.Transactional(readOnly=true)
 public org.springframework.http.ResponseEntity<String> csv(){
  var rows=new java.util.ArrayList<String>();rows.add(com.htttdn.crm.util.Csv.line("productId","productName","variantId","color","size","stock","stockVersion"));
  products.findAll(org.springframework.data.domain.Sort.by("id")).forEach(p->{if(p.isVariantsAllocated())p.getVariants().forEach(v->rows.add(com.htttdn.crm.util.Csv.line(p.getId(),p.getName(),v.getId(),v.getColor(),v.getSize(),v.getStock(),p.getStockVersion())));else rows.add(com.htttdn.crm.util.Csv.line(p.getId(),p.getName(),null,null,null,p.getStock(),p.getStockVersion()));});
  return com.htttdn.crm.util.Csv.response("ton-kho-bien-the.csv",rows);
 }
 @GetMapping("/{id}") @org.springframework.transaction.annotation.Transactional(readOnly=true) public Product detail(@PathVariable Long id){return products.findById(id).orElseThrow(()->new com.htttdn.crm.exception.ApiException(org.springframework.http.HttpStatus.NOT_FOUND,"PRODUCT_NOT_FOUND","Không tìm thấy sản phẩm.")).prepareForView();}
 @PatchMapping("/{id}/variants/stock") public Product batch(@PathVariable Long id,@RequestBody ProductVariantService.Batch r,@RequestAttribute(AuditTrail.ACTOR_ATTRIBUTE) AuditTrail.Actor actor){return variants.batch(id,r,actor);}
 @org.springframework.beans.factory.annotation.Autowired private jakarta.persistence.EntityManager em;
 public record LegacyLine(Long orderItemId,Long orderId,long orderCode,String size,String color,int quantity){}
 @GetMapping("/{id}/variants/legacy-lines") @org.springframework.transaction.annotation.Transactional(readOnly=true)
 public java.util.List<LegacyLine> legacy(@PathVariable Long id){
  var p=products.findById(id).orElseThrow();
  return em.createQuery("select i from OrderItem i join fetch i.order where i.product.id=:id and i.variantId is null order by i.id",com.htttdn.crm.entity.OrderItem.class).setParameter("id",id).getResultList().stream()
   .filter(i->p.getVariants().stream().filter(v->i.getSize()!=null&&i.getColor()!=null&&v.getSize().equalsIgnoreCase(i.getSize().trim())&&v.getColor().equalsIgnoreCase(i.getColor().trim())).count()!=1)
   .map(i->new LegacyLine(i.getId(),i.getOrder().getId(),i.getOrder().getOrderCode(),i.getSize(),i.getColor(),i.getQuantity())).toList();
 }
 public record Reconciliation(Long orderItemId,Long variantId,String note){}
 @PostMapping("/{id}/variants/reconcile") public Product reconcile(@PathVariable Long id,@RequestBody Reconciliation r,@RequestAttribute(AuditTrail.ACTOR_ATTRIBUTE) AuditTrail.Actor actor){return variants.reconcile(id,r.orderItemId(),r.variantId(),r.note(),actor);}
 private final ProductVariantService variants;
 public ProductVariantController(ProductVariantService v){variants=v;}
 @PostMapping("/{id}/variants/allocate") public Product allocate(@PathVariable Long id,@RequestBody ProductVariantService.Allocation r,@RequestAttribute(AuditTrail.ACTOR_ATTRIBUTE) AuditTrail.Actor actor){return variants.allocate(id,r,actor);}
 @PatchMapping("/{id}/variants/{variantId}/stock") public Product adjust(@PathVariable Long id,@PathVariable Long variantId,@RequestBody ProductVariantService.Adjustment r,@RequestAttribute(AuditTrail.ACTOR_ATTRIBUTE) AuditTrail.Actor actor){return variants.adjust(id,variantId,r,actor);}
}
