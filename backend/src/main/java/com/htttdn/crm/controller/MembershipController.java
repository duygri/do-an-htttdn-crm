package com.htttdn.crm.controller;
import com.htttdn.crm.service.*;
import org.springframework.web.bind.annotation.*;
@RestController
public class MembershipController {
 private final AuthService auth; private final MembershipService membership;
 public MembershipController(AuthService auth,MembershipService membership){this.auth=auth;this.membership=membership;}
 @GetMapping("/api/account/membership") public MembershipService.Membership mine(@RequestHeader(value="Authorization",required=false) String authorization){return membership.forCustomer(auth.requireCustomer(authorization).getId());}
}
