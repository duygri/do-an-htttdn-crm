package com.htttdn.crm.service;

import com.htttdn.crm.entity.*; import com.htttdn.crm.repository.CustomerNotificationRepository; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.time.Instant; import java.util.Set; import java.util.UUID;
@Service public class NotificationService {
    private static final Set<String> ORDER_EMAIL_TYPES=Set.of("PAYMENT_FAILED","PAYMENT_EXPIRED","REFUND_REQUEST","REFUND_REJECTED");
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.context.ApplicationEventPublisher events;
    @org.springframework.beans.factory.annotation.Autowired private TransactionalMail mail;
    private final CustomerNotificationRepository notifications; public NotificationService(CustomerNotificationRepository notifications){this.notifications=notifications;}
    private static String emailSubject(String title){return "ANH LỚN SHOP — "+title.replaceAll("[\\r\\n]+"," ");}
    private void publish(User recipient,String title,String text,String key){if(mail.permits(recipient.getEmail()))events.publishEvent(new TransactionalMail.Notice(recipient.getEmail(),emailSubject(title),text,key));}
    private CustomerNotification save(User recipient,String title,String content,String type){var n=new CustomerNotification();n.setCustomer(recipient);n.setTitle(title);n.setContent(content);n.setType(type);n.setCreatedAt(Instant.now());return n;}
    @Transactional public void createSupport(User recipient,String title,String content,String type,Long ticketId,String emailDetail,boolean sendEmail){
        var n=save(recipient,title,content,type);n.setSupportTicketId(ticketId);notifications.save(n);
        if(!sendEmail)return;
        String path="MANAGER".equals(recipient.getRole())?"/manager/support-tickets?id="+ticketId:"/ho-tro/"+ticketId;
        publish(recipient,title,content+(emailDetail==null||emailDetail.isBlank()?"":"\n\n"+emailDetail)+"\n\nXem phiếu hỗ trợ: "+mail.link(path),"support-notification-"+n.getId());
    }
    @Transactional public void createVoucher(User recipient,String title,String content){
        var n=save(recipient,title,content,"SURVEY_REWARD");notifications.save(n);
        publish(recipient,title,content+"\n\nXem voucher: "+mail.link("/tai-khoan/voucher"),"notification-"+n.getId());
    }
    @Transactional public void accountNotice(User recipient,String title,String content){publish(recipient,title,content,"account-"+UUID.randomUUID());}
    @Transactional public void create(User customer,String title,String content,String type,Long orderId){
        var n=save(customer,title,content,type);n.setOrderId(orderId);notifications.save(n);
        boolean orderMail=orderId!=null&&type!=null&&(type.startsWith("ORDER_")||type.startsWith("RETURN_")||type.equals("REFUND_COMPLETED")||ORDER_EMAIL_TYPES.contains(type));
        if(orderMail)publish(customer,title,content+"\n\nXem đơn hàng: "+mail.link("/don-hang/"+orderId),"notification-"+n.getId());
        else if("ACCOUNT_LOCKED".equals(type))publish(customer,title,"Tài khoản của bạn đã bị khóa. Vui lòng liên hệ cửa hàng để được hỗ trợ.","notification-"+n.getId());
    }
}
