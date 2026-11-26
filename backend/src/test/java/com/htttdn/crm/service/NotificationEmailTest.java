package com.htttdn.crm.service;

import com.htttdn.crm.entity.CustomerNotification;
import com.htttdn.crm.entity.User;
import com.htttdn.crm.repository.CustomerNotificationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationEmailTest {
    private final CustomerNotificationRepository repository=mock(CustomerNotificationRepository.class);
    private final EmailOutboxService outbox=mock(EmailOutboxService.class);
    private final TransactionalMail mail=mock(TransactionalMail.class);
    private NotificationService service(){
        var service=new NotificationService(repository);ReflectionTestUtils.setField(service,"outbox",outbox);ReflectionTestUtils.setField(service,"mail",mail);
        when(mail.sandboxExcludes(anyString())).thenReturn(false);when(mail.link(anyString())).thenAnswer(a->"https://example.com"+a.getArgument(0));return service;
    }
    private User user(String role){var u=new User();u.setRole(role);u.setEmail("person@example.com");return u;}
    @Test void routesSupportLinksByRecipientRole(){
        var service=service();
        service.createSupport(user("CUSTOMER"),"Shop đã trả lời phiếu hỗ trợ","Phiếu hỗ trợ #3 - Lỗi đơn (Đang xử lý)","SUPPORT_REPLY",3L,"Nội dung trả lời công khai",true);
        service.createSupport(user("MANAGER"),"Phiếu hỗ trợ cần xử lý","Phiếu hỗ trợ #3 - Lỗi đơn (Chờ khách)","SUPPORT_ASSIGNED",3L,"Khách vừa trả lời",true);
        var captor=ArgumentCaptor.forClass(String.class); var subject=ArgumentCaptor.forClass(String.class);
        verify(outbox,times(2)).enqueue(anyString(),subject.capture(),captor.capture(),anyString());
        var notices=captor.getAllValues();
        assertTrue(notices.get(0).contains("https://example.com/ho-tro/3"));
        assertTrue(notices.get(0).contains("Nội dung trả lời công khai"));
        assertTrue(notices.get(1).contains("https://example.com/manager/support-tickets?id=3"));
        assertTrue(notices.get(1).contains("Khách vừa trả lời"));
        assertEquals("ANH LỚN SHOP — Shop đã trả lời phiếu hỗ trợ",subject.getAllValues().get(0));
    }
    @Test void skipsEmailForSelfAssignmentButKeepsInAppNotification(){
        service().createSupport(user("MANAGER"),"Phiếu hỗ trợ cần xử lý","Phiếu hỗ trợ #3 - Lỗi đơn (Đang xử lý)","SUPPORT_ASSIGNED",3L,null,false);
        verifyNoInteractions(outbox);verify(repository).save(any(CustomerNotification.class));
    }
    @Test void longReplyTextStaysOutOfStoredNotification(){
        String longText="x".repeat(4000);
        service().createSupport(user("CUSTOMER"),"Shop đã trả lời phiếu hỗ trợ","Phiếu hỗ trợ #3 - Lỗi đơn (Đang xử lý)","SUPPORT_REPLY",3L,longText,true);
        var stored=ArgumentCaptor.forClass(CustomerNotification.class);verify(repository).save(stored.capture());
        assertTrue(stored.getValue().getContent().length()<500);
        var notice=ArgumentCaptor.forClass(String.class);verify(outbox).enqueue(anyString(),anyString(),notice.capture(),anyString());
        assertTrue(notice.getValue().contains(longText));
    }
    @Test void voucherEmailLinksToWalletAndDoesNotReuseOrderId(){
        service().createVoucher(user("CUSTOMER"),"Bạn nhận được voucher khảo sát","Mã KS-1 đã được thêm vào Voucher của tôi.");
        var stored=ArgumentCaptor.forClass(CustomerNotification.class);verify(repository).save(stored.capture());
        assertNull(stored.getValue().getOrderId());assertEquals("SURVEY_REWARD",stored.getValue().getType());
        var notice=ArgumentCaptor.forClass(String.class);verify(outbox).enqueue(anyString(),anyString(),notice.capture(),anyString());
        assertTrue(notice.getValue().contains("https://example.com/tai-khoan/voucher"));
    }
}
