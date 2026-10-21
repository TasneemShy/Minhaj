package com.example.minhaj;

import com.example.minhaj.support.IntegrationTestBase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Runs every migration on a fresh, empty schema and checks V6's constraints and cascades directly in SQL.
// Uses its own schema so the application's tables are left alone.
class QuizMigrationTest extends IntegrationTestBase {

    private static final String SCHEMA = "v6_quiz_check";

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void migrateFreshSchema() {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .createSchemas(true)
                .load()
                .migrate();

        jdbc.update("INSERT INTO " + SCHEMA + ".users (id, name, email, password, role, email_verified) "
                + "VALUES (1, 'Student', 's@test.com', 'x', 'STUDENT', TRUE)");
        jdbc.update("INSERT INTO " + SCHEMA + ".courses (id, title, is_free, price, average_rating, review_count, "
                + "lesson_count, status) VALUES (1, 'Course', TRUE, 0, 0, 0, 1, 'PUBLISHED')");
        jdbc.update("INSERT INTO " + SCHEMA + ".lessons (id, title, course_id, position) VALUES (1, 'Lesson', 1, 1)");
    }

    @AfterEach
    void dropSchema() {
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    private void seedQuizWithAttempt() {
        jdbc.update("INSERT INTO " + SCHEMA + ".quizzes (id, lesson_id, title, created_at) VALUES (1, 1, 'Quiz', now())");
        jdbc.update("INSERT INTO " + SCHEMA + ".quiz_questions (id, quiz_id, position, text, type) "
                + "VALUES (1, 1, 1, 'Q', 'SINGLE_CHOICE')");
        jdbc.update("INSERT INTO " + SCHEMA + ".quiz_options (id, question_id, position, text, correct) "
                + "VALUES (1, 1, 1, 'A', TRUE), (2, 1, 2, 'B', FALSE)");
        jdbc.update("INSERT INTO " + SCHEMA + ".quiz_attempts (id, quiz_id, user_id, score_percentage, passed, submitted_at) "
                + "VALUES (1, 1, 1, 100, TRUE, now())");
        jdbc.update("INSERT INTO " + SCHEMA + ".quiz_attempt_answers (attempt_id, question_id, option_id) VALUES (1, 1, 1)");
    }

    private long schemaCount(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + SCHEMA + "." + table, Long.class);
    }

    private void assertQuizTablesEmpty() {
        for (String table : new String[]{"quizzes", "quiz_questions", "quiz_options", "quiz_attempts", "quiz_attempt_answers"}) {
            assertThat(schemaCount(table)).as(table).isZero();
        }
    }

    @Test
    void quizzesDefaultToSeventyPercentAndAreUniquePerLesson() {
        seedQuizWithAttempt();

        assertThat(jdbc.queryForObject("SELECT pass_percentage FROM " + SCHEMA + ".quizzes WHERE id = 1", Integer.class))
                .isEqualTo(70);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO " + SCHEMA + ".quizzes (lesson_id, title, created_at) "
                + "VALUES (1, 'Second', now())"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void checkConstraintsRejectBadValues() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO " + SCHEMA + ".quizzes (lesson_id, title, pass_percentage, created_at) "
                + "VALUES (1, 'Quiz', 101, now())"))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("INSERT INTO " + SCHEMA + ".quizzes (id, lesson_id, title, created_at) VALUES (1, 1, 'Quiz', now())");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO " + SCHEMA + ".quiz_questions (quiz_id, position, text, type) "
                + "VALUES (1, 1, 'Q', 'TRUE_FALSE')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingALessonCascadesToAllQuizData() {
        seedQuizWithAttempt();

        jdbc.update("DELETE FROM " + SCHEMA + ".lessons WHERE id = 1");

        assertQuizTablesEmpty();
    }

    @Test
    void deletingAUserRemovesTheirAttempts() {
        seedQuizWithAttempt();

        jdbc.update("DELETE FROM " + SCHEMA + ".users WHERE id = 1");

        assertThat(schemaCount("quiz_attempts")).isZero();
        assertThat(schemaCount("quiz_attempt_answers")).isZero();
        assertThat(schemaCount("quizzes")).isEqualTo(1);
    }
}
