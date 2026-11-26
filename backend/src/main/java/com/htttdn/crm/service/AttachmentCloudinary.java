package com.htttdn.crm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.htttdn.crm.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** All provider traffic is server-side. Never return provider URLs or response bodies to clients. */
@Component
public class AttachmentCloudinary {
    private final RestClient http;
    private final String cloud,key,secret;
    @org.springframework.beans.factory.annotation.Autowired
    public AttachmentCloudinary(@Value("${CLOUDINARY_CLOUD_NAME:}") String cloud,
        @Value("${CLOUDINARY_API_KEY:}") String key,@Value("${CLOUDINARY_API_SECRET:}") String secret) {
        this(cloud,key,secret,client());
    }
    AttachmentCloudinary(String cloud,String key,String secret,RestClient http){this.cloud=cloud;this.key=key;this.secret=secret;this.http=http;}
    private static RestClient client(){var f=new org.springframework.http.client.SimpleClientHttpRequestFactory();f.setConnectTimeout(5000);f.setReadTimeout(20000);return RestClient.builder().requestFactory(f).build();}
    public boolean configured(){return cloud.matches("[a-zA-Z0-9_-]+")&&!key.isBlank()&&!secret.isBlank();}
    public void available(){if(!configured())throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"ATTACHMENTS_DISABLED","Tải ảnh chưa được cấu hình.");}
    private String endpoint(String action){available();return "https://api.cloudinary.com/v1_1/"+cloud+"/image/"+action;}
    String signature(Map<String,String> params){try{String data=params.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->e.getKey()+"="+e.getValue()).collect(java.util.stream.Collectors.joining("&"));return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest((data+secret).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private LinkedMultiValueMap<String,Object> form(Map<String,String> params){var form=new LinkedMultiValueMap<String,Object>();params.forEach(form::add);form.add("api_key",key);form.add("signature",signature(params));return form;}
    public void upload(String publicId,byte[] bytes,String format){
        var params=new TreeMap<String,String>();params.put("public_id",publicId);params.put("type","authenticated");params.put("overwrite","false");params.put("timestamp",""+Instant.now().getEpochSecond());
        var body=form(params);body.add("file",new ByteArrayResource(bytes){@Override public String getFilename(){return "attachment."+format;}});
        try{String response=http.post().uri(endpoint("upload")).contentType(MediaType.MULTIPART_FORM_DATA).body(body).retrieve().body(String.class);
            var node=new ObjectMapper().readTree(response);if(!publicId.equals(node.path("public_id").asText())||!"authenticated".equals(node.path("type").asText())||!format.equals(node.path("format").asText()))throw new IOException("Invalid provider metadata");
        }catch(Exception e){throw unavailable();}
    }
    public void destroy(String publicId){var p=new TreeMap<String,String>();p.put("public_id",publicId);p.put("type","authenticated");p.put("invalidate","true");p.put("timestamp",""+Instant.now().getEpochSecond());try{var response=http.post().uri(endpoint("destroy")).body(form(p)).retrieve().body(String.class);String result=new ObjectMapper().readTree(response).path("result").asText();if(!Set.of("ok","not found").contains(result))throw new IOException();}catch(Exception e){throw unavailable();}}
    public void stream(String publicId,String format,OutputStream output){
        var p=new TreeMap<String,String>();p.put("public_id",publicId);p.put("format",format);p.put("type","authenticated");p.put("expires_at",""+(Instant.now().getEpochSecond()+60));p.put("attachment","true");
        String query=new TreeMap<>(p).entrySet().stream().map(e->enc(e.getKey())+"="+enc(e.getValue())).collect(java.util.stream.Collectors.joining("&"));
        String url=endpoint("download")+"?"+query+"&api_key="+enc(key)+"&signature="+signature(p);
        try{http.get().uri(java.net.URI.create(url)).exchange((request,response)->{if(!response.getStatusCode().is2xxSuccessful())throw new IOException("Provider unavailable");long total=0;byte[] buffer=new byte[8192];int n;while((n=response.getBody().read(buffer))!=-1){total+=n;if(total>AttachmentService.MAX_BYTES)throw new IOException("Asset exceeds limit");output.write(buffer,0,n);}return null;});}catch(Exception e){throw unavailable();}
    }
    private static String enc(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
    private ApiException unavailable(){return new ApiException(HttpStatus.BAD_GATEWAY,"ATTACHMENT_PROVIDER_ERROR","Không thể xử lý ảnh. Vui lòng thử lại.");}
}
