package com.example.minhaj;

import com.example.minhaj.support.IntegrationTestBase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Runs V1, inserts lessons the way a pre-V2 database holds them, then runs V2 and checks the backfill.
// Uses its own schema so the application's tables are left alone.
class LessonPositionMigrationTest extends IntegrationTestBase {

    private static final String SCHEMA = "v2_backfill_check";

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
    void existingLessonsAreNumberedPerCourseInIdOrder() {
        flywayUpTo("1").migrate();

        String course = "INSERT INTO " + SCHEMA + ".courses (id, title, is_free, price, lesson_count, review_count, "
                + "average_rating) VALUES (?, ?, true, 0, 0, 0, 0)";
        jdbc.update(course, 1, "A");
        jdbc.update(course, 2, "B");
        // ids interleaved across the two courses
        String lesson = "INSERT INTO " + SCHEMA + ".lessons (id, title, course_id) VALUES (?, ?, ?)";
        jdbc.update(lesson, 10, "A1", 1);
        jdbc.update(lesson, 11, "B1", 2);
        jdbc.update(lesson, 12, "A2", 1);
        jdbc.update(lesson, 13, "B2", 2);
        jdbc.update(lesson, 14, "A3", 1);

        flywayUpTo("2").migrate();

        List<String> rows = jdbc.queryForList("SELECT course_id || ':' || id || '@' || position FROM " + SCHEMA
                + ".lessons ORDER BY id", String.class);
        assertThat(rows).containsExactly("1:10@1", "2:11@1", "1:12@2", "2:13@2", "1:14@3");
    }
}
