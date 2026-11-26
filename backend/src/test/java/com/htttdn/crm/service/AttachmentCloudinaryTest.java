package com.htttdn.crm.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AttachmentCloudinaryTest {
    @Test void signedAuthenticatedUploadAndPrivateDownloadUseFakeHttpOnly()throws Exception{
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();var client=new AttachmentCloudinary("demo","test-key","test-secret",builder.build());
        server.expect(requestTo("https://api.cloudinary.com/v1_1/demo/image/upload")).andExpect(method(HttpMethod.POST)).andExpect(request->{
            var body=((org.springframework.mock.http.client.MockClientHttpRequest)request).getBodyAsString(StandardCharsets.UTF_8);
            assertTrue(body.contains("authenticated"));assertTrue(body.contains("signature"));assertTrue(body.contains("test-key"));assertFalse(body.contains("test-secret"));
        }).andRespond(withSuccess("{\"public_id\":\"crm_attachments/1/test\",\"type\":\"authenticated\",\"format\":\"png\"}",MediaType.APPLICATION_JSON));
        byte[] data=AttachmentValidationTest.image("png");
        server.expect(request->{var uri=request.getURI();assertEquals("/v1_1/demo/image/download",uri.getPath());var query=uri.getRawQuery();assertTrue(query.contains("type=authenticated"));assertTrue(query.contains("expires_at="));assertTrue(query.contains("signature="));assertFalse(query.contains("test-secret"));}).andRespond(withSuccess(data,MediaType.IMAGE_PNG));
        client.upload("crm_attachments/1/test",data,"png");
        var out=new ByteArrayOutputStream();client.stream("crm_attachments/1/test","png",out);assertArrayEquals(data,out.toByteArray());server.verify();
    }
    @Test void providerFailuresDoNotLeakSecretsOrSignedUrls(){
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();var client=new AttachmentCloudinary("demo","key","TOP_SECRET",builder.build());
        server.expect(anything()).andRespond(withServerError().body("signed-url-secret"));var error=assertThrows(com.htttdn.crm.exception.ApiException.class,()->client.upload("test",new byte[]{1},"png"));assertEquals("ATTACHMENT_PROVIDER_ERROR",error.code());assertFalse(error.getMessage().contains("SECRET"));assertFalse(error.getMessage().contains("signed-url"));server.verify();
    }
    @Test void deletingAuthenticatedAssetIsSignedAndNotFoundIsIdempotent(){
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();var client=new AttachmentCloudinary("demo","key","secret",builder.build());
        server.expect(requestTo("https://api.cloudinary.com/v1_1/demo/image/destroy")).andExpect(content().string(org.hamcrest.Matchers.containsString("type=authenticated"))).andExpect(content().string(org.hamcrest.Matchers.containsString("signature="))).andRespond(withSuccess("{\"result\":\"not found\"}",MediaType.APPLICATION_JSON));client.destroy("test");server.verify();
    }
    @Test void missingConfigurationFailsClosed(){assertThrows(com.htttdn.crm.exception.ApiException.class,()->new AttachmentCloudinary("","","",RestClient.create()).available());}
}
