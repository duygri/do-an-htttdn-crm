package com.htttdn.crm.service;

import com.htttdn.crm.entity.AppSetting;
import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.repository.AppSettingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;

/** Key-value system configuration with validated defaults. */
@Service
public class SettingsService {
    public static final Map<String,String> DEFAULTS = Map.of(
        "invoice.seller.name", "Anh Lon Shop",
        "invoice.seller.address", "",
        "invoice.seller.phone", "",
        "invoice.seller.taxCode", "",
        "inventory.lowStockDefault", "5",
        "loyalty.pointsPer10000", "1",
        "loyalty.redeemPoints", "100",
        "loyalty.redeemValue", "20000");

    private final AppSettingRepository repo;
    private final AuditTrail audit;

    public SettingsService(AppSettingRepository repo, AuditTrail audit) {
        this.repo = repo;
        this.audit = audit;
    }

    public String get(String key) {
        return repo.findById(key).map(AppSetting::getValue).orElseGet(() -> DEFAULTS.get(key));
    }

    public int getInt(String key) {
        return Integer.parseInt(get(key));
    }

    public BigDecimal getDecimal(String key) {
        return new BigDecimal(get(key));
    }

    public Map<String,String> all() {
        var values = new TreeMap<String,String>(DEFAULTS);
        repo.findAll().forEach(setting -> values.put(setting.getKey(), setting.getValue()));
        return values;
    }

    @Transactional
    public Map<String,String> update(Map<String,String> values, AuditTrail.Actor actor) {
        if (actor == null) throw new IllegalStateException("Verified audit actor required");
        if (values == null || values.isEmpty()) throw bad();
        for (var entry : values.entrySet()) validate(entry.getKey(), entry.getValue());
        for (var entry : values.entrySet()) {
            String before = repo.findById(entry.getKey()).map(AppSetting::getValue).orElse(DEFAULTS.get(entry.getKey()));
            if (before.equals(entry.getValue())) continue;
            repo.save(new AppSetting(entry.getKey(), entry.getValue(), actor.id()));
            audit.recordAs(actor, "SETTINGS_UPDATE", "SETTING", 0L,
                Map.of("key", entry.getKey(), "value", before),
                Map.of("key", entry.getKey(), "value", entry.getValue()));
        }
        return all();
    }

    private static void validate(String key, String value) {
        if (!DEFAULTS.containsKey(key) || value == null) throw bad();
        switch (key) {
            case "inventory.lowStockDefault" -> intRange(value, 0, 100000);
            case "loyalty.pointsPer10000" -> intRange(value, 0, 1000);
            case "loyalty.redeemPoints" -> intRange(value, 1, 1000000);
            case "loyalty.redeemValue" -> decimalRange(value, BigDecimal.ZERO, new BigDecimal("100000000"));
            case "invoice.seller.name" -> length(value, 150);
            case "invoice.seller.address" -> length(value, 1000);
            default -> length(value, 40);
        }
    }

    private static void intRange(String value, int min, int max) {
        try {
            int number = Integer.parseInt(value);
            if (number < min || number > max) throw bad();
        } catch (NumberFormatException e) {
            throw bad();
        }
    }

    private static void decimalRange(String value, BigDecimal min, BigDecimal max) {
        try {
            var number = new BigDecimal(value);
            if (number.compareTo(min) < 0 || number.compareTo(max) > 0) throw bad();
        } catch (NumberFormatException e) {
            throw bad();
        }
    }

    private static void length(String value, int max) {
        if (value.length() > max) throw bad();
    }

    private static ApiException bad() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SETTING", "Cấu hình không hợp lệ.");
    }
}
