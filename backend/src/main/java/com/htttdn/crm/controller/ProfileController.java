package com.htttdn.crm.controller;

import com.htttdn.crm.entity.User; import com.htttdn.crm.service.AuthService; import jakarta.validation.Valid; import jakarta.validation.constraints.*; import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/customers/me")
public class ProfileController {
    private final AuthService auth; public ProfileController(AuthService auth){this.auth=auth;}
    @GetMapping public User me(@RequestHeader(value="Authorization",required=false) String authorization){return auth.requireCustomer(authorization);}
    @PutMapping public User update(@RequestHeader(value="Authorization",required=false) String authorization,@Valid @RequestBody ProfileRequest r){User u=auth.requireCustomer(authorization);u.setFullName(r.fullName().trim());u.setPhone(r.phone()==null?null:r.phone().trim());u.setPreferences(r.preferences());return auth.saveProfile(u);}
    public record ProfileRequest(@NotBlank String fullName,String phone,String preferences){}
}
