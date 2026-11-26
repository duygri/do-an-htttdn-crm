package com.htttdn.crm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.htttdn.crm.controller.AttachmentController;
import com.htttdn.crm.entity.User;
import com.htttdn.crm.exception.*;
import com.htttdn.crm.repository.UserRepository;
import com.htttdn.crm.security.JwtTokenService;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.web.MockMultipartFile;
import java.util.*;
import java.io.OutputStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AttachmentControllerTest {
    AttachmentService service;UserRepository users;JwtTokenService jwt;MockMvc mvc;User customer;
    @BeforeEach void setup(){service=mock(AttachmentService.class);users=mock(UserRepository.class);jwt=new JwtTokenService(new ObjectMapper());ReflectionTestUtils.setField(jwt,"secret","test-only-signing-key-long-enough-for-tests");ReflectionTestUtils.setField(jwt,"accessMinutes",15L);mvc=MockMvcBuilders.standaloneSetup(new AttachmentController(service,jwt,users)).setControllerAdvice(new ApiExceptionHandler()).build();customer=new User();ReflectionTestUtils.setField(customer,"id",1L);customer.setRole("CUSTOMER");when(users.findById(1L)).thenReturn(Optional.of(customer));}
    String token(){return "Bearer "+jwt.issue(1,"CUSTOMER");}
    @Test void configurationReturnsOnlyAvailabilityAndRequiresAuthentication()throws Exception{
        mvc.perform(get("/api/attachments/config")).andExpect(status().isUnauthorized());
        when(service.enabled()).thenReturn(false);
        mvc.perform(get("/api/attachments/config").header("Authorization",token()))
            .andExpect(status().isOk()).andExpect(content().json("{\"enabled\":false}"));
    }
    @Test void unauthenticatedUploadsAndDownloadsAreDenied()throws Exception{
        mvc.perform(multipart("/api/attachments").file(new MockMultipartFile("file","x.png","image/png",new byte[]{1}))).andExpect(status().isUnauthorized());mvc.perform(get("/api/attachments/any/content")).andExpect(status().isUnauthorized());verifyNoInteractions(service);
    }
    @Test void disabledOrRoleChangedActorCannotUseExistingToken()throws Exception{
        String token=token();customer.setLocked(true);mvc.perform(get("/api/attachments/any/content").header("Authorization",token)).andExpect(status().isForbidden());customer.setLocked(false);customer.setRole("ADMIN");mvc.perform(get("/api/attachments/any/content").header("Authorization",token)).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @Test void downloadProxiesBytesAndNeverRedirectsOrExposesProviderUrl()throws Exception{
        var item=new AttachmentService.Metadata("id","PRIVATE_PROVIDER_ID","evidence.png","image/png","png",3,0,"READY",null,null,0,0);when(service.authorized(customer,"id")).thenReturn(item);doAnswer(call->{((OutputStream)call.getArgument(1)).write(new byte[]{1,2,3});return null;}).when(service).stream(eq(item),any());
        var result=mvc.perform(get("/api/attachments/id/content").header("Authorization",token())).andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store, max-age=0")).andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(header().doesNotExist("Location")).andExpect(content().bytes(new byte[]{1,2,3})).andReturn();assertFalse(result.getResponse().getHeaderNames().stream().map(n->result.getResponse().getHeader(n)).anyMatch(v->v.contains("PRIVATE_PROVIDER_ID")));
    }
    @Test void authorizationIsCheckedBeforeStreaming()throws Exception{
        when(service.authorized(customer,"foreign")).thenThrow(new ApiException(org.springframework.http.HttpStatus.NOT_FOUND,"ATTACHMENT_NOT_FOUND","Missing"));mvc.perform(get("/api/attachments/foreign/content").header("Authorization",token())).andExpect(status().isNotFound());verify(service,never()).stream(any(),any());
    }
    @Test void managerReturnMetadataEndpointRejectsCustomer()throws Exception{mvc.perform(get("/api/manager/orders/10/attachments").header("Authorization",token())).andExpect(status().isForbidden());verify(service,never()).returnAttachmentsForManager(anyLong());}
}
