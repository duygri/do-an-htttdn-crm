package com.htttdn.crm.controller;

import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class WebDeploymentController {
    @GetMapping("/healthz")
    @ResponseBody
    public Map<String, String> health() { return Map.of("status", "ok"); }

    // Only browser pages are forwarded. API and missing assets retain HTTP errors.
    @GetMapping({"/", "/admin", "/admin/{*path}", "/manager", "/manager/{*path}",
        "/tai-khoan", "/tai-khoan/{*path}", "/don-hang", "/don-hang/{*path}",
        "/san-pham", "/san-pham/{*path}", "/khao-sat", "/khao-sat/{*path}", "/yeu-thich",
        "/thong-bao", "/gio-hang", "/dat-hang", "/thanh-toan", "/dang-nhap",
        "/dang-ky", "/quen-mat-khau", "/dat-lai-mat-khau", "/ho-tro", "/ho-tro/{*path}"})
    public String page() { return "forward:/index.html"; }
}
