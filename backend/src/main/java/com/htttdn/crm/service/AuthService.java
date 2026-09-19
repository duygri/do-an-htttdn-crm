package com.htttdn.crm.service;

import com.fasterxml.jackson.databind.JsonNode; import com.fasterxml.jackson.databind.ObjectMapper;
import com.htttdn.crm.entity.*; import com.htttdn.crm.exception.ApiException; import com.htttdn.crm.repository.*; import com.htttdn.crm.security.JwtTokenService;
import org.springframework.beans.factory.annotation.Value; import org.springframework.http.*; import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets; import java.security.*; import java.time.*; import java.util.*; import javax.crypto.Mac; import javax.crypto.spec.SecretKeySpec;

@Service
public class AuthService {
    private final UserRepository users; private final RefreshTokenRepository refreshTokens; private final PasswordResetTokenRepository resetTokens; private final PasswordEncoder encoder; private final ObjectMapper mapper; private final JwtTokenService jwt;
    @Value("${app.auth.jwt-secret}") private String jwtSecret; @Value("${app.auth.access-minutes:15}") private long accessMinutes; @Value("${app.auth.refresh-days:30}") private long refreshDays; @Value("${app.auth.secure-cookie:false}") private boolean secureCookie; @Value("${app.auth.reset-code-minutes:15}") private long resetCodeMinutes;
    public AuthService(UserRepository users,RefreshTokenRepository refreshTokens,PasswordResetTokenRepository resetTokens,PasswordEncoder encoder,ObjectMapper mapper,JwtTokenService jwt){this.users=users;this.refreshTokens=refreshTokens;this.resetTokens=resetTokens;this.encoder=encoder;this.mapper=mapper;this.jwt=jwt;}

