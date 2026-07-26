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

    @Override
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            return;
        }
        userRepository.findByEmail(managerEmail).ifPresentOrElse(
                user -> {
                    if (!user.getRoles().contains(Role.HOTEL_MANAGER)) {
                        user.getRoles().add(Role.HOTEL_MANAGER);
                        userRepository.save(user);
                        log.info("Ensured HOTEL_MANAGER role for {}", managerEmail);
                    }
                },
                () -> {
                    User manager = new User();
                    manager.setEmail(managerEmail);
                    manager.setName(managerName);
                    manager.setPassword(passwordEncoder.encode(managerPassword));
                    manager.setRoles(Set.of(Role.HOTEL_MANAGER, Role.GUEST));
                    userRepository.save(manager);
                    log.info("Seeded hotel manager user: {}", managerEmail);
                }
        );
    }
}
