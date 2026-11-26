package com.htttdn.crm.service;

import com.htttdn.crm.entity.*; import com.htttdn.crm.exception.ApiException; import com.htttdn.crm.repository.*; import org.springframework.http.HttpStatus; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.math.BigDecimal; import java.util.*;

@Service
public class CartService {
    private final CartItemRepository carts; private final ProductRepository products;
    public CartService(CartItemRepository carts,ProductRepository products){this.carts=carts;this.products=products;}
    @Transactional(readOnly=true) public CartSummary get(User customer){return summary(carts.findByCustomerIdOrderByIdAsc(customer.getId()));}
    @Transactional public synchronized CartSummary replace(User customer,List<LineRequest> lines){
        Map<VariantKey,LineRequest> requested=new LinkedHashMap<>();
        if(lines!=null)for(LineRequest line:lines){
            if(line==null||line.productId()==null||line.quantity()<1)throw invalid();
            String size=normalize(line.size()),color=normalize(line.color());
            VariantKey key=new VariantKey(line.productId(),size,color);
            requested.merge(key,new LineRequest(line.productId(),line.quantity(),size,color,line.variantId()),
                (first,second)->new LineRequest(first.productId(),Math.addExact(first.quantity(),second.quantity()),size,color,first.variantId()));
        }
        Map<VariantKey,Product> requestedProducts=new LinkedHashMap<>();
        for(Map.Entry<VariantKey,LineRequest> entry:requested.entrySet()){
            LineRequest line=entry.getValue();
            Product product=products.findByIdAndActiveTrue(line.productId()).orElseThrow(()->new ApiException(HttpStatus.BAD_REQUEST,"INVALID_CART","Sản phẩm trong giỏ không còn khả dụng."));
            var variant=variant(product,sizeOf(line),normalize(line.color()));
            if(line.variantId()!=null&&(variant==null||!line.variantId().equals(variant.getId())))throw invalid();
            if(line.quantity()>(variant==null?product.getStock():variant.getStock()))throw new ApiException(HttpStatus.CONFLICT,"OUT_OF_STOCK","Sản phẩm "+product.getName()+" không đủ số lượng.");
            requestedProducts.put(entry.getKey(),product);
        }

        Map<String,Long> perVariant=new HashMap<>();
        for(var entry:requested.entrySet()){
            var p=requestedProducts.get(entry.getKey());var line=entry.getValue();var v=variant(p,line.size(),line.color());
            if(v!=null){long count=perVariant.merge(p.getId()+":"+v.getId(),(long)line.quantity(),Long::sum);if(count>v.getStock())throw new ApiException(HttpStatus.CONFLICT,"OUT_OF_STOCK","Biến thể không đủ tồn kho.");}
        }
        Map<Long,Long> totals=new HashMap<>();
        for(var entry:requested.entrySet()){var p=requestedProducts.get(entry.getKey());long total=totals.merge(p.getId(),(long)entry.getValue().quantity(),Long::sum);if(total>p.getStock())throw new ApiException(HttpStatus.CONFLICT,"OUT_OF_STOCK","Sản phẩm không đủ tồn kho.");}
        Map<VariantKey,CartItem> existing=new LinkedHashMap<>();
        for(CartItem item:carts.findByCustomerIdOrderByIdAsc(customer.getId()))
            existing.put(new VariantKey(item.getProduct().getId(),normalize(item.getSize()),normalize(item.getColor())),item);
        for(Map.Entry<VariantKey,LineRequest> entry:requested.entrySet()){
            VariantKey key=entry.getKey(); LineRequest line=entry.getValue();
            CartItem item=existing.remove(key);
            if(item==null){item=new CartItem();item.setCustomer(customer);item.setProduct(requestedProducts.get(key));}
            item.setVariantId(variantId(requestedProducts.get(key),key.size(),key.color()));item.setQuantity(line.quantity());item.setSize(key.size());item.setColor(key.color());carts.save(item);
        }
        if(!existing.isEmpty())carts.deleteAll(existing.values());
        carts.flush();
        return get(customer);
    }
    @Transactional public CartSummary add(User customer,Long productId,int quantity,String size,String color){return add(customer,productId,quantity,size,color,null);}
    @Transactional public synchronized CartSummary add(User customer,Long productId,int quantity,String size,String color,Long variantId){
        if(quantity<1)throw invalid();
        Product p=products.findByIdAndActiveTrue(productId).orElseThrow(this::invalid);
        var selected=variant(p,size,color);
        if(variantId!=null&&(selected==null||!variantId.equals(selected.getId())))throw invalid();
        var lines=new ArrayList<LineRequest>();
        for(var i:carts.findByCustomerIdOrderByIdAsc(customer.getId()))lines.add(new LineRequest(i.getProduct().getId(),i.getQuantity(),i.getSize(),i.getColor()));
        lines.add(new LineRequest(productId,quantity,size,color,variantId));
        return replace(customer,lines);
    }
    private ProductVariant displayVariant(Product p,String size,String color){return p.getVariants().stream().filter(v->size!=null&&color!=null&&v.getSize().equalsIgnoreCase(size.trim())&&v.getColor().equalsIgnoreCase(color.trim())).findFirst().orElse(null);}
    private Long displayVariantId(Product p,String size,String color){var v=displayVariant(p,size,color);return v==null?null:v.getId();}
    private int displayStock(Product p,String size,String color){if(!p.isActive())return 0;if(!p.isVariantsAllocated())return p.getStock();var v=displayVariant(p,size,color);return v==null||!v.isActive()?0:v.getStock();}
    private String sizeOf(LineRequest line){return normalize(line.size());}
    private ProductVariant variant(Product p,String size,String color){if(!p.isVariantsAllocated())return null;return p.getVariants().stream().filter(v->v.isActive()&&size!=null&&color!=null&&v.getSize().equalsIgnoreCase(size.trim())&&v.getColor().equalsIgnoreCase(color.trim())).findFirst().orElseThrow(this::invalid);}
    private Long variantId(Product p,String size,String color){var v=variant(p,size,color);return v==null?null:v.getId();}
    private int available(Product p,String size,String color){var v=variant(p,size,color);return v==null?p.getStock():v.getStock();}
    private ApiException invalid(){return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_CART","Thông tin giỏ hàng chưa hợp lệ.");}
    private String normalize(String value){return value==null||value.isBlank()?null:value.trim();}
    private CartSummary summary(List<CartItem> items){BigDecimal subtotal=BigDecimal.ZERO;int count=0;List<CartLine> lines=new ArrayList<>();for(CartItem i:items){BigDecimal price=price(i.getProduct());BigDecimal total=price.multiply(BigDecimal.valueOf(i.getQuantity()));subtotal=subtotal.add(total);count+=i.getQuantity();lines.add(new CartLine(i.getProduct().getId(),i.getProduct().getName(),i.getProduct().getImageUrl(),price,i.getQuantity(),total,displayStock(i.getProduct(),i.getSize(),i.getColor()),i.getSize(),i.getColor(),displayVariantId(i.getProduct(),i.getSize(),i.getColor())));}return new CartSummary(lines,subtotal,count);}
    public static BigDecimal price(Product p){return p.getSalePrice()!=null?p.getSalePrice():p.getPrice();}
    public record LineRequest(Long productId,int quantity,String size,String color,Long variantId){public LineRequest(Long productId,int quantity,String size,String color){this(productId,quantity,size,color,null);}}
    private record VariantKey(Long productId,String size,String color){}
    public record CartLine(Long productId,String name,String imageUrl,BigDecimal unitPrice,int quantity,BigDecimal lineTotal,int stock,String size,String color,Long variantId){public CartLine(Long productId,String name,String imageUrl,BigDecimal unitPrice,int quantity,BigDecimal lineTotal,int stock,String size,String color){this(productId,name,imageUrl,unitPrice,quantity,lineTotal,stock,size,color,null);}}
    public record CartSummary(List<CartLine> items,BigDecimal subtotal,int itemCount){}
}
