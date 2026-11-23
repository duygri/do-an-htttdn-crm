package com.htttdn.crm.service;

import com.htttdn.crm.entity.ShippingCarrier;
import com.htttdn.crm.repository.ShippingCarrierRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class ShippingCarrierSeeder implements ApplicationRunner {
    private final ShippingCarrierRepository carriers;
    public ShippingCarrierSeeder(ShippingCarrierRepository carriers){this.carriers=carriers;}
    @Override public void run(ApplicationArguments args){
        if(carriers.count()>0)return;
        carriers.saveAll(List.of(
            new ShippingCarrier("GHN","Giao Hàng Nhanh","https://track.ghn.vn/order/{code}",10),
            new ShippingCarrier("GHTK","Giao Hàng Tiết Kiệm","https://i.ghtk.vn/{code}",20),
            new ShippingCarrier("VIETTEL_POST","Viettel Post","https://viettelpost.com.vn/tra-cuu-hanh-trinh-don/?code={code}",30),
            new ShippingCarrier("VNPOST","VNPost","https://www.vnpost.vn/vi-vn/dinh-vi/buu-pham?key={code}",40),
            new ShippingCarrier("JT","J&T Express","https://jtexpress.vn/track?billcode={code}",50),
            new ShippingCarrier("SPX","Shopee Express","https://spx.vn/track?order_sn={code}",60),
            new ShippingCarrier("OTHER","Hãng khác",null,99)));
    }
}
