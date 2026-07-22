package com.gamesaves.gamesaves.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class DirectMessageServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayAppliesDirectMessagingMigrationsAndRejectsNonNormalizedPairs() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version IN ('5', '6')
                  AND success = TRUE
                """, Integer.class);

        assertEquals(2, migrationCount);

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO direct_conversations (user_one_id, user_two_id, created_at, updated_at)
                VALUES (900001, 900001, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """));
    }
}
