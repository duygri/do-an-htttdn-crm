package com.htttdn.crm.controller;

import com.htttdn.crm.entity.User; import com.htttdn.crm.service.AuthService;
import jakarta.servlet.http.*; import jakarta.validation.Valid; import jakarta.validation.constraints.*; import org.springframework.http.*; import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth; public AuthController(AuthService auth){this.auth=auth;}
    @PostMapping("/register") public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest r){User u=auth.register(r.email(),r.password(),r.fullName(),r.phone());return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message","Đăng ký tài khoản thành công.","customer",u));}
    @PostMapping("/login") public ResponseEntity<?> login(@Valid @RequestBody LoginRequest r,HttpServletResponse response){AuthService.Session s=auth.login(r.email(),r.password());response.addHeader(HttpHeaders.SET_COOKIE,auth.cookie(s.refreshToken()).toString());return ResponseEntity.ok(Map.of("accessToken",s.accessToken(),"expiresIn",s.expiresIn(),"customer",s.user()));}
    @PostMapping("/refresh") public ResponseEntity<?> refresh(HttpServletRequest request,HttpServletResponse response){AuthService.Session s=auth.refresh(cookie(request));response.addHeader(HttpHeaders.SET_COOKIE,auth.cookie(s.refreshToken()).toString());return ResponseEntity.ok(Map.of("accessToken",s.accessToken(),"expiresIn",s.expiresIn(),"customer",s.user()));}
    @PostMapping("/logout") public ResponseEntity<Void> logout(HttpServletRequest request,HttpServletResponse response){auth.logout(cookie(request));response.addHeader(HttpHeaders.SET_COOKIE,auth.clearCookie().toString());return ResponseEntity.noContent().build();}
    private String cookie(HttpServletRequest request){if(request.getCookies()==null)return null;return Arrays.stream(request.getCookies()).filter(c->"refreshToken".equals(c.getName())).map(Cookie::getValue).findFirst().orElse(null);}
    public record RegisterRequest(@NotBlank @Email String email,@NotBlank String password,@NotBlank String fullName,String phone){}
    public record LoginRequest(@NotBlank @Email String email,@NotBlank String password){}
}
