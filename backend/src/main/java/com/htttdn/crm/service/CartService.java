package com.htttdn.crm.service;

import com.htttdn.crm.entity.*; import com.htttdn.crm.exception.ApiException; import com.htttdn.crm.repository.*; import org.springframework.http.HttpStatus; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.math.BigDecimal; import java.util.*;

@Service
public class CartService {
    private final CartItemRepository carts; private final ProductRepository products;
    public CartService(CartItemRepository carts,ProductRepository products){this.carts=carts;this.products=products;}
    @Transactional(readOnly=true) public CartSummary get(User customer){return summary(carts.findByCustomerIdOrderByIdAsc(customer.getId()));}
    @Transactional public CartSummary replace(User customer,List<LineRequest> lines){
        carts.deleteByCustomerId(customer.getId());
        if(lines!=null)for(LineRequest line:lines){if(line==null||line.productId()==null||line.quantity()<1)throw invalid();Product p=products.findByIdAndActiveTrue(line.productId()).orElseThrow(()->new ApiException(HttpStatus.BAD_REQUEST,"INVALID_CART","Sản phẩm trong giỏ không còn khả dụng."));if(line.quantity()>p.getStock())throw new ApiException(HttpStatus.CONFLICT,"OUT_OF_STOCK","Sản phẩm "+p.getName()+" không đủ số lượng.");CartItem item=new CartItem();item.setCustomer(customer);item.setProduct(p);item.setQuantity(line.quantity());item.setSize(line.size());item.setColor(line.color());carts.save(item);}
        return get(customer);
    }
    @Transactional public CartSummary add(User customer,Long productId,int quantity,String size,String color){
        if(quantity<1)throw invalid();Product p=products.findByIdAndActiveTrue(productId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"PRODUCT_NOT_FOUND","Không tìm thấy sản phẩm."));CartItem item=carts.findByCustomerIdAndProductIdAndSizeAndColor(customer.getId(),productId,size,color).orElseGet(()->{CartItem x=new CartItem();x.setCustomer(customer);x.setProduct(p);x.setSize(size);x.setColor(color);return x;});if(item.getQuantity()+quantity>p.getStock())throw new ApiException(HttpStatus.CONFLICT,"OUT_OF_STOCK","Sản phẩm không đủ số lượng trong kho.");item.setQuantity(item.getQuantity()+quantity);carts.save(item);return get(customer);
    }
    private ApiException invalid(){return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_CART","Thông tin giỏ hàng chưa hợp lệ.");}
    private CartSummary summary(List<CartItem> items){BigDecimal subtotal=BigDecimal.ZERO;int count=0;List<CartLine> lines=new ArrayList<>();for(CartItem i:items){BigDecimal price=price(i.getProduct());BigDecimal total=price.multiply(BigDecimal.valueOf(i.getQuantity()));subtotal=subtotal.add(total);count+=i.getQuantity();lines.add(new CartLine(i.getProduct().getId(),i.getProduct().getName(),i.getProduct().getImageUrl(),price,i.getQuantity(),total,i.getProduct().getStock(),i.getSize(),i.getColor()));}return new CartSummary(lines,subtotal,count);}
    public static BigDecimal price(Product p){return p.getSalePrice()!=null?p.getSalePrice():p.getPrice();}
    public record LineRequest(Long productId,int quantity,String size,String color){}
    public record CartLine(Long productId,String name,String imageUrl,BigDecimal unitPrice,int quantity,BigDecimal lineTotal,int stock,String size,String color){}
    public record CartSummary(List<CartLine> items,BigDecimal subtotal,int itemCount){}
}