    @Transactional
    public User register(String email,String password,String fullName,String phone,Short age){
        String normalized=email==null?"":email.trim().toLowerCase(Locale.ROOT); validatePassword(password);
        if(normalized.isBlank() || !normalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Email không hợp lệ.");
        if(fullName==null || fullName.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Vui lòng nhập họ và tên.");
        if(users.existsByEmailIgnoreCase(normalized)) throw new ApiException(HttpStatus.CONFLICT,"EMAIL_ALREADY_EXISTS","Email này đã được đăng ký.");
        User user=new User(); user.setEmail(normalized); user.setFullName(fullName.trim()); user.setPhone(phone==null?null:phone.trim()); user.setAge(age); user.setRole("CUSTOMER"); user.setPasswordHash(encoder.encode(password));
        try{return users.saveAndFlush(user);}catch(Exception e){if(e.getCause()!=null)throw new ApiException(HttpStatus.CONFLICT,"EMAIL_ALREADY_EXISTS","Email này đã được đăng ký.");throw e;}
    }
    public void validatePassword(String password){if(password==null || password.length()<8 || !password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Mật khẩu cần ít nhất 8 ký tự, gồm chữ và số.");}
    @Transactional public Session login(String email,String password){
        User user=users.findByEmailIgnoreCase(email==null?"":email.trim()).orElse(null);
        if(user==null || !"CUSTOMER".equals(user.getRole()) || user.getPasswordHash()==null || !encoder.matches(password==null?"":password,user.getPasswordHash())) throw new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_CREDENTIALS","Email hoặc mật khẩu không đúng.");
        if(user.isLocked()) throw new ApiException(HttpStatus.LOCKED,"ACCOUNT_LOCKED","Tài khoản đang bị khóa.");
        return issue(user,UUID.randomUUID().toString());
    }
    @Transactional(noRollbackFor=ApiException.class) public Session refresh(String raw){
        if(raw==null || raw.isBlank()) throw new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_REFRESH_TOKEN","Phiên đăng nhập đã hết hạn.");
        RefreshToken token=refreshTokens.findForUpdate(hash(raw)).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_REFRESH_TOKEN","Phiên đăng nhập không hợp lệ."));
        if("ADMIN".equals(token.getAudience())) throw new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_REFRESH_TOKEN","Phiên customer không hợp lệ.");
        if("ROTATED".equals(token.getStatus())){refreshTokens.revokeFamily(token.getFamilyId(),"REUSE_DETECTED");throw new ApiException(HttpStatus.UNAUTHORIZED,"REFRESH_TOKEN_REUSE_DETECTED","Phát hiện refresh token bị sử dụng lại. Vui lòng đăng nhập lại.");}
        if(!"ACTIVE".equals(token.getStatus())) throw new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_REFRESH_TOKEN","Phiên đăng nhập không còn hiệu lực.");
        if(token.getExpiresAt().isBefore(Instant.now())){token.setStatus("REVOKED");token.setRevokeReason("EXPIRED");token.setRevokedAt(Instant.now());refreshTokens.save(token);throw new ApiException(HttpStatus.UNAUTHORIZED,"REFRESH_TOKEN_EXPIRED","Refresh token đã hết hạn.");}
        token.setStatus("ROTATED"); token.setRevokeReason("ROTATED"); token.setRevokedAt(Instant.now()); refreshTokens.save(token);
        User user=token.getUser(); if(!"CUSTOMER".equals(user.getRole())) throw new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_REFRESH_TOKEN","Phiên customer không hợp lệ."); if(user.isLocked()) throw new ApiException(HttpStatus.LOCKED,"ACCOUNT_LOCKED","Tài khoản đang bị khóa.");
        return issue(user,token.getFamilyId());
    }
    @Transactional public void logout(String raw){if(raw==null||raw.isBlank())return;refreshTokens.findForUpdate(hash(raw)).filter(t->!"ADMIN".equals(t.getAudience())).ifPresent(t->{if("ACTIVE".equals(t.getStatus())){t.setStatus("REVOKED");t.setRevokeReason("LOGOUT");t.setRevokedAt(Instant.now());refreshTokens.save(t);}});}
    @Transactional public User saveProfile(User user){return users.save(user);}
    @Transactional public void changePassword(User user,String currentPassword,String newPassword){if(!encoder.matches(currentPassword==null?"":currentPassword,user.getPasswordHash()))throw new ApiException(HttpStatus.BAD_REQUEST,"CURRENT_PASSWORD_INVALID","Mật khẩu hiện tại không đúng.");validatePassword(newPassword);user.setPasswordHash(encoder.encode(newPassword));users.save(user);refreshTokens.revokeByUserId(user.getId());}
    @Transactional public String requestPasswordReset(String email){String normalized=email==null?"":email.trim().toLowerCase(Locale.ROOT);User user=users.findByEmailIgnoreCase(normalized).orElse(null);if(user==null||!"CUSTOMER".equals(user.getRole()))return null;resetTokens.deleteByCustomerId(user.getId());String code=String.format("%06d",new SecureRandom().nextInt(1_000_000));PasswordResetToken token=new PasswordResetToken();token.setCustomer(user);token.setTokenHash(hash(code));token.setExpiresAt(Instant.now().plus(Duration.ofMinutes(resetCodeMinutes)));resetTokens.save(token);return code;}
    @Transactional public void resetPassword(String email,String code,String newPassword){validatePassword(newPassword);String normalized=email==null?"":email.trim().toLowerCase(Locale.ROOT);PasswordResetToken token=resetTokens.findByTokenHash(hash(code==null?"":code.trim())).orElseThrow(()->new ApiException(HttpStatus.BAD_REQUEST,"RESET_CODE_INVALID","Mã đặt lại mật khẩu không đúng."));if(token.getUsedAt()!=null||token.getExpiresAt().isBefore(Instant.now())||!normalized.equalsIgnoreCase(token.getCustomer().getEmail()))throw new ApiException(HttpStatus.BAD_REQUEST,"RESET_CODE_INVALID","Mã đặt lại mật khẩu đã hết hạn hoặc không hợp lệ.");User user=token.getCustomer();user.setPasswordHash(encoder.encode(newPassword));users.save(user);token.setUsedAt(Instant.now());resetTokens.save(token);refreshTokens.revokeByUserId(user.getId());}
    public ResponseCookie cookie(String raw){return ResponseCookie.from("refreshToken",raw).httpOnly(true).secure(secureCookie).sameSite("Lax").path("/api/auth").maxAge(Duration.ofDays(refreshDays)).build();}
    public ResponseCookie clearCookie(){return ResponseCookie.from("refreshToken","").httpOnly(true).secure(secureCookie).sameSite("Lax").path("/api/auth").maxAge(Duration.ZERO).build();}
    public User requireCustomer(String authorization){
        JwtTokenService.Claims claims=jwt.parse(authorization); if(!"CUSTOMER".equals(claims.role())) throw new ApiException(HttpStatus.FORBIDDEN,"CUSTOMER_REQUIRED","Customer token required.");
        User user=users.findById(claims.userId()).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","Vui lòng đăng nhập."));
        if(!"CUSTOMER".equals(user.getRole()) || user.isLocked()) throw new ApiException(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","Phiên đăng nhập không hợp lệ."); return user;
    }
    private Session issue(User user,String family){String raw=UUID.randomUUID()+"."+UUID.randomUUID();RefreshToken token=new RefreshToken();token.setUser(user);token.setAudience("CUSTOMER");token.setFamilyId(family);token.setTokenHash(hash(raw));token.setExpiresAt(Instant.now().plus(Duration.ofDays(refreshDays)));refreshTokens.save(token);return new Session(jwt.issue(user.getId(),"CUSTOMER"),raw,accessMinutes*60,user);}
    public static String hash(String value){try{byte[] b=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format("%02x",x));return s.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    public record Session(String accessToken,String refreshToken,long expiresIn,User user){}
}
