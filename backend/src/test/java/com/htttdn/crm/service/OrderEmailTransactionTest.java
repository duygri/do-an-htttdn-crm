package com.htttdn.crm.service;

import com.htttdn.crm.entity.*;
import com.htttdn.crm.repository.CustomerNotificationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderEmailTransactionTest {
 @Test void publishesImmutableMessageOnlyForEligibleNotifications(){
  var repository=mock(CustomerNotificationRepository.class);var publisher=mock(ApplicationEventPublisher.class);var mail=mock(TransactionalMail.class);
  var service=new NotificationService(repository);ReflectionTestUtils.setField(service,"events",publisher);ReflectionTestUtils.setField(service,"mail",mail);
  var user=new User();user.setEmail("owner@example.com");when(mail.permits(user.getEmail())).thenReturn(true);when(mail.link(anyString())).thenReturn("https://example.com/link");
  service.create(user,"Đang giao","Đơn đang giao","ORDER_STATUS",7L);
  verify(publisher).publishEvent(any(TransactionalMail.Notice.class));
  clearInvocations(publisher);
  service.create(user,"Thanh toán chưa hoàn tất","Đơn chưa thanh toán","PAYMENT_FAILED",7L);
  service.create(user,"Hết hạn thanh toán","Liên kết đã hết hạn","PAYMENT_EXPIRED",7L);
  service.create(user,"Đã ghi nhận yêu cầu hoàn tiền","Đang chờ xử lý","REFUND_REQUEST",7L);
  service.create(user,"Yêu cầu hoàn tiền chưa được chấp thuận","Không được chấp thuận","REFUND_REJECTED",7L);
  verify(publisher,times(4)).publishEvent(any(TransactionalMail.Notice.class));
  clearInvocations(publisher);
  service.create(user,"Tài khoản đã bị khóa","Lý do nội bộ do admin nhập","ACCOUNT_LOCKED",null);
  var account=ArgumentCaptor.forClass(TransactionalMail.Notice.class);verify(publisher).publishEvent(account.capture());
  assertFalse(account.getValue().text().contains("Lý do nội bộ do admin nhập"));
  assertTrue(account.getValue().text().contains("liên hệ cửa hàng"));
  clearInvocations(publisher);
  service.create(user,"Khảo sát","Khảo sát mới","SURVEY_PUBLISHED",null);verifyNoInteractions(publisher);
  when(mail.permits(user.getEmail())).thenReturn(false);service.create(user,"Đang giao","Đơn đang giao","ORDER_STATUS",7L);verifyNoInteractions(publisher);
 }
 @Test void listenerRequiresCommitAndSwallowsProviderFailure(){
  var mail=spy(new TransactionalMail(new com.fasterxml.jackson.databind.ObjectMapper()));
  doReturn(true).when(mail).permits(anyString());doThrow(new IllegalStateException("provider")).when(mail).send(anyString(),anyString(),anyString(),anyString());
  assertDoesNotThrow(()->mail.afterNoticeCommit(new TransactionalMail.Notice("owner@example.com","subject","text","key")));
  try {assertEquals(org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT,TransactionalMail.class.getMethod("afterNoticeCommit",TransactionalMail.Notice.class).getAnnotation(org.springframework.transaction.event.TransactionalEventListener.class).phase());}catch(NoSuchMethodException e){throw new AssertionError(e);}
 }
}
