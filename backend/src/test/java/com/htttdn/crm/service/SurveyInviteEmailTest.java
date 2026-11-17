package com.htttdn.crm.service;

import com.htttdn.crm.entity.CustomerNotification;
import com.htttdn.crm.entity.User;
import com.htttdn.crm.repository.CustomerNotificationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SurveyInviteEmailTest {
    private final CustomerNotificationRepository repository=mock(CustomerNotificationRepository.class);
    private final ApplicationEventPublisher publisher=mock(ApplicationEventPublisher.class);
    private final TransactionalMail mail=mock(TransactionalMail.class);
    private NotificationService service(){
        var service=new NotificationService(repository);ReflectionTestUtils.setField(service,"events",publisher);ReflectionTestUtils.setField(service,"mail",mail);
        when(mail.permits(anyString())).thenReturn(true);when(mail.link(anyString())).thenAnswer(a->"https://example.com"+a.getArgument(0));return service;
    }
    private User customer(){var u=new User();ReflectionTestUtils.setField(u,"id",1L);u.setRole("CUSTOMER");u.setEmail("person@example.com");return u;}

    @Test void keepsInAppNotificationAndEmailsOneInviteLink(){
        service().createSurveyInvite(customer(),"Khảo sát mới: Mức độ hài lòng","Cửa hàng mời bạn tham gia khảo sát. Đăng nhập để trả lời.",7L);
        var stored=ArgumentCaptor.forClass(CustomerNotification.class);verify(repository).save(stored.capture());
        assertEquals("SURVEY_PUBLISHED",stored.getValue().getType());
        assertEquals("Khảo sát mới: Mức độ hài lòng",stored.getValue().getTitle());
        assertEquals("Cửa hàng mời bạn tham gia khảo sát. Đăng nhập để trả lời.",stored.getValue().getContent());
        assertNull(stored.getValue().getOrderId());
        var notice=ArgumentCaptor.forClass(TransactionalMail.Notice.class);verify(publisher,times(1)).publishEvent(notice.capture());
        var value=notice.getValue();
        assertEquals("person@example.com",value.recipient());
        assertEquals("ANH LỚN SHOP — Khảo sát mới: Mức độ hài lòng",value.subject());
        assertTrue(value.text().contains("https://example.com/khao-sat/7"));
        assertTrue(value.text().contains("Cửa hàng mời bạn tham gia khảo sát."));
        assertEquals("survey-invite-7-1",value.key());
    }

    @Test void blockedMailKeepsTheInAppInvite(){
        var service=service();
        when(mail.permits(anyString())).thenReturn(false);
        service.createSurveyInvite(customer(),"Khảo sát mới","Nội dung",7L);
        verify(repository).save(any(CustomerNotification.class));
        verifyNoInteractions(publisher);
    }
}
