package com.htttdn.crm.controller;

import com.htttdn.crm.entity.StoreVoucher; import com.htttdn.crm.exception.ApiException; import com.htttdn.crm.repository.StoreVoucherRepository; import org.springframework.http.*; import org.springframework.web.bind.annotation.*; import java.math.*; import java.time.*; import java.util.*;

@RestController @RequestMapping("/api/vouchers")
public class VoucherController {
    private final StoreVoucherRepository vouchers; public VoucherController(StoreVoucherRepository vouchers){this.vouchers=vouchers;}
    @GetMapping("/validate") public VoucherView validate(@RequestParam String code,@RequestParam BigDecimal amount){StoreVoucher voucher=find(code);check(voucher,amount);return view(voucher,amount);}
    public static void check(StoreVoucher v,BigDecimal amount){Instant now=Instant.now();if(!v.isActive()||(v.getStartsAt()!=null&&now.isBefore(v.getStartsAt()))||(v.getExpiresAt()!=null&&!now.isBefore(v.getExpiresAt()))||(v.getUsageLimit()!=null&&v.getUsedCount()>=v.getUsageLimit()))throw new ApiException(HttpStatus.BAD_REQUEST,"VOUCHER_INVALID","Mã giảm giá không còn hiệu lực.");if(amount.compareTo(v.getMinOrderAmount())<0)throw new ApiException(HttpStatus.BAD_REQUEST,"VOUCHER_MINIMUM_NOT_MET","Đơn hàng chưa đạt giá trị tối thiểu để dùng mã này.");}
    public static BigDecimal discount(StoreVoucher v,BigDecimal amount){return "FIXED_AMOUNT".equalsIgnoreCase(v.getDiscountType())?v.getDiscountValue().min(amount):amount.multiply(v.getDiscountValue()).divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP).min(amount);}
    private StoreVoucher find(String code){return vouchers.findByCodeIgnoreCase(code==null?"":code.trim()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"VOUCHER_NOT_FOUND","Không tìm thấy mã giảm giá."));}
    private VoucherView view(StoreVoucher v,BigDecimal amount){return new VoucherView(v.getCode(),v.getDiscountType(),v.getDiscountValue(),discount(v,amount),v.getMinOrderAmount(),v.getExpiresAt());}
    public record VoucherView(String code,String discountType,BigDecimal discountValue,BigDecimal discountAmount,BigDecimal minOrderAmount,Instant expiresAt){}
}
