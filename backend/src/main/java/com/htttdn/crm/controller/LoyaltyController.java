package com.htttdn.crm.controller;

import com.htttdn.crm.entity.User;
import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.repository.UserRepository;
import com.htttdn.crm.service.AuditTrail;
import com.htttdn.crm.service.AuthService;
import com.htttdn.crm.service.LoyaltyService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class LoyaltyController {
    private final LoyaltyService loyalty;
    private final AuthService auth;
    private final UserRepository users;

    public LoyaltyController(LoyaltyService loyalty, AuthService auth, UserRepository users) {
        this.loyalty = loyalty;
        this.auth = auth;
        this.users = users;
    }

    public record AdjustRequest(Integer points, String note) {}

    @GetMapping("/api/account/points")
    public LoyaltyService.Summary mine(@RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.requireCustomer(authorization); throw LoyaltyService.retired();
    }

    @PostMapping("/api/account/points/redeem")
    @ResponseStatus(HttpStatus.CREATED)
    public LoyaltyService.RedeemResult redeem(@RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String requestKey) {
        return loyalty.redeem(auth.requireCustomer(authorization), requestKey == null ? java.util.UUID.randomUUID().toString() : requestKey);
    }

    @GetMapping("/api/manager/customers/{id}/points")
    public LoyaltyService.Summary customerPoints(@PathVariable long id) {
        customer(id); throw LoyaltyService.retired();
    }

    @PostMapping("/api/manager/customers/{id}/points")
    public LoyaltyService.Summary adjust(@PathVariable long id, @RequestBody AdjustRequest request,
            @RequestAttribute(AuditTrail.ACTOR_ATTRIBUTE) AuditTrail.Actor actor) {
        if (request == null || request.points() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_POINTS", "Thông tin điều chỉnh điểm không hợp lệ.");
        }
        return loyalty.adjust(customer(id), request.points(), request.note(), actor);
    }

    private User customer(long id) {
        return users.findById(id)
            .filter(user -> "CUSTOMER".equals(user.getRole()) && user.getDeletedAt() == null)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", "Không tìm thấy khách hàng."));
    }
}
