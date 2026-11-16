package com.htttdn.crm.service;

import com.htttdn.crm.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Flags orders stuck in an open status for more than a day; at most one notice per manager per day. */
@Component
public class OperationsNoticeScheduler {
    private static final Logger log = LoggerFactory.getLogger(OperationsNoticeScheduler.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final OrderRepository orders;
    private final InternalNoticeService notices;
    @Value("${app.notices.enabled:true}") private boolean enabled = true;

    public OperationsNoticeScheduler(OrderRepository orders, InternalNoticeService notices) {
        this.orders = orders;
        this.notices = notices;
    }

    @Scheduled(fixedDelayString = "${app.notices.check-delay-ms:3600000}", initialDelayString = "${app.notices.initial-delay-ms:600000}")
    public void scheduledCheck() {
        if (!enabled) return;
        try {
            checkStaleOrders();
        } catch (RuntimeException failure) {
            log.warn("Stale order notice check failed", failure);
        }
    }

    public long checkStaleOrders() {
        long stale = orders.countStale(Instant.now().minus(Duration.ofHours(24)));
        if (stale > 0) {
            notices.notifyManagers("STALE_ORDERS", "Đơn chờ xử lý quá 24 giờ",
                "Có " + stale + " đơn đang chờ xử lý quá 24 giờ.", "/manager/orders",
                "STALE_ORDERS:" + LocalDate.now(ZONE));
        }
        return stale;
    }
}
