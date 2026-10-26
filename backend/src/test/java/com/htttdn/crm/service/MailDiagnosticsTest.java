package com.htttdn.crm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MailDiagnosticsTest {
 private final TransactionalMail mail=new TransactionalMail(new ObjectMapper());
 @ParameterizedTest @CsvSource({
  "We detected an unrecognised IP address,IP_NOT_AUTHORIZED",
  "Your SMTP account is not yet activated,ACCOUNT_NOT_ACTIVATED_OR_SUSPENDED",
  "API Key is not enabled,API_KEY_REJECTED",
  "Sender is not verified,SENDER_NOT_VERIFIED"
 }) void classifiesOnlyKnownReasons(String message,String reason) throws Exception {
  var failure=mail.failure(403,new ObjectMapper().writeValueAsBytes(java.util.Map.of("message",message,"code","unauthorized")));
  assertEquals(reason,failure.reason);assertEquals(403,failure.status);
 }
 @Test void neverIncludesProviderTextOrSecretsInDiagnostics() throws Exception {
  String secret="synthetic-secret-key-and-reset-link";
  var failure=mail.failure(403,new ObjectMapper().writeValueAsBytes(java.util.Map.of("message",secret,"code",secret)));
  assertEquals("UNKNOWN",failure.reason);assertFalse(failure.getMessage().contains(secret));
  assertEquals("EDGE_ACCESS_DENIED",mail.failure(403,"<html>Cloudflare Access denied</html>".getBytes(StandardCharsets.UTF_8)).reason);
  assertEquals("OVERSIZED_RESPONSE",mail.failure(403,new byte[16_385]).reason);
  assertEquals("NON_JSON_RESPONSE",mail.failure(403,new byte[0]).reason);
 }
 @Test void capsResponseAndCancelsSubscription() {
  var body=new TransactionalMail.LimitedErrorBody();var subscription=mock(java.util.concurrent.Flow.Subscription.class);body.onSubscribe(subscription);
  body.onNext(java.util.List.of(java.nio.ByteBuffer.wrap(new byte[40_000])));
  assertEquals(16_385,body.getBody().toCompletableFuture().join().length);verify(subscription).cancel();
 }
}
