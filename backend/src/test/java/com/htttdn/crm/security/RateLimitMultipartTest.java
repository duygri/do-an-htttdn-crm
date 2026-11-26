package com.htttdn.crm.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class RateLimitMultipartTest {
    private RateLimitFilter filter() {
        return new RateLimitFilter(new RateLimitStore(Clock.fixed(Instant.EPOCH,ZoneOffset.UTC),100,1,3,15),
            new ObjectMapper(),true,false,1024*1024,Set.of());
    }
    @ParameterizedTest
    @ValueSource(strings={"/api/attachments","/api/manager/attachments"})
    void uploadBodyRemainsUnreadAndGlobalRateLimitStillApplies(String path) throws Exception {
        var filter=filter();
        var request=new MockHttpServletRequest("POST",path);
        request.setContentType("multipart/form-data; boundary=upload");
        byte[] body=new byte[5*1024*1024];body[0]=42;request.setContent(body);
        var response=new MockHttpServletResponse();
        filter.doFilter(request,response,(incoming,outgoing)->{
            assertSame(request,incoming);
            assertEquals(body.length,incoming.getInputStream().available());
            assertEquals(42,incoming.getInputStream().read());
        });
        assertEquals(200,response.getStatus());
        var next=new MockHttpServletRequest("POST",path);next.setContentType(request.getContentType());next.setContent(body);
        var blocked=new MockHttpServletResponse();
        filter.doFilter(next,blocked,(incoming,outgoing)->fail("Upload must still be rate limited"));
        assertEquals(429,blocked.getStatus());assertNotNull(blocked.getHeader("Retry-After"));
        assertEquals(body.length,next.getInputStream().available());
    }
    @ParameterizedTest
    @ValueSource(strings={"/api/other","/api/attachments/extra","/api/auth/login"})
    void otherMultipartPostsKeepBodyLimit(String path) throws Exception {
        var request=new MockHttpServletRequest("POST",path);request.setContentType("multipart/form-data; boundary=upload");
        request.setContent(new byte[1024*1024+1]);var response=new MockHttpServletResponse();
        filter().doFilter(request,response,(incoming,outgoing)->fail("Oversized non-upload route"));
        assertEquals(413,response.getStatus());
    }
    @ParameterizedTest
    @ValueSource(strings={"application/json","multipart/form-data-other"})
    void uploadRouteWithOtherContentTypeKeepsBodyLimit(String contentType) throws Exception {
        var request=new MockHttpServletRequest("POST","/api/attachments");request.setContentType(contentType);
        request.setContent(new byte[1024*1024+1]);var response=new MockHttpServletResponse();
        filter().doFilter(request,response,(incoming,outgoing)->fail("Oversized non-multipart request"));
        assertEquals(413,response.getStatus());
    }
}
