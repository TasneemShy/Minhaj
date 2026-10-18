package com.example.minhaj.integration;

import com.example.minhaj.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Requests bind to DTOs (no mass assignment of server-owned fields) and responses are DTOs (no entity leaks).
class RequestResponseDtoIntegrationTest extends IntegrationTestBase {

    // Fields a client must not be able to set on a course
    private static final String SERVER_FIELDS = "\"id\":4242,\"averageRating\":4.9,\"reviewCount\":100,\"lessonCount\":50,"
            + "\"createdAt\":\"2000-01-01T00:00:00\",\"instructor\":{\"id\":4242},\"instructorId\":4242";

    @Test
    void clientSuppliedServerFieldsAreIgnoredOnCourseCreate() throws Exception {
        String admin = adminToken();

        mockMvc.perform(withJson(post("/api/admin/courses"), admin,
                        "{\"title\":\"Sneaky\",\"price\":10," + SERVER_FIELDS + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.averageRating").value(0.0))
                .andExpect(jsonPath("$.reviewCount").value(0))
                .andExpect(jsonPath("$.lessonCount").value(0))
                .andExpect(jsonPath("$.instructorId").doesNotExist());

        mockMvc.perform(get("/api/courses/1"))
                .andExpect(jsonPath("$.title").value("Sneaky"))
                .andExpect(jsonPath("$.averageRating").value(0.0))
                .andExpect(jsonPath("$.reviewCount").value(0))
                .andExpect(jsonPath("$.lessonCount").value(0));
        mockMvc.perform(get("/api/courses/4242")).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT created_at > '2001-01-01' FROM courses WHERE id = 1", Boolean.class)).isTrue();
    }

    @Test
    void clientSuppliedServerFieldsAreIgnoredOnCourseUpdate() throws Exception {
        String admin = adminToken();
        String email = uniqueEmail("owner");
        String instructor = instructorToken(email);
        Long id = postForId("/api/instructor/courses", instructor, courseJson("Mine", false, 10, null));
        Long other = createCourse(admin, "Someone else's", false, 5, null);
        postForId("/api/instructor/courses/" + id + "/lessons", instructor, json("title", "L1"));

        mockMvc.perform(withJson(put("/api/instructor/courses/" + id), instructor,
                        "{\"title\":\"Mine v2\",\"price\":12," + SERVER_FIELDS.replace("4242", String.valueOf(other)) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.lessonCount").value(1))
                .andExpect(jsonPath("$.reviewCount").value(0))
                .andExpect(jsonPath("$.averageRating").value(0.0))
                .andExpect(jsonPath("$.instructorId").value(userId(email)));

        mockMvc.perform(get("/api/courses/" + other)).andExpect(jsonPath("$.title").value("Someone else's"));
    }

    @Test
    void clientSuppliedIdsAreIgnoredOnLessonAndCategoryCreate() throws Exception {
        String admin = adminToken();
        Long courseId = createCourse(admin, "Course", false, 1, null);
        Long otherCourse = createCourse(admin, "Other", false, 1, null);

        mockMvc.perform(withJson(post("/api/admin/courses/" + courseId + "/lessons"), admin,
                        "{\"id\":99,\"title\":\"Intro\",\"course\":{\"id\":" + otherCourse + "}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.courseId").value(courseId));

        mockMvc.perform(withJson(post("/api/admin/categories"), admin, "{\"id\":99,\"name\":\"Design\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Design"));
    }

    @Test
    void responsesNeverContainPasswordsOrOtherUsersEmails() throws Exception {
        String admin = adminToken();
        String instructorEmail = uniqueEmail("teacher");
        String instructor = instructorToken(instructorEmail);
        String reviewerEmail = uniqueEmail("reviewer");
        String reviewer = studentToken(reviewerEmail);
        String viewer = studentToken();
        // Helper-made names embed the email; rename so any appearance of the email in a body is a real leak
        rename(instructorEmail, "Ines Instructor");
        rename(reviewerEmail, "Rami Reviewer");

        Long categoryId = createCategory(admin, "Programming");
        Long courseId = postForId("/api/instructor/courses", instructor, courseJson("Taught", false, 10, categoryId));
        postForId("/api/instructor/courses/" + courseId + "/lessons", instructor, json("title", "L1"));
        enroll(reviewer, courseId);
        Long reviewId = review(reviewer, courseId, 4, "nice");

        assertNoLeaks(get("/api/courses"), viewer, instructorEmail, reviewerEmail);
        assertNoLeaks(get("/api/courses/" + courseId), viewer, instructorEmail, reviewerEmail);
        assertNoLeaks(get("/api/courses/" + courseId + "/lessons"), viewer, instructorEmail, reviewerEmail);
        assertNoLeaks(get("/api/courses/" + courseId + "/reviews"), viewer, instructorEmail, reviewerEmail);
        assertNoLeaks(get("/api/users/" + userId(reviewerEmail) + "/reviews"), viewer, instructorEmail, reviewerEmail);
        assertNoLeaks(get("/api/categories"), viewer, instructorEmail, reviewerEmail);
        assertNoLeaks(get("/api/my-courses"), reviewer, instructorEmail, reviewerEmail);
        assertNoLeaks(get("/api/instructor/courses"), instructor, instructorEmail, reviewerEmail);
        assertNoLeaks(withJson(put("/api/courses/" + courseId + "/reviews/" + reviewId), null,
                "{\"rating\":5,\"comment\":\"better\"}"), reviewer, instructorEmail, reviewerEmail);

        // Reviews name their author by id and name only
        mockMvc.perform(get("/api/courses/" + courseId + "/reviews"))
                .andExpect(jsonPath("$.content[0].authorId").value(userId(reviewerEmail)))
                .andExpect(jsonPath("$.content[0].authorName").value("Rami Reviewer"))
                .andExpect(jsonPath("$.content[0].rating").value(5))
                .andExpect(jsonPath("$.content[0].createdAt").exists())
                .andExpect(jsonPath("$.content[0].user").doesNotExist());
    }

    private void assertNoLeaks(MockHttpServletRequestBuilder request, String token, String... emails) throws Exception {
        String body = mockMvc.perform(auth(request, token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContainIgnoringCase("password");
        assertThat(body).doesNotContain("\"email\"");
        for (String email : emails) {
            assertThat(body).doesNotContain(email);
        }
    }

    private void rename(String email, String name) {
        var user = userRepository.findByEmail(email).orElseThrow();
        user.setName(name);
        userRepository.save(user);
    }
}
