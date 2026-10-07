package com.ledgersystem.demo.config;
import com.ledgersystem.demo.entity.Account;
import com.ledgersystem.demo.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import java.math.BigDecimal;
import java.util.UUID;
@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {
    private final AccountRepository accountRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    public static final UUID ALICE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID BOB_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    @Override
    public void run(String... args) {
        if (accountRepository.count() == 0) {
            log.info("Database is empty. Seeding test accounts...");
            jdbcTemplate.update("INSERT INTO accounts (id, user_id, balance, currency, created_at, updated_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", ALICE_ID, "alice_test_user", new BigDecimal("1000.0000"), "USD");
            jdbcTemplate.update("INSERT INTO accounts (id, user_id, balance, currency, created_at, updated_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", BOB_ID, "bob_test_user", new BigDecimal("0.0000"), "USD");
            log.info("Seeded Alice ($1000) with ID: {}", ALICE_ID);
            log.info("Seeded Bob ($0) with ID: {}", BOB_ID);
        } else {
            log.info("Database already contains data. Skipping seeder.");
        }
    }
}
