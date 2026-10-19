package com.htttdn.crm.config;

import com.htttdn.crm.entity.User;
import com.htttdn.crm.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Explicit opt-in for a new demo database; never resets existing credentials. */
@Component
@ConditionalOnProperty(name="deployment.bootstrap-admin.enabled", havingValue="true")
public class DemoAdminBootstrap implements ApplicationRunner {
    private final UserRepository users;
    private final String email;
    private final String password;
    public DemoAdminBootstrap(UserRepository users,
        @Value("${BOOTSTRAP_ADMIN_EMAIL:}") String email,
        @Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String password) {
        this.users = users; this.email = email.trim().toLowerCase(Locale.ROOT); this.password = password;
    }
    @Override @Transactional
    public void run(ApplicationArguments args) {
        if (users.countByRole("ADMIN") > 0) return;
        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || email.length() > 255
            || password.length() < 16 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalStateException("Bootstrap requires a valid admin email and a private password of at least 16 characters, at most 72 UTF-8 bytes.");
        if (users.existsByEmailIgnoreCase(email))
            throw new IllegalStateException("Bootstrap email already exists; no account was changed.");
        User admin = new User();
        admin.setEmail(email); admin.setFullName("Quản trị demo"); admin.setRole("ADMIN");
        admin.setPasswordHash(new BCryptPasswordEncoder().encode(password));
        users.save(admin);
    }
}
