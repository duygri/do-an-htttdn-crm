package com.htttdn.crm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BrevoMailTest {
 private final HttpClient client=mock(HttpClient.class);
 private TransactionalMail configured(){
  var service=new TransactionalMail(new ObjectMapper(),client);
  ReflectionTestUtils.setField(service,"provider","brevo");ReflectionTestUtils.setField(service,"brevoEnabled",true);
  ReflectionTestUtils.setField(service,"brevoApiKey","dummy-test-key");ReflectionTestUtils.setField(service,"brevoSender","sender@example.com");
  ReflectionTestUtils.setField(service,"brevoTestRecipient","owner@example.com");ReflectionTestUtils.setField(service,"origin","https://example.com");
  return service;
 }
 @Test void sandboxNeverRedirectsAndLiveModeAllowsDifferentRecipients(){
  var service=configured();assertTrue(service.available());assertTrue(service.permits("OWNER@example.com"));assertFalse(service.permits("other@example.com"));
  assertThrows(IllegalStateException.class,()->service.send("other@example.com","test","text","key"));verifyNoInteractions(client);
  ReflectionTestUtils.setField(service,"brevoTestMode",false);assertTrue(service.permits("other@example.com"));
 }
 @Test void unknownProviderAndMissingConfigurationFailClosed(){
  var service=configured();ReflectionTestUtils.setField(service,"provider","unknown");assertFalse(service.available());
  ReflectionTestUtils.setField(service,"provider","brevo");ReflectionTestUtils.setField(service,"brevoApiKey","");assertFalse(service.available());
  ReflectionTestUtils.setField(service,"brevoApiKey","dummy");ReflectionTestUtils.setField(service,"brevoSender","Bad <sender@example.com>");assertFalse(service.available());
 }
 @Test void sendsCorrectHttpsPayloadAndEscapesHtmlWithoutResendCredentials() throws Exception {
  var service=configured();var response=mock(HttpResponse.class);when(response.statusCode()).thenReturn(201);
  doReturn(response).when(client).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
  service.send("owner@example.com","Đặt lại mật khẩu","<script>bad</script>\nhttps://example.com/dat-lai-mat-khau#code=abc&email=owner","notification-1");
  var request=ArgumentCaptor.forClass(HttpRequest.class);verify(client).send(request.capture(),any(HttpResponse.BodyHandler.class));
  assertEquals("https://api.brevo.com/v3/smtp/email",request.getValue().uri().toString());
  assertEquals("dummy-test-key",request.getValue().headers().firstValue("api-key").orElseThrow());
  assertTrue(request.getValue().headers().firstValue("Authorization").isEmpty());
  var json=new ObjectMapper().readTree(body(request.getValue()));assertEquals("sender@example.com",json.path("sender").path("email").asText());
  assertEquals("owner@example.com",json.path("to").get(0).path("email").asText());assertEquals("notification-1",json.path("headers").path("IdempotencyKey").asText());
  assertFalse(json.path("htmlContent").asText().contains("<script>"));assertTrue(json.path("htmlContent").asText().contains("<a href=\"https://example.com/"));
 }
 @Test void rejectsProviderErrorsWithoutLeakingKeyOrBody() throws Exception {
  var service=configured();var response=mock(HttpResponse.class);when(response.statusCode()).thenReturn(403);
  doReturn(response).when(client).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
  var failure=assertThrows(TransactionalMail.DeliveryFailure.class,()->service.send("owner@example.com","subject","secret-link","key"));
  assertEquals(403,failure.status);assertFalse(failure.getMessage().contains("dummy-test-key"));assertFalse(failure.getMessage().contains("secret-link"));
 }
 private String body(HttpRequest request) throws Exception {
  var result=new CompletableFuture<String>();var bytes=new java.io.ByteArrayOutputStream();
  request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>(){
   public void onSubscribe(Flow.Subscription subscription){subscription.request(Long.MAX_VALUE);}
   public void onNext(ByteBuffer buffer){byte[] chunk=new byte[buffer.remaining()];buffer.get(chunk);bytes.writeBytes(chunk);}
   public void onError(Throwable error){result.completeExceptionally(error);}
   public void onComplete(){result.complete(bytes.toString(StandardCharsets.UTF_8));}
  });return result.get(2,TimeUnit.SECONDS);
 }
}
