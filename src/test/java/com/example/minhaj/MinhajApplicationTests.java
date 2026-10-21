package com.example.minhaj;

import com.example.minhaj.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Starting the context runs Flyway's migrations on an empty Postgres and lets Hibernate validate the entities against them.
class MinhajApplicationTests extends IntegrationTestBase {

    @Test
    void contextLoadsAndFlywayAppliedBaseline() {
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success", Integer.class);
        assertThat(applied).isEqualTo(1);
    }

    @Test
    void flywayAppliedLessonPositionWithDeferredUniqueConstraint() {
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '2' AND success", Integer.class);
        assertThat(applied).isEqualTo(1);

        String nullable = jdbc.queryForObject("SELECT is_nullable FROM information_schema.columns "
                + "WHERE table_schema = 'public' AND table_name = 'lessons' AND column_name = 'position'", String.class);
        assertThat(nullable).isEqualTo("NO");

        Boolean deferred = jdbc.queryForObject("SELECT condeferrable AND condeferred FROM pg_constraint "
                + "WHERE conname = 'lessons_course_id_position_key'", Boolean.class);
        assertThat(deferred).isTrue();
    }
}
