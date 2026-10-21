package com.example.minhaj;

import com.example.minhaj.support.IntegrationTestBase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Runs the migrations before V4, inserts users the way an existing database holds them, then runs V4
// and checks they are all treated as verified. Uses its own schema so the application's tables are left alone.
class AuthHardeningMigrationTest extends IntegrationTestBase {

    private static final String SCHEMA = "v4_backfill_check";

    @Autowired
    private DataSource dataSource;

    private Flyway flywayUpTo(String version) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .createSchemas(true)
                .target(version)
                .load();
    }

    @AfterEach
    void dropSchema() {
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void existingUsersAreBackfilledAsVerifiedAndTheColumnBecomesNotNull() {
        flywayUpTo("2").migrate();

        String user = "INSERT INTO " + SCHEMA + ".users (id, name, email, password, role) VALUES (?, ?, ?, 'x', ?)";
        jdbc.update(user, 1, "Old Student", "old-student@test.com", "STUDENT");
        jdbc.update(user, 2, "Old Admin", "old-admin@test.com", "ADMIN");

        flywayUpTo("4").migrate();

        assertThat(jdbc.queryForList("SELECT email_verified FROM " + SCHEMA + ".users ORDER BY id", Boolean.class))
                .containsExactly(true, true);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO " + SCHEMA + ".users (name, email, password, role) "
                + "VALUES ('New', 'new@test.com', 'x', 'STUDENT')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
