package com.htttdn.crm.controller;

import com.htttdn.crm.entity.Feedback;
import com.htttdn.crm.entity.Product;
import com.htttdn.crm.entity.User;
import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.repository.FeedbackRepository;
import com.htttdn.crm.repository.OrderRepository;
import com.htttdn.crm.repository.ProductRepository;
import com.htttdn.crm.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@RestController
public class FeedbackController {
    private static final java.util.Set<String> ELIGIBLE_ORDER_STATUSES =
            java.util.Set.of("CONFIRMED", "SHIPPED", "DELIVERED", "COMPLETED");

    private final AuthService auth;
    private final ProductRepository products;
    private final FeedbackRepository feedback;
    private final OrderRepository orders;

    public FeedbackController(AuthService auth, ProductRepository products, FeedbackRepository feedback,
                              OrderRepository orders) {
        this.auth = auth;
        this.products = products;
        this.feedback = feedback;
        this.orders = orders;
    }

    @GetMapping("/api/products/{productId}/feedback")
    public List<FeedbackView> listForProduct(@PathVariable Long productId) {
        return list(productId);
    }

    /** Compatibility read endpoint for the /api/feedback contract. */
    @GetMapping("/api/feedback")
    public List<FeedbackView> listAll(@RequestParam(required = false) Long productId) {
        return list(productId);
    }

    /** Compatibility read endpoint for clients that put the product id in the path. */
    @GetMapping("/api/feedback/{productId}")
    public List<FeedbackView> listByCompatibilityPath(@PathVariable Long productId) {
        return list(productId);
    }

    @PostMapping("/api/products/{productId}/feedback")
    public ResponseEntity<FeedbackView> createForProduct(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long productId,
            @Valid @RequestBody FeedbackRequest request) {
        return saveFeedback(auth.requireCustomer(authorization), productId, request.rating(), request.comment());
    }

    /** Compatibility write endpoint required by issue IV-04. */
    @PostMapping("/api/feedback")
    public ResponseEntity<FeedbackView> create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody FeedbackSubmission request) {
        return saveFeedback(auth.requireCustomer(authorization), request.productId(), request.rating(), request.comment());
    }

    private List<FeedbackView> list(Long productId) {
        return feedback.findAll().stream()
                .filter(item -> item.getProduct() != null
                        && (productId == null || item.getProduct().getId().equals(productId)))
                .sorted(Comparator.comparing(Feedback::getCreatedAt).reversed())
                .map(this::view)
                .toList();
    }

    private ResponseEntity<FeedbackView> saveFeedback(User customer, Long productId, int rating, String comment) {
        Product product = products.findByIdAndActiveTrue(productId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm."));
        if (!orders.existsEligiblePurchase(customer.getId(), productId, ELIGIBLE_ORDER_STATUSES)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PURCHASE_REQUIRED", "Bạn cần mua và nhận sản phẩm trước khi đánh giá.");
        }
        if (feedback.findByCustomerIdAndProductId(customer.getId(), productId).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_FEEDBACK", "Bạn đã gửi đánh giá cho sản phẩm này.");
        }

        Feedback item = new Feedback();
        item.setCustomer(customer);
        item.setProduct(product);
        item.setRating(rating);
        String content = comment == null ? "Đánh giá không kèm bình luận." : comment.trim();
        item.setComment(content.isBlank() ? "Đánh giá không kèm bình luận." : content);
        item.setStatus("NEW");
        item.setCreatedAt(Instant.now());
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(view(feedback.saveAndFlush(item)));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_FEEDBACK", "Bạn đã gửi đánh giá cho sản phẩm này.");
        }
    }

    private FeedbackView view(Feedback item) {
        return new FeedbackView(item.getId(), item.getCustomer().getFullName(), item.getRating(),
                item.getComment(), item.getCreatedAt());
    }

    public record FeedbackRequest(@Min(1) @Max(5) int rating, @Size(max = 4000) String comment) {
    }

    public record FeedbackSubmission(@NotNull Long productId, @Min(1) @Max(5) int rating,
                                     @Size(max = 4000) String comment) {
    }

    public record FeedbackView(Long id, String customerName, int rating, String comment, Instant createdAt) {
    }
}
