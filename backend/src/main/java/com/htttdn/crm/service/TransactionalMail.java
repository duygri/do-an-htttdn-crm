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
            var response=client.send(request,HttpResponse.BodyHandlers.discarding());
            if(response.statusCode()<200 || response.statusCode()>=300) throw new DeliveryFailure(response.statusCode());
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
        DeliveryFailure(int status){super("Email provider rejected request");this.status=status;}
    }
    public record OrderEmail(String recipient,String subject,String text,String key) {}
    @Async("transactionalMailExecutor")
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void afterOrderCommit(OrderEmail message) {
        if(!permits(message.recipient())) return;
        try { send(message.recipient(),message.subject(),message.text(),message.key()); }
        catch(RuntimeException e) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Order email delivery failed (provider={}, status={}); in-app notification retained",provider,e instanceof DeliveryFailure f?f.status:0); }
    }
    @Configuration @EnableAsync
    static class MailExecutorConfig {
        @Bean(name="transactionalMailExecutor")
        ThreadPoolTaskExecutor executor() {
            var executor=new ThreadPoolTaskExecutor(); executor.setCorePoolSize(1); executor.setMaxPoolSize(1);
            executor.setQueueCapacity(100); executor.setThreadNamePrefix("transactional-mail-");
            executor.setRejectedExecutionHandler((task,pool)->org.slf4j.LoggerFactory.getLogger(TransactionalMail.class).warn("Order email queue full; in-app notification retained"));
            return executor;
        }
    }
}
