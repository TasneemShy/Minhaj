package com.example.minhaj.integration;

import com.example.minhaj.seed.DemoDataSeeder;
import com.example.minhaj.support.IntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static com.example.minhaj.seed.DemoDataSeeder.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The demo profile on top of the test profile (its own Spring context). The seeder also runs at startup, but
// IntegrationTestBase empties the tables before each test, so every test seeds again by calling it directly.
@ActiveProfiles({"test", "demo"})
class DemoDataIntegrationTest extends IntegrationTestBase {

    private static final String DEMO_PASSWORD = "demo-password-123";
    private static final String DRAFT_TITLE = "تعلم الآلة: مقدمة عملية";
    private static final String COMPLETED_TITLE = "أساسيات البرمجة بلغة بايثون";

    @Autowired
    private DemoDataSeeder seeder;

    @BeforeEach
    void seed() {
        assertThat(seeder.seed()).isTrue();
    }

    private Long courseId(String title) {
        return jdbc.queryForObject("SELECT id FROM courses WHERE title = ?", Long.class, title);
    }

    @Test
    void seedsTheExpectedData() {
        assertThat(count("users")).isEqualTo(6);
        assertThat(count("categories")).isEqualTo(4);
        assertThat(count("courses")).isEqualTo(6);
        assertThat(count("lessons")).isEqualTo(22);
        assertThat(count("student_courses")).isEqualTo(7);
        assertThat(count("lesson_progress")).isEqualTo(7);
        assertThat(count("reviews")).isEqualTo(6);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE NOT email_verified", Long.class)).isZero();
        assertThat(jdbc.queryForList("SELECT role FROM users ORDER BY id", String.class))
                .containsExactly("ADMIN", "INSTRUCTOR", "INSTRUCTOR", "STUDENT", "STUDENT", "STUDENT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM courses WHERE status = 'DRAFT'", Long.class)).isEqualTo(1);
        // lessonCount matches the lessons table for every course
        assertThat(jdbc.queryForObject("SELECT count(*) FROM courses c WHERE c.lesson_count <> "
                + "(SELECT count(*) FROM lessons l WHERE l.course_id = c.id)", Long.class)).isZero();
    }

    @Test
    void ratingsAreRecalculatedFromTheReviews() {
        Long python = courseId(COMPLETED_TITLE);
        assertThat(jdbc.queryForObject("SELECT review_count FROM courses WHERE id = ?", Integer.class, python)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT average_rating FROM courses WHERE id = ?", Double.class, python)).isEqualTo(4.5);
    }

    @Test
    void seedingTwiceAddsNothing() {
        assertThat(seeder.seed()).isFalse();

        assertThat(count("users")).isEqualTo(6);
        assertThat(count("courses")).isEqualTo(6);
        assertThat(count("lessons")).isEqualTo(22);
        assertThat(count("reviews")).isEqualTo(6);
    }

    @Test
    void demoUsersCanLogInWithTheDemoPassword() throws Exception {
        for (String email : List.of(ADMIN_EMAIL, INSTRUCTOR_1, INSTRUCTOR_2, STUDENT_1, STUDENT_2, STUDENT_3)) {
            login(email, DEMO_PASSWORD);
        }
        String body = loginResponse(ADMIN_EMAIL, DEMO_PASSWORD);
        assertThat(JsonPath.<String>read(body, "$.role")).isEqualTo("ADMIN");
    }

    @Test
    void draftCourseIsHiddenFromThePublicCatalog() throws Exception {
        mockMvc.perform(get("/api/courses").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[*].title", not(hasItem(DRAFT_TITLE))));
        mockMvc.perform(get("/api/courses/" + courseId(DRAFT_TITLE)))
                .andExpect(status().isNotFound());
    }

    @Test
    void arabicSearchFindsTheArabicCourse() throws Exception {
        mockMvc.perform(get("/api/courses").param("search", "بايثون"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value(COMPLETED_TITLE));
    }

    @Test
    void completedStudentHasACompletionCertificate() throws Exception {
        String token = login(STUDENT_1, DEMO_PASSWORD);
        Long python = courseId(COMPLETED_TITLE);

        mockMvc.perform(auth(get("/api/courses/" + python + "/progress"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.percentage").value(100));
        mockMvc.perform(auth(get("/api/courses/" + python + "/completion"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lessonCount").value(4))
                .andExpect(jsonPath("$.instructorName").value("Sara Al-Hassan"));
        mockMvc.perform(auth(get("/api/my-courses/completed"), token))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void seedingSendsNoEmail() {
        // EmailService is async; wait a little so a stray email would have reached the mock.
        verify(mailSender, after(500).never()).send(any(SimpleMailMessage.class));
    }
}
