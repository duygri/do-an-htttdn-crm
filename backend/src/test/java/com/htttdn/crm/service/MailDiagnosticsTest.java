package com.htttdn.crm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.URI;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MailDiagnosticsTest {
    @ParameterizedTest
    @CsvSource({"401,FAILED,PERMISSION_DENIED", "403,FAILED,PERMISSION_DENIED",
        "400,FAILED,HTTP_400", "429,RETRYABLE,HTTP_429", "503,RETRYABLE,HTTP_503"})
    void diagnosticsUseOnlyStatusAndNeverReadOrLogProviderBody(int status, String outcome, String reason) throws Exception {
        var client=mock(HttpClient.class);
        var response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        doReturn(response).when(client).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        var mail=new TransactionalMail(new ObjectMapper(),client,URI.create("http://localhost/email"));
        ReflectionTestUtils.setField(mail,"enabled",true);
        ReflectionTestUtils.setField(mail,"apiKey","synthetic-private-key");
        ReflectionTestUtils.setField(mail,"senderEmail","sender@example.test");
        ReflectionTestUtils.setField(mail,"origin","https://example.test");
        ReflectionTestUtils.setField(mail,"testRecipient","person@example.test");
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(TransactionalMail.class);
        var events=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        events.start();logger.addAppender(events);
        try {
            var failure=assertThrows(TransactionalMail.MailFailure.class,
                ()->mail.send("person@example.test","Private subject","https://example.test/reset#secret","private-event"));
            assertEquals(TransactionalMail.Outcome.valueOf(outcome),failure.outcome());
            assertEquals(reason,failure.getMessage());assertNull(failure.getCause());
            verify(response,never()).body();
            assertTrue(events.list.isEmpty());
        } finally { logger.detachAppender(events);events.stop(); }
    }
}
