package com.example.minhaj;

import com.example.minhaj.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Starting the context runs Flyway's V1 on an empty Postgres and lets Hibernate validate the entities against it.
class MinhajApplicationTests extends IntegrationTestBase {

    @Test
    void contextLoadsAndFlywayAppliedBaseline() {
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success", Integer.class);
        assertThat(applied).isEqualTo(1);
    }
}
