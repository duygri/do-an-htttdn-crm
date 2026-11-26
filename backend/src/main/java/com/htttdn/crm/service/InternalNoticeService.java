package com.htttdn.crm.service;

import com.htttdn.crm.entity.InternalNotice;
import com.htttdn.crm.entity.Product;
import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.repository.InternalNoticeRepository;
import com.htttdn.crm.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Sends operational notices to every active manager; dedupe keeps one notice per day per event. */
@Service
public class InternalNoticeService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final InternalNoticeRepository notices;
    private final UserRepository users;
    private final SettingsService settings;

    public record NoticeView(Long id, String type, String title, String content, String link, Instant readAt, Instant createdAt) {}
    public record PageView(List<NoticeView> content, int number, int size, long totalElements, int totalPages) {}
    public record Inbox(PageView page, long unread) {}

    public InternalNoticeService(InternalNoticeRepository notices, UserRepository users, SettingsService settings) {
        this.notices = notices;
        this.users = users;
        this.settings = settings;
    }

    @Transactional
    public int notifyManagers(String type, String title, String content, String link, String dedupeKey) {
        int sent = 0;
        for (var manager : users.findByRoleAndLockedFalseAndDeletedAtIsNull("MANAGER")) {
            if (notices.existsByRecipientIdAndDedupeKey(manager.getId(), dedupeKey)) continue;
            var notice = new InternalNotice();
            notice.setRecipient(manager);
            notice.setNoticeType(type);
            notice.setTitle(title);
            notice.setContent(content);
            notice.setLink(link);
            notice.setDedupeKey(dedupeKey);
            notices.save(notice);
            sent++;
        }
        return sent;
    }

    @Transactional
    public void notifyLowStock(Product product, int before, int after) {
        int threshold = product.getLowStockThreshold() != null
            ? product.getLowStockThreshold()
            : settings.getInt("inventory.lowStockDefault");
        if (!(before > threshold && after <= threshold)) return;
        String dedupeKey = "LOW_STOCK:" + product.getId() + ":" + LocalDate.now(ZONE);
        notifyManagers("LOW_STOCK", "Sản phẩm sắp hết",
            "Sản phẩm " + product.getName() + " còn " + after + " (ngưỡng " + threshold + ").",
            "/manager/inventory?tab=low", dedupeKey);
    }

    @Transactional(readOnly = true)
    public Inbox inbox(Long recipientId, int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "Phân trang không hợp lệ.");
        var result = notices.findByRecipientIdOrderByCreatedAtDescIdDesc(recipientId, PageRequest.of(page, size));
        return new Inbox(new PageView(result.getContent().stream()
            .map(notice -> new NoticeView(notice.getId(), notice.getNoticeType(), notice.getTitle(),
                notice.getContent(), notice.getLink(), notice.getReadAt(), notice.getCreatedAt()))
            .toList(), page, size, result.getTotalElements(), result.getTotalPages()),
            notices.countByRecipientIdAndReadAtIsNull(recipientId));
    }

    @Transactional
    public void notifyVariantLowStock(Product product, com.htttdn.crm.entity.ProductVariant variant, int before, int after) {
        int threshold=product.getLowStockThreshold()!=null?product.getLowStockThreshold():settings.getInt("inventory.lowStockDefault");
        if(!(before>threshold && after<=threshold))return;
        notifyManagers("LOW_STOCK", "Biến thể sắp hết",
            product.getName()+" · "+variant.getColor()+" / "+variant.getSize()+" còn "+after+" (ngưỡng "+threshold+").",
            "/manager/inventory?tab=low", "LOW_STOCK_VARIANT:"+variant.getId()+":"+LocalDate.now(ZONE));
    }

    @Transactional(readOnly = true)
    public long unread(Long recipientId) {
        return notices.countByRecipientIdAndReadAtIsNull(recipientId);
    }

    @Transactional
    public void markRead(Long id, Long recipientId) {
        var notice = notices.findByIdAndRecipientId(id, recipientId).orElseThrow(() ->
            new ApiException(HttpStatus.NOT_FOUND, "NOTICE_NOT_FOUND", "Không tìm thấy thông báo."));
        if (notice.getReadAt() == null) notice.setReadAt(Instant.now());
    }

    @Transactional
    public int markAllRead(Long recipientId) {
        return notices.markAllRead(recipientId, Instant.now());
    }
}
