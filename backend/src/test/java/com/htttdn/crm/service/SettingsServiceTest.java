package com.htttdn.crm.service;

import com.htttdn.crm.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:settings;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop","crm.care-reminders.enabled=false"})
@Transactional
class SettingsServiceTest {
    @Autowired SettingsService settings;

    @Test void defaultsAndOverrides(){
        assertEquals("Anh Lon Shop",settings.get("invoice.seller.name"));
        assertEquals(5,settings.getInt("inventory.lowStockDefault"));
        assertEquals(5,settings.all().size());
        var actor=new AuditTrail.Actor(1L,"Admin","ADMIN");
        settings.update(Map.of("inventory.lowStockDefault","8"),actor);
        assertEquals(8,settings.getInt("inventory.lowStockDefault"));
        assertFalse(settings.all().containsKey("loyalty.redeemPoints"));
        assertEquals("Anh Lon Shop",settings.all().get("invoice.seller.name"));
    }

    @Test void rejectsUnknownKeysAndBadValues(){
        var actor=new AuditTrail.Actor(1L,"Admin","ADMIN");
        var unknown=assertThrows(ApiException.class,()->settings.update(Map.of("unknown.key","1"),actor));
        assertEquals("INVALID_SETTING",unknown.code());
        assertThrows(ApiException.class,()->settings.update(Map.of("inventory.lowStockDefault","-1"),actor));
        assertThrows(ApiException.class,()->settings.update(Map.of("loyalty.redeemValue","abc"),actor));
        assertThrows(ApiException.class,()->settings.update(Map.of("loyalty.redeemPoints","0"),actor));
        assertThrows(IllegalStateException.class,()->settings.update(Map.of("loyalty.redeemPoints","150"),null));
    }
}
