package com.htttdn.crm.service;

import com.htttdn.crm.entity.Product;
import com.htttdn.crm.entity.Supplier;
import com.htttdn.crm.entity.SupplierReceipt;
import com.htttdn.crm.entity.SupplierReceiptItem;
import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.repository.ProductRepository;
import com.htttdn.crm.repository.SupplierReceiptRepository;
import com.htttdn.crm.repository.SupplierRepository;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Phiếu nhập hàng: ghi nhận hóa đơn mua từ nhà cung cấp để tính chi phí, không cộng tồn kho. */
@Service
public class SupplierReceiptService {
    private final SupplierReceiptRepository receipts;
    private final SupplierRepository suppliers;
    private final ProductRepository products;
    private final AuditTrail audit;
    public SupplierReceiptService(SupplierReceiptRepository receipts,SupplierRepository suppliers,ProductRepository products,AuditTrail audit){this.receipts=receipts;this.suppliers=suppliers;this.products=products;this.audit=audit;}
    public record LineRequest(@NotNull Long productId,@Min(1) @Max(1000000) int quantity,@NotNull @DecimalMin("0.0") BigDecimal unitCost){}
    public record CreateRequest(@NotNull Long supplierId,@NotBlank @Size(max=80) String invoiceNumber,@NotNull LocalDate invoiceDate,@Size(max=1000) String note,@NotEmpty List<@jakarta.validation.Valid LineRequest> items){}
    private ApiException bad(String message){return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_SUPPLIER_RECEIPT",message);}
    @Transactional(readOnly=true) public Page<SupplierReceipt> list(Long supplierId,LocalDate from,LocalDate to,int page,int size){
        if(page<0||size<1||size>100||from!=null&&to!=null&&from.isAfter(to))throw bad("Bộ lọc hoặc phân trang không hợp lệ.");
        return receipts.findAll((root,query,cb)->{
            var predicates=new ArrayList<jakarta.persistence.criteria.Predicate>();
            if(supplierId!=null)predicates.add(cb.equal(root.get("supplier").get("id"),supplierId));
            if(from!=null)predicates.add(cb.greaterThanOrEqualTo(root.get("invoiceDate"),from));
            if(to!=null)predicates.add(cb.lessThanOrEqualTo(root.get("invoiceDate"),to));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        },PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"invoiceDate").and(Sort.by(Sort.Direction.DESC,"id"))));
    }
    @Transactional(readOnly=true) public SupplierReceipt get(Long id){
        return receipts.findById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"SUPPLIER_RECEIPT_NOT_FOUND","Không tìm thấy phiếu nhập hàng."));
    }
    @Transactional public SupplierReceipt create(CreateRequest request){
        if(request==null||request.items()==null||request.items().isEmpty())throw bad("Phiếu nhập hàng phải có ít nhất một dòng sản phẩm.");
        if(request.invoiceDate().isAfter(LocalDate.now().plusDays(1)))throw bad("Ngày hóa đơn không hợp lệ.");
        Supplier supplier=suppliers.findById(request.supplierId()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"SUPPLIER_NOT_FOUND","Không tìm thấy nhà cung cấp."));
        SupplierReceipt receipt=new SupplierReceipt();
        receipt.setSupplier(supplier);
        receipt.setInvoiceNumber(request.invoiceNumber().trim());
        receipt.setInvoiceDate(request.invoiceDate());
        String note=request.note()==null?null:request.note().trim();
        receipt.setNote(note==null||note.isEmpty()?null:note);
        var actor=AuditTrail.actor();
        receipt.setCreatedBy(actor==null?null:actor.id());
        BigDecimal total=BigDecimal.ZERO;
        for(LineRequest line:request.items()){
            if(line==null||line.productId()==null)throw bad("Dòng sản phẩm không hợp lệ.");
            Product product=products.findById(line.productId()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"PRODUCT_NOT_FOUND","Không tìm thấy sản phẩm trong phiếu nhập."));
            BigDecimal cost=line.unitCost()==null?BigDecimal.ZERO:line.unitCost();
            if(cost.scale()>2)throw bad("Đơn giá chỉ hỗ trợ tối đa 2 chữ số thập phân.");
            var item=new SupplierReceiptItem(receipt,product,line.quantity(),cost);
            receipt.getItems().add(item);
            total=total.add(item.getLineTotal());
        }
        receipt.setTotalAmount(total);
        receipts.saveAndFlush(receipt);
        if(actor!=null)audit.record("SUPPLIER_RECEIPT_CREATE","SUPPLIER_RECEIPT",receipt.getId(),Map.of(),Map.of("supplierId",supplier.id,"invoiceNumber",receipt.getInvoiceNumber(),"invoiceDate",receipt.getInvoiceDate().toString(),"totalAmount",total,"lines",receipt.getItems().size()));
        return receipt;
    }
}
