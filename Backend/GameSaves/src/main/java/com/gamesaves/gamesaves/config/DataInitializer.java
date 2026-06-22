package com.gamesaves.gamesaves.config;

import com.gamesaves.gamesaves.entity.User;
import com.gamesaves.gamesaves.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Ensures test users exist with properly BCrypt-hashed passwords.
 * Only runs in non-production profiles.
 *
 * Resets test user passwords to "password123" on every startup,
 * so the test data is always in a known-good state regardless of
 * what the SQL init script contains.
 */
@Component
@Profile("!prod")
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final String TEST_PASSWORD = "password123";

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Override
    public void run(String... args) {
        log.info("DataInitializer: ensuring test users exist with correct passwords...");
        ensureTestUser("admin", "系统管理员", "13800000001",
                "admin@gamesaving.com", "admin",
                "平台超级管理员，拥有所有权限");
        ensureTestUser("player_one", "玩家一号", "13800000002",
                "player1@example.com", "user",
                "硬核游戏玩家，专注魂系游戏");
        ensureTestUser("speedrunner", "速通达人", "13800000003",
                "speed@example.com", "user",
                "游戏速通爱好者，追求极限操作");
        log.info("DataInitializer: test users ready.");
    }

    private void ensureTestUser(String username, String nickname, String phone,
                                 String email, String role, String bio) {
        User user = userRepository.findByUsername(username).orElse(null);

        if (user == null) {
            // Create new test user
            user = User.builder()
                    .username(username)
                    .password(passwordEncoder.encode(TEST_PASSWORD))
                    .nickname(nickname)
                    .phone(phone)
                    .email(email)
                    .role(role)
                    .bio(bio)
                    .isActive(true)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            userRepository.save(user);
            log.info("  Created test user: {} (password={})", username, TEST_PASSWORD);
        } else {
            // Reset password to known-good hash
            String encodedPassword = passwordEncoder.encode(TEST_PASSWORD);
            if (!passwordEncoder.matches(TEST_PASSWORD, user.getPassword())) {
                user.setPassword(encodedPassword);
                userRepository.save(user);
                log.info("  Reset password for: {} → {}", username, TEST_PASSWORD);
            } else {
                log.info("  User '{}' already has correct password, skipping.", username);
            }
        }
    }
}
