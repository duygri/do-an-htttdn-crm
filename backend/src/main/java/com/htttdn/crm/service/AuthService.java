package com.htttdn.crm.service;

import com.fasterxml.jackson.databind.JsonNode; import com.fasterxml.jackson.databind.ObjectMapper;
import com.htttdn.crm.entity.*; import com.htttdn.crm.exception.ApiException; import com.htttdn.crm.repository.*;
import org.springframework.beans.factory.annotation.Value; import org.springframework.http.*; import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets; import java.security.*; import java.time.*; import java.util.*; import javax.crypto.Mac; import javax.crypto.spec.SecretKeySpec;

@Service
public class AuthService {
    private final UserRepository users; private final RefreshTokenRepository refreshTokens; private final PasswordEncoder encoder; private final ObjectMapper mapper;
    @Value("${app.auth.jwt-secret}") private String jwtSecret; @Value("${app.auth.access-minutes:15}") private long accessMinutes; @Value("${app.auth.refresh-days:30}") private long refreshDays; @Value("${app.auth.secure-cookie:false}") private boolean secureCookie;
    public AuthService(UserRepository users,RefreshTokenRepository refreshTokens,PasswordEncoder encoder,ObjectMapper mapper){this.users=users;this.refreshTokens=refreshTokens;this.encoder=encoder;this.mapper=mapper;}

    @Transactional
    public User register(String email,String password,String fullName,String phone){
        String normalized=email==null?"":email.trim().toLowerCase(Locale.ROOT); validatePassword(password);
        if(normalized.isBlank() || !normalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Email không hợp lệ.");
        if(fullName==null || fullName.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Vui lòng nhập họ và tên.");
        if(users.existsByEmailIgnoreCase(normalized)) throw new ApiException(HttpStatus.CONFLICT,"EMAIL_ALREADY_EXISTS","Email này đã được đăng ký.");
        User user=new User(); user.setEmail(normalized); user.setFullName(fullName.trim()); user.setPhone(phone==null?null:phone.trim()); user.setRole("CUSTOMER"); user.setPasswordHash(encoder.encode(password));
        try{return users.saveAndFlush(user);}catch(Exception e){if(e.getCause()!=null)throw new ApiException(HttpStatus.CONFLICT,"EMAIL_ALREADY_EXISTS","Email này đã được đăng ký.");throw e;}
    }
    public void validatePassword(String password){if(password==null || password.length()<8 || !password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Mật khẩu cần ít nhất 8 ký tự, gồm chữ và số.");}
    @Transactional public Session login(String email,String password){
        User user=users.findByEmailIgnoreCase(email==null?"":email.trim()).orElse(null);
        if(user==null || user.getPasswordHash()==null || !encoder.matches(password==null?"":password,user.getPasswordHash())) throw new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_CREDENTIALS","Email hoặc mật khẩu không đúng.");
        if(user.isLocked()) throw new ApiException(HttpStatus.LOCKED,"ACCOUNT_LOCKED","Tài khoản đang bị khóa.");
        return issue(user,UUID.randomUUID().toString());
    }
    @Transactional(noRollbackFor=ApiException.class) public Session refresh(String raw){
        if(raw==null || raw.isBlank()) throw new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_REFRESH_TOKEN","Phiên đăng nhập đã hết hạn.");
        RefreshToken token=refreshTokens.findForUpdate(hash(raw)).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_REFRESH_TOKEN","Phiên đăng nhập không hợp lệ."));
        if("ROTATED".equals(token.getStatus())){refreshTokens.revokeFamily(token.getFamilyId(),"REUSE_DETECTED");throw new ApiException(HttpStatus.UNAUTHORIZED,"REFRESH_TOKEN_REUSE_DETECTED","Phát hiện refresh token bị sử dụng lại. Vui lòng đăng nhập lại.");}
        if(!"ACTIVE".equals(token.getStatus())) throw new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_REFRESH_TOKEN","Phiên đăng nhập không còn hiệu lực.");
        if(token.getExpiresAt().isBefore(Instant.now())){token.setStatus("REVOKED");token.setRevokeReason("EXPIRED");token.setRevokedAt(Instant.now());refreshTokens.save(token);throw new ApiException(HttpStatus.UNAUTHORIZED,"REFRESH_TOKEN_EXPIRED","Refresh token đã hết hạn.");}
        token.setStatus("ROTATED"); token.setRevokeReason("ROTATED"); token.setRevokedAt(Instant.now()); refreshTokens.save(token);
        User user=token.getUser(); if(user.isLocked()) throw new ApiException(HttpStatus.LOCKED,"ACCOUNT_LOCKED","Tài khoản đang bị khóa.");
        return issue(user,token.getFamilyId());
    }
    @Transactional public void logout(String raw){if(raw==null||raw.isBlank())return;refreshTokens.findForUpdate(hash(raw)).ifPresent(t->{if("ACTIVE".equals(t.getStatus())){t.setStatus("REVOKED");t.setRevokeReason("LOGOUT");t.setRevokedAt(Instant.now());refreshTokens.save(t);}});}
    @Transactional public User saveProfile(User user){return users.save(user);}
    public ResponseCookie cookie(String raw){return ResponseCookie.from("refreshToken",raw).httpOnly(true).secure(secureCookie).sameSite("Lax").path("/api/auth").maxAge(Duration.ofDays(refreshDays)).build();}
    public ResponseCookie clearCookie(){return ResponseCookie.from("refreshToken","").httpOnly(true).secure(secureCookie).sameSite("Lax").path("/api/auth").maxAge(Duration.ZERO).build();}
    public User requireCustomer(String authorization){
        long id=parseAccessToken(authorization); User user=users.findById(id).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","Vui lòng đăng nhập."));
        if(!"CUSTOMER".equals(user.getRole()) || user.isLocked()) throw new ApiException(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","Phiên đăng nhập không hợp lệ."); return user;
    }
    private Session issue(User user,String family){String raw=UUID.randomUUID()+"."+UUID.randomUUID();RefreshToken token=new RefreshToken();token.setUser(user);token.setFamilyId(family);token.setTokenHash(hash(raw));token.setExpiresAt(Instant.now().plus(Duration.ofDays(refreshDays)));refreshTokens.save(token);return new Session(accessToken(user),raw,accessMinutes*60,user);}
    private String accessToken(User user){try{long exp=Instant.now().plusSeconds(accessMinutes*60).getEpochSecond();String header=base64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");String payload=base64("{\"sub\":"+user.getId()+",\"role\":\"CUSTOMER\",\"exp\":"+exp+"}");return header+"."+payload+"."+sign(header+"."+payload);}catch(Exception e){throw new IllegalStateException(e);}}
    private long parseAccessToken(String authorization){try{if(authorization==null||!authorization.startsWith("Bearer "))throw new Exception();String[] p=authorization.substring(7).split("\\.");if(p.length!=3||!MessageDigest.isEqual(p[2].getBytes(StandardCharsets.UTF_8),sign(p[0]+"."+p[1]).getBytes(StandardCharsets.UTF_8)))throw new Exception();JsonNode n=mapper.readTree(new String(Base64.getUrlDecoder().decode(p[1]),StandardCharsets.UTF_8));if(n.path("exp").asLong(0)<Instant.now().getEpochSecond())throw new Exception();return n.path("sub").asLong(0);}catch(Exception e){throw new ApiException(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","Token đăng nhập không hợp lệ hoặc đã hết hạn.");}}
    private String base64(String value){return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));}
    private String sign(String value){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    public static String hash(String value){try{byte[] b=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format("%02x",x));return s.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    public record Session(String accessToken,String refreshToken,long expiresIn,User user){}
}
