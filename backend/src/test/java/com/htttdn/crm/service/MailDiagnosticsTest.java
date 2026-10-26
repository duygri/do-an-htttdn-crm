package com.htttdn.crm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
  for(int status:new int[]{403,429}){
   var failure=mail.failure(status,new ObjectMapper().writeValueAsBytes(java.util.Map.of("message",message,"code","unauthorized")));
   assertEquals(reason,failure.reason);assertEquals(status,failure.status);
  }
 }
 @ParameterizedTest @ValueSource(strings={""," \t\r\n","null","[]","[{}]","\"slow down\"","42","true","false"})
 void classifiesRateLimitedEmptyAndNonObjectResponses(String body) {
  var failure=mail.failure(429,body.getBytes(StandardCharsets.UTF_8));
  assertEquals("RATE_LIMITED",failure.reason);assertEquals(429,failure.status);
  assertEquals("NON_JSON_RESPONSE",mail.failure(403,body.getBytes(StandardCharsets.UTF_8)).reason);
 }
 @ParameterizedTest @CsvSource({
  "not_enough_credits,INSUFFICIENT_CREDITS",
  "permission_denied,PERMISSION_DENIED",
  "access_denied,PERMISSION_DENIED",
  "unauthorized,PROVIDER_UNAUTHORIZED",
  "unauthorised,PROVIDER_UNAUTHORIZED",
  "invalid_parameter,REQUEST_VALIDATION_FAILED",
  "validation_error,REQUEST_VALIDATION_FAILED",
  "missing_parameter,REQUEST_VALIDATION_FAILED"
 }) void preservesRecognizedJsonErrorsWhenRateLimited(String code,String reason) throws Exception {
  assertEquals(reason,mail.failure(429,new ObjectMapper().writeValueAsBytes(java.util.Map.of("code",code))).reason);
 }
 @Test void classifiesUnrecognizedJsonObjectAsRateLimited() {
  assertEquals("RATE_LIMITED",mail.failure(429,"{}".getBytes(StandardCharsets.UTF_8)).reason);
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
