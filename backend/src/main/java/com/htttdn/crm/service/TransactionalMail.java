package com.htttdn.crm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/** No recipient substitution: sandbox mode only permits the configured address. */
@Service
public class TransactionalMail {
    @Value("${RESEND_API_KEY:}") private String apiKey;
    @Value("${RESEND_ENABLED:false}") private boolean enabled;
    @Value("${RESEND_FROM:onboarding@resend.dev}") private String from;
    @Value("${RESEND_TEST_MODE:true}") private boolean testMode;
    @Value("${RESEND_TEST_RECIPIENT:}") private String testRecipient;
    @Value("${MAIL_PROVIDER:resend}") private String provider = "resend";
    @Value("${BREVO_API_KEY:}") private String brevoApiKey = "";
    @Value("${BREVO_ENABLED:false}") private boolean brevoEnabled;
    @Value("${BREVO_SENDER_EMAIL:}") private String brevoSender = "";
    @Value("${BREVO_SENDER_NAME:ANH LON SHOP}") private String brevoSenderName = "ANH LON SHOP";
    @Value("${BREVO_TEST_MODE:true}") private boolean brevoTestMode = true;
    @Value("${BREVO_TEST_RECIPIENT:}") private String brevoTestRecipient = "";
    @Value("${PUBLIC_ORIGIN:}") private String origin;
    private final ObjectMapper mapper;
    private final HttpClient client;
    @org.springframework.beans.factory.annotation.Autowired
    public TransactionalMail(ObjectMapper mapper) { this(mapper,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()); }
    TransactionalMail(ObjectMapper mapper,HttpClient client) { this.mapper=mapper;this.client=client; }
    private boolean brevo(){return "brevo".equals(provider);}
    private boolean sandbox(){return brevo()?brevoTestMode:testMode;}
    private String allowedRecipient(){return brevo()?brevoTestRecipient:testRecipient;}
    public boolean available() {
        try { var uri=URI.create(origin); return (brevo() ? brevoEnabled && !brevoApiKey.isBlank() && brevoSender.matches("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$") : "resend".equals(provider) && enabled && !apiKey.isBlank()) && "https".equals(uri.getScheme())
            && uri.getHost()!=null && uri.getUserInfo()==null && uri.getQuery()==null && uri.getFragment()==null
            && (uri.getPath().isEmpty() || "/".equals(uri.getPath())) && (!sandbox() || !allowedRecipient().isBlank());
        } catch (RuntimeException e) { return false; }
    }
    public boolean permits(String recipient) { return available() && recipient!=null && (!sandbox() || recipient.equalsIgnoreCase(allowedRecipient().trim())); }
    public String link(String path) { return origin.replaceAll("/+$", "") + path; }
    public void send(String recipient, String subject, String text, String key) {
        if (!permits(recipient)) throw new IllegalStateException("Email delivery unavailable");
        try {
            var payload=brevo()?Map.of("sender",Map.of("email",brevoSender,"name",brevoSenderName),"to",List.of(Map.of("email",recipient)),"subject",subject,"textContent",text,"htmlContent",html(text),"headers",Map.of("IdempotencyKey",key))
                :Map.of("from",from,"to",List.of(recipient),"subject",subject,"text",text);
            var body=mapper.writeValueAsString(payload);
            var builder=HttpRequest.newBuilder(URI.create(brevo()?"https://api.brevo.com/v3/smtp/email":"https://api.resend.com/emails"));
            if(brevo())builder.header("api-key",brevoApiKey);else builder.header("Authorization","Bearer "+apiKey).header("Idempotency-Key",key);
            var request=builder.timeout(Duration.ofSeconds(10))
                .header("Content-Type","application/json").header("Accept","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8)).build();
            HttpResponse.BodyHandler<byte[]> handler=info->info.statusCode()>=200 && info.statusCode()<300
                ?HttpResponse.BodySubscribers.mapping(HttpResponse.BodySubscribers.discarding(),ignored->new byte[0]):new LimitedErrorBody();
            var response=client.send(request,handler);
            if(response.statusCode()<200 || response.statusCode()>=300)throw failure(response.statusCode(),response.body()==null?new byte[0]:response.body());
            org.slf4j.LoggerFactory.getLogger(getClass()).info("Email request accepted by provider (provider={}, status={}); delivery not confirmed",brevo()?"brevo":"resend",response.statusCode());
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Email delivery interrupted"); }
        catch (java.io.IOException e) { throw new IllegalStateException("Email delivery unavailable"); }
    }
    private String html(String text){
        return "<html><body>"+text.lines().map(line->{String safe=escape(line);
            return line.startsWith(link("/")) && !line.matches(".*\\s.*") ? "<p><a href=\""+safe+"\">Mở liên kết ANH LON SHOP</a></p>" : "<p>"+safe+"</p>";
        }).collect(java.util.stream.Collectors.joining())+"</body></html>";
    }
    private static String escape(String value){return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
    static final class DeliveryFailure extends IllegalStateException {
        final int status;
        final String reason;
        DeliveryFailure(int status){this(status,"UNKNOWN");}
        DeliveryFailure(int status,String reason){super("Email provider rejected request");this.status=status;this.reason=reason;}
    }
    static final class LimitedErrorBody implements HttpResponse.BodySubscriber<byte[]> {
        private final java.util.concurrent.CompletableFuture<byte[]> result=new java.util.concurrent.CompletableFuture<>();
        private final java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
        private java.util.concurrent.Flow.Subscription subscription;
        public java.util.concurrent.CompletionStage<byte[]> getBody(){return result;}
        public void onSubscribe(java.util.concurrent.Flow.Subscription value){subscription=value;value.request(Long.MAX_VALUE);}
        public void onNext(List<java.nio.ByteBuffer> chunks){
            if(result.isDone())return;
            for(var chunk:chunks){
                int count=Math.min(chunk.remaining(),16_385-bytes.size());byte[] part=new byte[count];chunk.get(part);bytes.writeBytes(part);
                if(bytes.size()==16_385){subscription.cancel();result.complete(bytes.toByteArray());return;}
            }
        }
        public void onError(Throwable error){result.completeExceptionally(error);}
        public void onComplete(){result.complete(bytes.toByteArray());}
    }
    /** Provider-controlled text is used only for classification, never returned or logged. */
    DeliveryFailure failure(int status,byte[] body){
        String reason="UNKNOWN";
        if(body.length>16_384)return new DeliveryFailure(status,"OVERSIZED_RESPONSE");
        try {
            var error=mapper.readTree(body);
            if(error==null || !error.isObject())return new DeliveryFailure(status,status==429?"RATE_LIMITED":"NON_JSON_RESPONSE");
            String code=error.path("code").asText(error.path("name").asText("")).toLowerCase(Locale.ROOT);
            String message=error.path("message").asText("").toLowerCase(Locale.ROOT);
            if(message.contains("ip") && (message.contains("unrecogn") || message.contains("unauthoriz") || message.contains("not authoriz") || message.contains("not whitelist")))reason="IP_NOT_AUTHORIZED";
            else if(message.contains("account") && (message.contains("not activated") || message.contains("not yet activated") || message.contains("disabled") || message.contains("suspended") || message.contains("under validation")))reason="ACCOUNT_NOT_ACTIVATED_OR_SUSPENDED";
            else if(message.contains("key") && (message.contains("not enabled") || message.contains("invalid") || message.contains("not found") || message.contains("unauthoriz")))reason="API_KEY_REJECTED";
            else if((message.contains("sender") || message.contains("from")) && (message.contains("verif") || message.contains("valid") || message.contains("authoriz")))reason="SENDER_NOT_VERIFIED";
            else if(code.equals("not_enough_credits") || message.contains("not enough credits"))reason="INSUFFICIENT_CREDITS";
            else if(code.equals("permission_denied") || code.equals("access_denied"))reason="PERMISSION_DENIED";
            else if(code.equals("unauthorized") || code.equals("unauthorised"))reason="PROVIDER_UNAUTHORIZED";
            else if(code.equals("invalid_parameter") || code.equals("validation_error") || code.equals("missing_parameter"))reason="REQUEST_VALIDATION_FAILED";
            else if(status==429)reason="RATE_LIMITED";
        }catch(java.io.IOException | RuntimeException ignored){
            String text=new String(body,StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            if(text.contains("cloudflare") || text.contains("access denied"))reason="EDGE_ACCESS_DENIED";
            else reason="NON_JSON_RESPONSE";
        }
        return new DeliveryFailure(status,reason);
    }
    public record OrderEmail(String recipient,String subject,String text,String key) {}
    @Async("transactionalMailExecutor")
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void afterOrderCommit(OrderEmail message) {
        if(!permits(message.recipient())) return;
        try { send(message.recipient(),message.subject(),message.text(),message.key()); }
        catch(RuntimeException e) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Order email delivery failed (provider={}, status={}, reason={}); in-app notification retained",provider,e instanceof DeliveryFailure f?f.status:0,e instanceof DeliveryFailure f?f.reason:"TRANSPORT_OR_INTERNAL"); }
    }
    @Configuration @EnableAsync
    static class MailExecutorConfig {
        @Bean(name="transactionalMailExecutor")
        ThreadPoolTaskExecutor executor() {
            var executor=new ThreadPoolTaskExecutor(); executor.setCorePoolSize(1); executor.setMaxPoolSize(1);
            executor.setQueueCapacity(100); executor.setThreadNamePrefix("transactional-mail-");
            executor.setRejectedExecutionHandler((task,pool)->org.slf4j.LoggerFactory.getLogger(TransactionalMail.class).warn("Email queue full; delivery skipped"));
            return executor;
        }
    }
}
