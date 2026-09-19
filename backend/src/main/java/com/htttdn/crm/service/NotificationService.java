package com.htttdn.crm.service;

import com.htttdn.crm.entity.*; import com.htttdn.crm.repository.CustomerNotificationRepository; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.time.Instant;
@Service public class NotificationService {
    private final CustomerNotificationRepository notifications; public NotificationService(CustomerNotificationRepository notifications){this.notifications=notifications;}
    @Transactional public void create(User customer,String title,String content,String type,Long orderId){CustomerNotification n=new CustomerNotification();n.setCustomer(customer);n.setTitle(title);n.setContent(content);n.setType(type);n.setOrderId(orderId);n.setCreatedAt(Instant.now());notifications.save(n);}
}
