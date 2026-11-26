package com.htttdn.crm.service;

import com.htttdn.crm.entity.CustomerNotification;
import com.htttdn.crm.entity.User;
import com.htttdn.crm.repository.CustomerNotificationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SurveyInviteEmailTest {
    private final CustomerNotificationRepository repository=mock(CustomerNotificationRepository.class);
    private final EmailOutboxService outbox=mock(EmailOutboxService.class);
    private final TransactionalMail mail=mock(TransactionalMail.class);
    private NotificationService service(){
        var service=new NotificationService(repository);ReflectionTestUtils.setField(service,"outbox",outbox);ReflectionTestUtils.setField(service,"mail",mail);
        when(mail.sandboxExcludes(anyString())).thenReturn(false);when(mail.link(anyString())).thenAnswer(a->"https://example.com"+a.getArgument(0));return service;
    }
    private User customer(){var u=new User();ReflectionTestUtils.setField(u,"id",1L);u.setRole("CUSTOMER");u.setEmail("person@example.com");return u;}

    @Test void keepsInAppNotificationAndEmailsOneInviteLink(){
        service().createSurveyInvite(customer(),"Khảo sát mới: Mức độ hài lòng","Cửa hàng mời bạn tham gia khảo sát. Đăng nhập để trả lời.",7L);
        var stored=ArgumentCaptor.forClass(CustomerNotification.class);verify(repository).save(stored.capture());
        assertEquals("SURVEY_PUBLISHED",stored.getValue().getType());
        assertEquals("Khảo sát mới: Mức độ hài lòng",stored.getValue().getTitle());
        assertEquals("Cửa hàng mời bạn tham gia khảo sát. Đăng nhập để trả lời.",stored.getValue().getContent());
        assertNull(stored.getValue().getOrderId());
        var text=ArgumentCaptor.forClass(String.class);
        verify(outbox).enqueue(eq("person@example.com"),eq("ANH LỚN SHOP — Khảo sát mới: Mức độ hài lòng"),text.capture(),eq("survey-invite-7-1"));
        assertTrue(text.getValue().contains("https://example.com/khao-sat/7"));
        assertTrue(text.getValue().contains("Cửa hàng mời bạn tham gia khảo sát."));
    }

    @Test void blockedMailKeepsTheInAppInvite(){
        var service=service();
        when(mail.sandboxExcludes(anyString())).thenReturn(true);
        service.createSurveyInvite(customer(),"Khảo sát mới","Nội dung",7L);
        verify(repository).save(any(CustomerNotification.class));
        verifyNoInteractions(outbox);
    }
}
