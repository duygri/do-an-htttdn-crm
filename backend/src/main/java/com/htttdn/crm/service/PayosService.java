package com.htttdn.crm.service;

import com.fasterxml.jackson.databind.JsonNode; import com.fasterxml.jackson.databind.ObjectMapper; import com.htttdn.crm.entity.Order; import com.htttdn.crm.exception.ApiException; import org.springframework.beans.factory.annotation.Value; import org.springframework.http.*; import org.springframework.stereotype.Service; import org.springframework.web.client.RestClient; import java.math.BigDecimal; import java.nio.charset.StandardCharsets; import java.security.MessageDigest; import java.util.*; import javax.crypto.Mac; import javax.crypto.spec.SecretKeySpec;

@Service
public class PayosService {
    private final ObjectMapper mapper;
    @Value("${app.payos.client-id:}") private String clientId; @Value("${app.payos.api-key:}") private String apiKey; @Value("${app.payos.checksum-key:}") private String checksumKey; @Value("${app.payos.return-url}") private String returnUrl; @Value("${app.payos.cancel-url}") private String cancelUrl;
    public PayosService(ObjectMapper mapper){this.mapper=mapper;}
    public Link createPaymentLink(Order order){
        if(clientId==null||clientId.isBlank()||apiKey==null||apiKey.isBlank())return new Link("MOCK-"+order.getOrderCode(),"http://localhost:5173/thanh-toan?orderCode="+order.getOrderCode()+"&mock=true");
        Map<String,Object> body=new LinkedHashMap<>();body.put("orderCode",order.getOrderCode());body.put("amount",order.getTotalAmount().longValue());body.put("description","Thanh toan "+order.getOrderCode());body.put("cancelUrl",cancelUrl);body.put("returnUrl",returnUrl);body.put("items",order.getItems().stream().map(i->Map.of("name",i.getProduct().getName(),"quantity",i.getQuantity(),"price",i.getUnitPrice().longValue())).toList());
        if (checksumKey != null && !checksumKey.isBlank()) body.put("signature", requestSignature(body));
        RestClient client=RestClient.builder().baseUrl("https://api-merchant.payos.vn").build();JsonNode result=client.post().uri("/v2/payment-requests").header("x-client-id",clientId).header("x-api-key",apiKey).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);JsonNode data=result==null?null:result.path("data");if(data==null||data.path("checkoutUrl").asText("").isBlank())throw new ApiException(HttpStatus.BAD_GATEWAY,"PAYMENT_PROVIDER_ERROR","Không thể tạo liên kết thanh toán.");return new Link(data.path("paymentLinkId").asText(),data.path("checkoutUrl").asText());
    }
    public boolean verifyWebhook(JsonNode data,String signature){if(checksumKey==null||checksumKey.isBlank())return true;if(signature==null||signature.isBlank())return false;return MessageDigest.isEqual(signature.getBytes(StandardCharsets.UTF_8),hmac(canonical(data),checksumKey).getBytes(StandardCharsets.UTF_8));}
    public String canonical(JsonNode data){List<String> keys=new ArrayList<>();data.fieldNames().forEachRemaining(keys::add);Collections.sort(keys);List<String> pairs=new ArrayList<>();for(String key:keys){JsonNode value=data.get(key);pairs.add(key+"="+(value==null||value.isNull()?"":value.isValueNode()?value.asText():value.toString()));}return String.join("&",pairs);}
    private String requestSignature(Map<String,Object> body){List<String> keys=List.of("amount","cancelUrl","description","orderCode","returnUrl");List<String> pairs=keys.stream().map(key->key+"="+String.valueOf(body.get(key))).toList();return hmac(String.join("&",pairs),checksumKey);}
    private String hmac(String value,String key){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));byte[] out=mac.doFinal(value.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:out)s.append(String.format("%02x",b));return s.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    public record Link(String paymentLinkId,String checkoutUrl){}
}
