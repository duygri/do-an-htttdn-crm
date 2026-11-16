package com.htttdn.crm.service;

import com.htttdn.crm.entity.InternalNotice;
import com.htttdn.crm.entity.Product;
import com.htttdn.crm.repository.InternalNoticeRepository;
import com.htttdn.crm.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

/** Sends operational notices to every active manager; dedupe keeps one notice per day per event. */
@Service
public class InternalNoticeService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final InternalNoticeRepository notices;
    private final UserRepository users;
    private final SettingsService settings;

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
}
