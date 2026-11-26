package com.htttdn.crm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.*;
import java.net.http.*;
import java.io.IOException;
import java.time.Duration;
import java.util.*;

/** Synchronous transport. Reset tokens stay in this call, never in the outbox. */
@Service
public class TransactionalMail {
    @Value("${MAIL_PROVIDER:brevo}") private String provider="brevo";
    @Value("${BREVO_API_KEY:}") private String apiKey="";
    @Value("${BREVO_ENABLED:false}") private boolean enabled;
    @Value("${BREVO_SENDER_EMAIL:}") private String senderEmail="";
    @Value("${BREVO_SENDER_NAME:ANH LON SHOP}") private String senderName="ANH LON SHOP";
    @Value("${BREVO_TEST_MODE:true}") private boolean testMode=true;
    @Value("${BREVO_TEST_RECIPIENT:}") private String testRecipient="";
    @Value("${PUBLIC_ORIGIN:}") private String origin="";
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final URI endpoint;
    @org.springframework.beans.factory.annotation.Autowired
    public TransactionalMail(ObjectMapper mapper) {
        this(mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
            URI.create("https://api.brevo.com/v3/smtp/email"));
    }
    // Package-private injection for loopback fake HTTP tests; production endpoint is fixed.
    TransactionalMail(ObjectMapper mapper, HttpClient client, URI endpoint) {
        this.mapper=mapper; this.client=client; this.endpoint=endpoint;
    }
    public boolean available() {
        try {
            var uri=URI.create(origin);
            return "brevo".equalsIgnoreCase(provider) && enabled && !apiKey.isBlank() && validSender()
                && "https".equals(uri.getScheme()) && uri.getHost()!=null && uri.getUserInfo()==null
                && uri.getQuery()==null && uri.getFragment()==null
                && (uri.getPath().isEmpty() || "/".equals(uri.getPath())) && (!testMode || !testRecipient.isBlank());
        } catch (RuntimeException e) { return false; }
    }
    private boolean validSender() {
        return senderEmail != null && senderEmail.matches("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }
    public boolean sandboxAllows(String recipient) {
        return recipient!=null && !recipient.isBlank() && (!testMode ||
            Arrays.stream(testRecipient.split(",")).anyMatch(v->recipient.equalsIgnoreCase(v.trim())));
    }
    /** A configured sandbox exclusion is intentional; missing configuration is an observable failure. */
    public boolean sandboxExcludes(String recipient) {
        return recipient==null || recipient.isBlank() || (testMode && !testRecipient.isBlank() && !sandboxAllows(recipient));
    }
    public boolean permits(String recipient) { return available() && sandboxAllows(recipient); }
    public String link(String path) { return origin.replaceAll("/+$", "") + path; }
    public enum Outcome { ACCEPTED, RETRYABLE, FAILED, UNKNOWN }
    public static class MailFailure extends IllegalStateException {
        private final Outcome outcome;
        public MailFailure(Outcome outcome,String reason) { super(reason); this.outcome=outcome; }
        public Outcome outcome() { return outcome; }
    }
    public void send(String recipient,String subject,String text,String key) {
        if(!permits(recipient)) throw new MailFailure(Outcome.FAILED,"CONFIG_OR_RECIPIENT_BLOCKED");
        try {
            var body=mapper.writeValueAsString(Map.of("sender",Map.of("email",senderEmail,"name",senderName),
                "to",List.of(Map.of("email",recipient)),"subject",subject,"textContent",text,"htmlContent",html(text),
                "headers",Map.of("IdempotencyKey",UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString())));
            var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10))
                .header("api-key",apiKey).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            // Never retain, log, or expose provider response bodies.
            int status=client.send(request,HttpResponse.BodyHandlers.discarding()).statusCode();
            if(status>=200 && status<300) return;
            if(status==429 || status>=500) throw new MailFailure(Outcome.RETRYABLE,"HTTP_"+status);
            throw new MailFailure(Outcome.FAILED,status==401||status==403?"PERMISSION_DENIED":"HTTP_"+status);
        } catch(HttpConnectTimeoutException | ConnectException | UnknownHostException e) {
            throw new MailFailure(Outcome.RETRYABLE,"CONNECTION_FAILED");
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt(); throw new MailFailure(Outcome.UNKNOWN,"INTERRUPTED");
        } catch(IOException e) {
            // Includes read timeouts, connection resets and partial responses: acceptance is ambiguous.
            throw new MailFailure(Outcome.UNKNOWN,"AMBIGUOUS_IO");
        }
    }
    private String html(String text) {
        return "<html><body>"+text.lines().map(line->{
            String safe=escape(line);
            return line.startsWith(link("/")) && !line.matches(".*\\s.*")
                ? "<p><a href=\""+safe+"\" disable-tracking=\"true\">Mở liên kết ANH LON SHOP</a></p>"
                : "<p>"+safe+"</p>";
        }).collect(java.util.stream.Collectors.joining())+"</body></html>";
    }
    private static String escape(String value) {
        return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
            .replace("\"","&quot;").replace("'","&#39;");
    }

}
