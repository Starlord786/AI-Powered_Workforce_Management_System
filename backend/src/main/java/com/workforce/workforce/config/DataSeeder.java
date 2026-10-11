package com.workforce.workforce.config;

import com.workforce.workforce.entity.AppUser;
import com.workforce.workforce.entity.Role;
import com.workforce.workforce.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// DEV ONLY: creates test users the first time the app starts.
@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public DataSeeder(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        if (users.count() > 0) {
            return;
        }
        create("admin@workforce.com", "admin123", Role.ADMIN);
        create("hr@workforce.com", "hr123", Role.HR);
        create("manager@workforce.com", "manager123", Role.MANAGER);
        create("employee@workforce.com", "employee123", Role.EMPLOYEE);
    }

    private void create(String email, String password, Role role) {
        AppUser u = new AppUser();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(password));
        u.setRole(role);
        users.save(u);
    }
}
