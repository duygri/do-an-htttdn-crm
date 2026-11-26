package com.htttdn.crm.controller;

import com.htttdn.crm.entity.User;
import com.htttdn.crm.repository.UserRepository;
import com.htttdn.crm.security.JwtTokenService;
import com.htttdn.crm.service.*;
import com.htttdn.crm.exception.ApiException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController
public class AttachmentController {
    private final AttachmentService service;private final JwtTokenService jwt;private final UserRepository users;
    public AttachmentController(AttachmentService service,JwtTokenService jwt,UserRepository users){this.service=service;this.jwt=jwt;this.users=users;}
    @GetMapping({"/api/attachments/config","/api/manager/attachments/config"})
    public Object config(@RequestHeader(value="Authorization",required=false)String token){actor(token);return java.util.Map.of("enabled",service.enabled());}
    private User actor(String token){var claims=jwt.parse(token);var u=users.findById(claims.userId()).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","Vui lòng đăng nhập."));if(!claims.role().equals(u.getRole())||u.isLocked()||u.getDeletedAt()!=null||!java.util.Set.of("CUSTOMER","MANAGER").contains(u.getRole()))throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Không có quyền truy cập ảnh.");return u;}
    @PostMapping(value={"/api/attachments","/api/manager/attachments"},consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@RequestHeader(value="Authorization",required=false)String token,@RequestPart("file")MultipartFile file){return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(service.upload(actor(token).getId(),file));}
    @GetMapping("/api/manager/orders/{id}/attachments")
    public Object returnAttachments(@RequestHeader(value="Authorization",required=false)String token,@PathVariable long id){if(!"MANAGER".equals(actor(token).getRole()))throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Không có quyền truy cập.");return service.returnAttachmentsForManager(id);}
    @GetMapping({"/api/attachments/{id}/content","/api/manager/attachments/{id}/content"})
    public void content(@RequestHeader(value="Authorization",required=false)String token,@PathVariable String id,HttpServletResponse response)throws IOException{
        var m=service.authorized(actor(token),id);response.setContentType(m.contentType());response.setHeader("Cache-Control","private, no-store, max-age=0");response.setHeader("Pragma","no-cache");response.setHeader("X-Content-Type-Options","nosniff");response.setHeader("Content-Disposition",ContentDisposition.attachment().filename(m.name(),java.nio.charset.StandardCharsets.UTF_8).build().toString());service.stream(m,response.getOutputStream());
    }
}
