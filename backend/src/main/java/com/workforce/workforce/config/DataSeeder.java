package com.workforce.workforce.config;

import com.workforce.workforce.entity.AppUser;
import com.workforce.workforce.entity.Role;
import com.workforce.workforce.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

// Creates demo users on the first start of an EMPTY database - only when app.seed.enabled=true
// (env: SEED_DEMO_USERS=true). Passwords come from config; if left blank a random one is
// generated and printed once to the console. No passwords are hardcoded.
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.seed.admin-password:}")
    private String adminPassword;
    @Value("${app.seed.hr-password:}")
    private String hrPassword;
    @Value("${app.seed.manager-password:}")
    private String managerPassword;
    @Value("${app.seed.employee-password:}")
    private String employeePassword;

    public DataSeeder(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        if (users.count() > 0) {
            return;
        }
        create("admin@workforce.com", adminPassword, Role.ADMIN);
        create("hr@workforce.com", hrPassword, Role.HR);
        create("manager@workforce.com", managerPassword, Role.MANAGER);
        create("employee@workforce.com", employeePassword, Role.EMPLOYEE);
    }

    private void create(String email, String password, Role role) {
        boolean generated = password == null || password.isBlank();
        String actual = generated ? randomPassword() : password;
        AppUser u = new AppUser();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(actual));
        u.setRole(role);
        users.save(u);
        if (generated) {
            log.warn("Seeded {} ({}) with generated password: {}  (shown once - change it)", email, role, actual);
        } else {
            log.info("Seeded {} ({}) with the password from config", email, role);
        }
    }

    private String randomPassword() {
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
