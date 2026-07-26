package com.sameerahmed.projects.airBnbApp.config;

import com.sameerahmed.projects.airBnbApp.entity.User;
import com.sameerahmed.projects.airBnbApp.entity.enums.Role;
import com.sameerahmed.projects.airBnbApp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataLoader implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.enabled:false}")
    private boolean seedEnabled;

    @Value("${app.seed.manager.email:manager@example.com}")
    private String managerEmail;

    @Value("${app.seed.manager.password:Manager@123}")
    private String managerPassword;

    @Value("${app.seed.manager.name:Hotel Manager}")
    private String managerName;

    @Value("${app.seed.admin.email:admin@example.com}")
    private String adminEmail;

    @Value("${app.seed.admin.password:Admin@123}")
    private String adminPassword;

    @Value("${app.seed.admin.name:Platform Admin}")
    private String adminName;

    @Override
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            return;
        }
        seedUser(managerEmail, managerPassword, managerName, Set.of(Role.HOTEL_MANAGER, Role.GUEST));
        seedUser(adminEmail, adminPassword, adminName, Set.of(Role.ADMIN, Role.GUEST));
    }

    private void seedUser(String email, String password, String name, Set<Role> roles) {
        userRepository.findByEmail(email).ifPresentOrElse(
                user -> {
                    if (user.getRoles() == null) {
                        user.setRoles(new HashSet<>());
                    }
                    boolean changed = user.getRoles().addAll(roles);
                    if (changed) {
                        userRepository.save(user);
                        log.info("Updated roles for {}", email);
                    }
                },
                () -> {
                    User user = new User();
                    user.setEmail(email);
                    user.setName(name);
                    user.setPassword(passwordEncoder.encode(password));
                    user.setRoles(new HashSet<>(roles));
                    userRepository.save(user);
                    log.info("Seeded user: {}", email);
                }
        );
    }
}
