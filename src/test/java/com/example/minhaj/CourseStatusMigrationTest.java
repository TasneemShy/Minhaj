package com.example.minhaj;

import com.example.minhaj.support.IntegrationTestBase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Runs V1-V2, inserts courses the way a pre-V3 database holds them, then runs V3 and checks the backfill.
// Uses its own schema so the application's tables are left alone.
class CourseStatusMigrationTest extends IntegrationTestBase {

    private static final String SCHEMA = "v3_backfill_check";

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
    void existingCoursesBecomePublishedAtTheirCreationTime() {
        flywayUpTo("2").migrate();

        String course = "INSERT INTO " + SCHEMA + ".courses (id, title, is_free, price, lesson_count, review_count, "
                + "average_rating, created_at) VALUES (?, ?, true, 0, 0, 0, 0, ?::timestamp)";
        jdbc.update(course, 1, "Old", "2025-01-02 03:04:05");
        jdbc.update(course, 2, "No timestamp", null);

        flywayUpTo("3").migrate();

        List<String> rows = jdbc.queryForList("SELECT id || ':' || status || '@' || coalesce(published_at::text, 'null') FROM "
                + SCHEMA + ".courses ORDER BY id", String.class);
        assertThat(rows).containsExactly("1:PUBLISHED@2025-01-02 03:04:05", "2:PUBLISHED@null");

        // status is required and limited to the two lifecycle values
        assertThatThrownBy(() -> jdbc.update("UPDATE " + SCHEMA + ".courses SET status = 'ARCHIVED' WHERE id = 1"))
                .hasMessageContaining("courses_status_check");
        assertThatThrownBy(() -> jdbc.update("UPDATE " + SCHEMA + ".courses SET status = NULL WHERE id = 1"))
                .hasMessageContaining("status");
    }
}
