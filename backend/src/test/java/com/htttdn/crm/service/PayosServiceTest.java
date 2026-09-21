package com.htttdn.crm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayosServiceTest {
    private static final String CHECKSUM_KEY = "test-checksum-key";
    private final ObjectMapper mapper = new ObjectMapper();
    private PayosService payos;

    @BeforeEach
    void setUp() {
        payos = new PayosService(mapper);
        ReflectionTestUtils.setField(payos, "checksumKey", CHECKSUM_KEY);
    }

    @Test
    void rejectsMissingOrInvalidWebhookSignature() throws Exception {
        JsonNode data = mapper.readTree("{\"amount\":699000,\"orderCode\":123456,\"code\":\"00\"}");

        assertFalse(payos.verifyWebhook(data, null));
        assertFalse(payos.verifyWebhook(data, "invalid-signature"));
    }

    @Test
    void acceptsValidWebhookSignature() throws Exception {
        JsonNode data = mapper.readTree("{\"amount\":699000,\"orderCode\":123456,\"code\":\"00\"}");
        String signature = hmac(payos.canonical(data), CHECKSUM_KEY);

        assertTrue(payos.verifyWebhook(data, signature));
    }

    private String hmac(String value, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        StringBuilder result = new StringBuilder();
        for (byte valueByte : mac.doFinal(value.getBytes(StandardCharsets.UTF_8))) {
            result.append(String.format("%02x", valueByte));
        }
        return result.toString();
    }
}
