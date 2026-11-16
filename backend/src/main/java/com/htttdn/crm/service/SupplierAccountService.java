package com.htttdn.crm.service;

import com.htttdn.crm.entity.Product;
import com.htttdn.crm.entity.Supplier;
import com.htttdn.crm.entity.SupplierPayment;
import com.htttdn.crm.entity.SupplierReceipt;
import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.repository.ProductRepository;
import com.htttdn.crm.repository.SupplierPaymentRepository;
import com.htttdn.crm.repository.SupplierReceiptRepository;
import com.htttdn.crm.repository.SupplierRepository;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Công nợ phải trả nhà cung cấp: tổng mua theo phiếu nhập trừ các lần thanh toán. */
@Service
public class SupplierAccountService {
    private final SupplierRepository suppliers;
    private final SupplierReceiptRepository receipts;
    private final SupplierPaymentRepository payments;
    private final ProductRepository products;
    private final AuditTrail audit;

    public SupplierAccountService(SupplierRepository suppliers, SupplierReceiptRepository receipts,
            SupplierPaymentRepository payments, ProductRepository products, AuditTrail audit) {
        this.suppliers = suppliers;
        this.receipts = receipts;
        this.payments = payments;
        this.products = products;
        this.audit = audit;
    }

    public record SupplierDetail(Long id, String code, String name, String email, String phone, String address,
            String notes, boolean active, Instant createdAt, long productCount, long receiptCount,
            BigDecimal totalPurchase, BigDecimal totalPaid, BigDecimal outstanding, LocalDate lastReceiptDate,
            List<ProductRow> products, List<ReceiptRow> receipts) {}

    public record ProductRow(Long id, String name, int stock, BigDecimal price, boolean active) {}

    public record ReceiptRow(Long id, String invoiceNumber, LocalDate invoiceDate, BigDecimal totalAmount,
            BigDecimal paidAmount, BigDecimal outstanding) {}

    public record PaymentRequest(@NotNull Long receiptId, @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotNull LocalDate paidAt, @NotBlank @Size(max = 30) String method, @Size(max = 500) String note) {}

    public record PaymentView(Long id, Long receiptId, String receiptNumber, BigDecimal amount, LocalDate paidAt,
            String method, String note, Instant createdAt) {}

    private ApiException bad(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private Supplier supplier(Long supplierId) {
        return suppliers.findById(supplierId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SUPPLIER_NOT_FOUND", "Không tìm thấy nhà cung cấp."));
    }

    @Transactional(readOnly = true)
    public SupplierDetail detail(Long supplierId) {
        Supplier supplier = supplier(supplierId);
        List<SupplierReceipt> rows = receipts.findTop10BySupplierIdOrderByInvoiceDateDescIdDesc(supplierId);
        BigDecimal totalPurchase = BigDecimal.ZERO;
        LocalDate lastReceiptDate = null;
        for (SupplierReceipt receipt : receipts.findAll(
                (root, query, cb) -> cb.equal(root.get("supplier").get("id"), supplierId))) {
            totalPurchase = totalPurchase.add(receipt.getTotalAmount());
            if (lastReceiptDate == null || receipt.getInvoiceDate().isAfter(lastReceiptDate)) {
                lastReceiptDate = receipt.getInvoiceDate();
            }
        }
        BigDecimal totalPaid = payments.paidForSupplier(supplierId);
        long receiptCount = receipts.count((root, query, cb) -> cb.equal(root.get("supplier").get("id"), supplierId));
        List<ProductRow> productRows = products.findTop50BySupplierIdOrderByCreatedAtDescIdDesc(supplierId)
            .stream().map(this::productRow).toList();
        List<ReceiptRow> receiptRows = rows.stream().map(this::receiptRow).toList();
        return new SupplierDetail(supplier.id, supplier.code, supplier.name, supplier.email, supplier.phone,
            supplier.address, supplier.notes, supplier.active, supplier.createdAt,
            products.countBySupplierId(supplierId), receiptCount, totalPurchase, totalPaid,
            totalPurchase.subtract(totalPaid), lastReceiptDate, productRows, receiptRows);
    }

    @Transactional(readOnly = true)
    public Page<PaymentView> payments(Long supplierId, int page, int size) {
        supplier(supplierId);
        if (page < 0 || size < 1 || size > 100) throw bad("INVALID_PAGINATION", "Phân trang không hợp lệ.");
        return payments.findBySupplierIdOrderByPaidAtDescIdDesc(supplierId, PageRequest.of(page, size))
            .map(this::paymentView);
    }

    @Transactional
    public PaymentView pay(Long supplierId, PaymentRequest request, AuditTrail.Actor actor) {
        if (actor == null) throw new IllegalStateException("Verified audit actor required");
        if (request == null || request.receiptId() == null || request.amount() == null
                || request.amount().signum() <= 0 || request.amount().scale() > 2
                || request.paidAt() == null || request.method() == null || request.method().isBlank()) {
            throw bad("INVALID_PAYMENT", "Thông tin thanh toán không hợp lệ.");
        }
        Supplier supplier = supplier(supplierId);
        SupplierReceipt receipt = receipts.findLockedById(request.receiptId())
            .filter(row -> row.getSupplier().id.equals(supplierId))
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SUPPLIER_RECEIPT_NOT_FOUND",
                "Không tìm thấy phiếu nhập hàng của nhà cung cấp này."));
        BigDecimal outstanding = receipt.getTotalAmount().subtract(payments.paidForReceipt(receipt.getId()));
        if (request.amount().compareTo(outstanding) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_EXCEEDS_OUTSTANDING",
                "Số tiền thanh toán vượt quá công nợ còn lại.");
        }
        String note = request.note() == null ? null : request.note().trim();
        if (note != null && note.isEmpty()) note = null;
        var payment = new SupplierPayment();
        payment.setSupplier(supplier);
        payment.setReceipt(receipt);
        payment.setAmount(request.amount());
        payment.setPaidAt(request.paidAt());
        payment.setMethod(request.method().trim().toUpperCase(java.util.Locale.ROOT));
        payment.setNote(note);
        payment.setCreatedBy(actor.id());
        payments.saveAndFlush(payment);
        audit.recordAs(actor, "SUPPLIER_PAYMENT_CREATE", "SUPPLIER_PAYMENT", payment.getId(),
            java.util.Map.of(),
            java.util.Map.of("supplierId", supplierId, "receiptId", receipt.getId(),
                "amount", payment.getAmount(), "outstanding", outstanding.subtract(payment.getAmount())));
        return paymentView(payment);
    }

    private ProductRow productRow(Product product) {
        return new ProductRow(product.getId(), product.getName(), product.getStock(), product.getPrice(),
            product.isActive());
    }

    private ReceiptRow receiptRow(SupplierReceipt receipt) {
        BigDecimal paid = payments.paidForReceipt(receipt.getId());
        return new ReceiptRow(receipt.getId(), receipt.getInvoiceNumber(), receipt.getInvoiceDate(),
            receipt.getTotalAmount(), paid, receipt.getTotalAmount().subtract(paid));
    }

    private PaymentView paymentView(SupplierPayment payment) {
        return new PaymentView(payment.getId(), payment.getReceipt().getId(),
            payment.getReceipt().getInvoiceNumber(), payment.getAmount(), payment.getPaidAt(),
            payment.getMethod(), payment.getNote(), payment.getCreatedAt());
    }
}
