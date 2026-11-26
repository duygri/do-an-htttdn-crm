package com.htttdn.crm.service;
import com.htttdn.crm.entity.*;
import com.htttdn.crm.exception.ApiException;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import java.util.*;
@Service
public class ProductVariantService {
 private final StockService stock; private final EntityManager em; private final AuditTrail audit;
 public ProductVariantService(StockService s,EntityManager e,AuditTrail a){stock=s;em=e;audit=a;}
 public record AllocationLine(String size,String color,int stock){}
 public record Allocation(int expectedStock,long expectedVersion,List<AllocationLine> items){}
 public record Adjustment(int expectedStock,long expectedVersion,int quantity,String note){}
 private ApiException conflict(){return new ApiException(HttpStatus.CONFLICT,"STOCK_CHANGED","Tồn kho đã thay đổi. Vui lòng tải lại.");}
 @Transactional public Product allocate(Long id,Allocation request,AuditTrail.Actor actor){
  if(actor==null)throw new IllegalStateException("Verified actor required");
  Product p=stock.lockProduct(id).orElseThrow();
  if(request==null||p.isVariantsAllocated()||request.expectedStock()!=p.getStock()||request.expectedVersion()!=p.getStockVersion())throw conflict();
  if(request.items()==null||request.items().isEmpty()||request.items().size()>500)throw invalid();
  Set<String> keys=new HashSet<>(); long total=0;
  for(var line:request.items()){
   if(line==null||line.size()==null||line.color()==null||line.stock()<0)throw invalid();
   String size=line.size().trim(),color=line.color().trim();
   if(size.isEmpty()||color.isEmpty()||size.length()>100||color.length()>100||!option(p.getSizes(),size)||!option(p.getColors(),color)||!keys.add(color.toLowerCase(Locale.ROOT)+"\u0000"+size.toLowerCase(Locale.ROOT)))throw invalid();
   total+=line.stock();
  }
  if(total!=p.getStock())throw invalid();
  for(var line:request.items()){var v=new ProductVariant();v.setProduct(p);v.setSize(line.size().trim());v.setColor(line.color().trim());v.setStock(line.stock());p.getVariants().add(v);em.persist(v);}
  em.flush();
  p.setVariantsAllocated(true);p.setStockVersion(Math.incrementExact(p.getStockVersion()));p.setUpdatedAt(java.time.Instant.now());
  audit.recordAs(actor,"VARIANT_ALLOCATE","PRODUCT",id,Map.of("stock",p.getStock()),Map.of("variants",keys.size()));
  em.flush();return p;
 }
 @Transactional public Product adjust(Long id,Long variantId,Adjustment r,AuditTrail.Actor actor){
  if(actor==null)throw new IllegalStateException("Verified actor required");
  Product p=stock.lockProduct(id).orElseThrow();
  if(r==null||r.quantity()<0||r.note()==null||r.note().isBlank()||r.note().length()>1000)throw invalid();
  if(r.expectedVersion()!=p.getStockVersion()||r.expectedStock()!=p.getStock())throw conflict();
  var v=p.getVariants().stream().filter(x->x.getId().equals(variantId)).findFirst().orElseThrow(this::invalid);
  stock.applyVariant(p,variantId,Math.subtractExact(r.quantity(),v.getStock()),"ADJUST","PRODUCT",id,r.note());
  em.flush();return p;
 }
 @Transactional public Product reconcile(Long productId,Long orderItemId,Long variantId,String note,AuditTrail.Actor actor){
  if(actor==null)throw new IllegalStateException("Verified actor required");
  if(note==null||note.isBlank()||note.length()>1000)throw invalid();
  // Order first, then product: same lock order as cancellation and returns.
  var line=em.find(OrderItem.class,orderItemId);
  if(line==null||!line.getProduct().getId().equals(productId))throw invalid();
  em.lock(line.getOrder(),jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
  Product p=stock.lockProduct(productId).orElseThrow();
  em.refresh(line);
  if(line.getVariantId()!=null)throw conflict();
  long matches=p.getVariants().stream().filter(v->line.getSize()!=null&&line.getColor()!=null&&v.getSize().equalsIgnoreCase(line.getSize().trim())&&v.getColor().equalsIgnoreCase(line.getColor().trim())).count();
  if(matches==1)throw new ApiException(HttpStatus.CONFLICT,"LEGACY_VARIANT_RESOLVED","Dòng đơn cũ đã khớp duy nhất màu/size; không cần gán lại lịch sử.");
  if(p.getVariants().stream().noneMatch(v->v.getId().equals(variantId)))throw invalid();
  line.setVariantId(variantId);
  audit.recordAs(actor,"LEGACY_VARIANT_RECONCILE","ORDER_ITEM",orderItemId,Map.of("variantId",""),Map.of("variantId",variantId,"note",note,"productId",productId));
  em.flush();return p;
 }
 public record BatchLine(Long variantId,int quantity){}
 public record Batch(int expectedStock,long expectedVersion,List<BatchLine> items,String note){}
 @Transactional public Product batch(Long id,Batch r,AuditTrail.Actor actor){
  Product p=stock.lockProduct(id).orElseThrow();
  if(actor==null)throw new IllegalStateException("Verified actor required");
  if(r==null||r.items()==null||r.items().isEmpty()||r.note()==null||r.note().isBlank()||r.note().length()>1000)throw invalid();
  if(r.expectedVersion()!=p.getStockVersion()||r.expectedStock()!=p.getStock())throw conflict();
  Set<Long> seen=new HashSet<>();long total=p.getStock();
  for(var line:r.items()){
   if(line==null||line.quantity()<0||!seen.add(line.variantId()))throw invalid();
   var v=p.getVariants().stream().filter(x->x.getId().equals(line.variantId())).findFirst().orElseThrow(this::invalid);
   total+=(long)line.quantity()-v.getStock();
  }
  if(total<0||total>Integer.MAX_VALUE)throw invalid();
  // Decreases first so an intermediate total cannot overflow when the final total is valid.
  var sorted=r.items().stream().sorted(Comparator.comparingLong(l->(long)l.quantity()-p.getVariants().stream().filter(v->v.getId().equals(l.variantId())).findFirst().orElseThrow().getStock())).toList();
  for(var line:sorted){var v=p.getVariants().stream().filter(x->x.getId().equals(line.variantId())).findFirst().orElseThrow();int delta=line.quantity()-v.getStock();if(delta!=0)stock.applyVariant(p,v.getId(),delta,"ADJUST","PRODUCT",id,r.note());}
  em.flush();return p;
 }
 private boolean option(String csv,String value){return csv!=null&&Arrays.stream(csv.split(",")).map(String::trim).anyMatch(value::equalsIgnoreCase);}
 private ApiException invalid(){return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_VARIANT","Thông tin phân bổ biến thể không hợp lệ.");}
}
