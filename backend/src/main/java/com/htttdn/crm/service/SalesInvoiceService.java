package com.htttdn.crm.service;

import com.htttdn.crm.entity.Order;
import com.htttdn.crm.entity.OrderItem;
import com.htttdn.crm.entity.SalesInvoice;
import com.htttdn.crm.entity.SalesInvoiceItem;
import com.htttdn.crm.entity.User;
import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.repository.OrderRepository;
import com.htttdn.crm.repository.SalesInvoiceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Hóa đơn bán hàng: một hóa đơn cho mỗi đơn hàng, chụp lại dữ liệu tại thời điểm phát hành. */
@Service
public class SalesInvoiceService {
    private final SalesInvoiceRepository invoices;
    private final OrderRepository orders;
    private final AuditTrail audit;
    private final SettingsService settings;
    public SalesInvoiceService(SalesInvoiceRepository invoices,OrderRepository orders,AuditTrail audit,SettingsService settings){
        this.invoices=invoices;this.orders=orders;this.audit=audit;this.settings=settings;
    }
    public record Line(String productName,String size,String color,int quantity,BigDecimal unitPrice,BigDecimal lineTotal){}
    public record View(Long id,String invoiceNumber,Long orderId,Long orderCode,String orderStatus,Instant issuedAt,
                       String sellerName,String sellerAddress,String sellerPhone,String sellerTaxCode,
                       String buyerName,String buyerEmail,String buyerPhone,String deliveryAddress,
                       BigDecimal subtotal,BigDecimal discountAmount,BigDecimal totalAmount,
                       String paymentMethod,String paymentStatus,String note,List<Line> items,String memberTier,int memberDiscountPercent,BigDecimal memberDiscountAmount){}
    public record IssueRequest(@jakarta.validation.constraints.Size(max=1000) String note){}
    private ApiException conflict(String message){return new ApiException(HttpStatus.CONFLICT,"INVOICE_CONFLICT",message);}
    @Transactional(readOnly=true) public Page<View> list(String keyword,int page,int size){
        if(page<0||size<1||size>100||keyword!=null&&keyword.length()>120)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_FILTER","Bộ lọc hoặc phân trang không hợp lệ.");
        return invoices.search(keyword==null?"":keyword.trim(),PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"issuedAt").and(Sort.by(Sort.Direction.DESC,"id")))).map(this::view);
    }
    @Transactional(readOnly=true) public View get(Long id){return view(invoices.findById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"INVOICE_NOT_FOUND","Không tìm thấy hóa đơn.")));}
    @Transactional(readOnly=true) public Optional<View> forOrder(Long orderId){return invoices.findByOrderId(orderId).map(this::view);}
    @Transactional(readOnly=true) public Optional<View> forCustomer(User customer,Long orderId){
        Order order=orders.findByIdAndCustomerId(orderId,customer.getId()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","Không tìm thấy đơn hàng."));
        return invoices.findByOrderId(order.getId()).map(this::view);
    }
    @Transactional public View issue(Long orderId,IssueRequest request){
        var actor=AuditTrail.actor();
        if(actor==null||!"MANAGER".equals(actor.role()))throw new ApiException(HttpStatus.FORBIDDEN,"MANAGER_REQUIRED","Chỉ Manager được phát hành hóa đơn.");
        Order order=orders.findForStatusUpdate(orderId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","Không tìm thấy đơn hàng."));
        var existing=invoices.findByOrderId(orderId);
        if(existing.isPresent())return view(existing.get());
        if("CANCELLED".equals(order.getStatus()))throw conflict("Không thể phát hành hóa đơn cho đơn đã hủy.");
        if(order.getItems()==null||order.getItems().isEmpty())throw conflict("Đơn hàng chưa có sản phẩm để lập hóa đơn.");
        User customer=order.getCustomer();
        SalesInvoice invoice=new SalesInvoice();
        invoice.setInvoiceNumber("HD-"+order.getOrderCode());
        invoice.setOrder(order);invoice.setCustomer(customer);invoice.setIssuedAt(Instant.now());
        invoice.setSellerName(settings.get("invoice.seller.name"));
        invoice.setSellerAddress(blankToNull(settings.get("invoice.seller.address")));
        invoice.setSellerPhone(blankToNull(settings.get("invoice.seller.phone")));
        invoice.setSellerTaxCode(blankToNull(settings.get("invoice.seller.taxCode")));
        invoice.setBuyerName(customer.getFullName());invoice.setBuyerEmail(customer.getEmail());invoice.setBuyerPhone(customer.getPhone());
        invoice.setDeliveryAddress(order.getDeliveryAddress());
        BigDecimal subtotal=BigDecimal.ZERO;
        for(OrderItem item:order.getItems()){
            var line=new SalesInvoiceItem();
            line.setVariantId(item.getVariantId());line.setInvoice(invoice);line.setProduct(item.getProduct());line.setProductName(item.getProduct().getName());
            line.setSize(item.getSize());line.setColor(item.getColor());line.setQuantity(item.getQuantity());line.setUnitPrice(item.getUnitPrice());
            BigDecimal lineTotal=item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            line.setLineTotal(lineTotal);subtotal=subtotal.add(lineTotal);invoice.getItems().add(line);
        }
        BigDecimal discount=order.getDiscountAmount()==null?BigDecimal.ZERO:order.getDiscountAmount();
        invoice.setSubtotal(subtotal);invoice.setDiscountAmount(discount);invoice.setTotalAmount(order.getTotalAmount());
        invoice.setPaymentMethod(order.getPaymentMethod());invoice.setPaymentStatus(order.getPaymentStatus());
        invoice.setNote(blankToNull(request==null?null:request.note()));invoice.setCreatedBy(actor.id());
        invoices.saveAndFlush(invoice);
        audit.record("INVOICE_ISSUE","ORDER",orderId,Map.of(),Map.of("invoiceId",invoice.getId(),"invoiceNumber",invoice.getInvoiceNumber(),"totalAmount",invoice.getTotalAmount()));
        return view(invoice);
    }
    private static String blankToNull(String value){return value==null||value.isBlank()?null:value.trim();}
    private View view(SalesInvoice i){
        Order o=i.getOrder();
        List<Line> lines=i.getItems().stream().map(x->new Line(x.getProductName(),x.getSize(),x.getColor(),x.getQuantity(),x.getUnitPrice(),x.getLineTotal())).toList();
        return new View(i.getId(),i.getInvoiceNumber(),o.getId(),o.getOrderCode(),o.getStatus(),i.getIssuedAt(),i.getSellerName(),i.getSellerAddress(),i.getSellerPhone(),i.getSellerTaxCode(),i.getBuyerName(),i.getBuyerEmail(),i.getBuyerPhone(),i.getDeliveryAddress(),i.getSubtotal(),i.getDiscountAmount(),i.getTotalAmount(),i.getPaymentMethod(),i.getPaymentStatus(),i.getNote(),lines,o.memberTier,o.memberDiscountPercent,o.memberDiscountAmount);
    }
}
