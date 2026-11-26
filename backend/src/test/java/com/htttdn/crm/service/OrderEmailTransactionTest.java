package com.htttdn.crm.service;

import com.htttdn.crm.entity.*;
import com.htttdn.crm.repository.CustomerNotificationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderEmailTransactionTest {
 @Test void enqueuesOnlyEligibleNotifications(){
  var repository=mock(CustomerNotificationRepository.class);var outbox=mock(EmailOutboxService.class);var mail=mock(TransactionalMail.class);
  var service=new NotificationService(repository);ReflectionTestUtils.setField(service,"outbox",outbox);ReflectionTestUtils.setField(service,"mail",mail);
  var user=new User();user.setEmail("owner@example.com");when(mail.sandboxExcludes(user.getEmail())).thenReturn(false);when(mail.link(anyString())).thenReturn("https://example.com/link");
  service.create(user,"Đang giao","Đơn đang giao","ORDER_STATUS",7L);
  verify(outbox).enqueue(anyString(),anyString(),anyString(),anyString());
  clearInvocations(outbox);
  service.create(user,"Thanh toán chưa hoàn tất","Đơn chưa thanh toán","PAYMENT_FAILED",7L);
  service.create(user,"Hết hạn thanh toán","Liên kết đã hết hạn","PAYMENT_EXPIRED",7L);
  service.create(user,"Đã ghi nhận yêu cầu hoàn tiền","Đang chờ xử lý","REFUND_REQUEST",7L);
  service.create(user,"Yêu cầu hoàn tiền chưa được chấp thuận","Không được chấp thuận","REFUND_REJECTED",7L);
  verify(outbox,times(4)).enqueue(anyString(),anyString(),anyString(),anyString());
  clearInvocations(outbox);
  service.create(user,"Tài khoản đã bị khóa","Lý do nội bộ do admin nhập","ACCOUNT_LOCKED",null);
  var account=ArgumentCaptor.forClass(String.class);verify(outbox).enqueue(anyString(),anyString(),account.capture(),anyString());
  assertFalse(account.getValue().contains("Lý do nội bộ do admin nhập"));
  assertTrue(account.getValue().contains("liên hệ cửa hàng"));
  clearInvocations(outbox);
  service.create(user,"Khảo sát","Khảo sát mới","SURVEY_PUBLISHED",null);verifyNoInteractions(outbox);
  when(mail.sandboxExcludes(user.getEmail())).thenReturn(true);service.create(user,"Đang giao","Đơn đang giao","ORDER_STATUS",7L);verifyNoInteractions(outbox);
 }
 @Test void enqueueRequiresBusinessTransaction() throws Exception {
  assertEquals(org.springframework.transaction.annotation.Propagation.MANDATORY,
    EmailOutboxService.class.getMethod("enqueue",String.class,String.class,String.class,String.class)
      .getAnnotation(org.springframework.transaction.annotation.Transactional.class).propagation());
 }
}
