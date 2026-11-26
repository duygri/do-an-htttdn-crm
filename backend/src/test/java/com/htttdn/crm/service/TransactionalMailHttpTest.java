package com.htttdn.crm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.*;
import java.net.http.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransactionalMailHttpTest {
    HttpServer server;
    final ObjectMapper mapper=new ObjectMapper();
    @AfterEach void stop() { if(server!=null)server.stop(0); }
    TransactionalMail configured(HttpClient client,URI endpoint) {
        var mail=new TransactionalMail(mapper,client,endpoint);
        ReflectionTestUtils.setField(mail,"enabled",true);
        ReflectionTestUtils.setField(mail,"apiKey","fake-not-a-real-key");
        ReflectionTestUtils.setField(mail,"senderEmail","sender@example.test");
        ReflectionTestUtils.setField(mail,"origin","https://example.test");
        ReflectionTestUtils.setField(mail,"testRecipient","owner@example.test, second@example.test");
        return mail;
    }
    @Test void fakeHttpVerifiesBrevoPayloadAndClassifiesStatusesWithoutLeakingResponse() throws Exception {
        var status=new AtomicInteger(201);var calls=new AtomicInteger();var body=new AtomicReference<String>();
        server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);
        server.createContext("/email",exchange->{
            calls.incrementAndGet();body.set(new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            assertEquals("fake-not-a-real-key",exchange.getRequestHeaders().getFirst("api-key"));
            byte[] response="secret-provider-response".getBytes();
            exchange.sendResponseHeaders(status.get(),response.length);exchange.getResponseBody().write(response);exchange.close();
        });server.start();
        var mail=configured(HttpClient.newHttpClient(),URI.create("http://localhost:"+server.getAddress().getPort()+"/email"));
        mail.send("owner@example.test","Subject","Private message","event-1");
        var payload=mapper.readTree(body.get());
        assertEquals("owner@example.test",payload.path("to").get(0).path("email").asText());
        assertEquals("Private message",payload.path("textContent").asText());
        var key=payload.path("headers").path("IdempotencyKey").asText();
        assertDoesNotThrow(()->java.util.UUID.fromString(key));
        mail.send("owner@example.test","Subject","Private message","event-1");
        assertEquals(key,mapper.readTree(body.get()).path("headers").path("IdempotencyKey").asText());
        assertThrows(TransactionalMail.MailFailure.class,()->mail.send("outsider@example.test","s","b","k"));
        assertEquals(2,calls.get());
        for(int code:new int[]{401,403,400,429,500,503}) {
            status.set(code);
            var failure=assertThrows(TransactionalMail.MailFailure.class,()->mail.send("owner@example.test","s","b","k"));
            assertEquals(code==429||code>=500?TransactionalMail.Outcome.RETRYABLE:TransactionalMail.Outcome.FAILED,failure.outcome());
            assertFalse(failure.getMessage().contains("secret"));assertNull(failure.getCause());
        }
        status.set(200);assertDoesNotThrow(()->mail.send("second@example.test","s","b","k"));
    }
    @Test void fakeTransportDistinguishesSafeConnectionFailureFromAmbiguousReadTimeout() throws Exception {
        var client=mock(HttpClient.class);var mail=configured(client,URI.create("http://localhost/email"));
        for(Exception error:new Exception[]{new ConnectException("secret"),new HttpConnectTimeoutException("secret"),new HttpTimeoutException("secret"),new java.io.IOException("secret")}) {
            reset(client);when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenThrow(error);
            var failure=assertThrows(TransactionalMail.MailFailure.class,()->mail.send("owner@example.test","s","b","k"));
            assertEquals(error instanceof ConnectException||error instanceof HttpConnectTimeoutException?TransactionalMail.Outcome.RETRYABLE:TransactionalMail.Outcome.UNKNOWN,failure.outcome());
            assertFalse(failure.getMessage().contains("secret"));
        }
    }
    @Test void invalidProviderAndOriginFailClosedWithoutSubstitution() {
        var mail=configured(HttpClient.newHttpClient(),URI.create("http://localhost/email"));
        assertTrue(mail.permits("OWNER@example.test"));assertFalse(mail.permits("outsider@example.test"));
        ReflectionTestUtils.setField(mail,"provider","other");assertFalse(mail.available());
        ReflectionTestUtils.setField(mail,"provider","brevo");ReflectionTestUtils.setField(mail,"origin","http://example.test");assertFalse(mail.available());
    }
}
