package com.htttdn.crm.controller;
import com.htttdn.crm.service.*;
import com.htttdn.crm.repository.StoreVoucherRepository;
import com.htttdn.crm.entity.StoreVoucher;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
@RestController
public class VoucherController {
    private final VoucherService service; private final AuthService auth; private final StoreVoucherRepository vouchers;
    public VoucherController(VoucherService service,AuthService auth,StoreVoucherRepository vouchers){this.service=service;this.auth=auth;this.vouchers=vouchers;}
    @GetMapping("/api/vouchers/validate") public VoucherService.Preview validate(@RequestHeader(value="Authorization",required=false) String authorization,@RequestParam String code,@RequestParam BigDecimal amount){return service.preview(code,amount,authorization==null?null:auth.requireCustomer(authorization).getId());}
    @GetMapping("/api/customers/me/vouchers") public Page<WalletView> mine(@RequestHeader(value="Authorization",required=false) String authorization,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
        Long id=auth.requireCustomer(authorization).getId();
        if(page<0||size<1||size>100)throw VoucherService.bad("INVALID_PAGE","Phân trang không hợp lệ.");
        return vouchers.findWallet(id,Instant.now(),PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"id"))).map(VoucherController::view);
    }
    public static WalletView view(StoreVoucher v){
        boolean personal=v.getOwnerCustomerId()!=null;
        return new WalletView(v.getCode(),v.getDescription(),v.getDiscountType(),v.getDiscountValue(),v.getMinOrderAmount(),v.getMaxDiscountAmount(),v.getExpiresAt(),personal&&v.getUsedCount()>0?"USED":VoucherService.state(v,Instant.now()),personal?"SURVEY":"GENERAL");
    }
    public record WalletView(String code,String description,String discountType,BigDecimal discountValue,BigDecimal minOrderAmount,BigDecimal maxDiscountAmount,Instant expiresAt,String state,String source){}
}
