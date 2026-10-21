package com.example.minhaj.integration;

import com.example.minhaj.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileIntegrationTest extends IntegrationTestBase {

    @Test
    void getProfileDoesNotExposePassword() throws Exception {
        String email = uniqueEmail("me");
        String token = studentToken(email);

        mockMvc.perform(auth(get("/api/profile"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId(email)))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void updateChangesOnlyTheName() throws Exception {
        String email = uniqueEmail("me");
        String token = studentToken(email);

        mockMvc.perform(withJson(put("/api/profile"), token,
                        "{\"name\":\"  New Name  \",\"email\":\"hacker@test.com\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("STUDENT"));

        mockMvc.perform(withJson(put("/api/profile"), token, json("name", " ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("Name cannot be empty"));
    }

    @Test
    void changePasswordRequiresTheCurrentOne() throws Exception {
        String email = uniqueEmail("pw");
        String token = studentToken(email);

        mockMvc.perform(withJson(put("/api/profile/password"), token, json("currentPassword", "wrong-one", "newPassword", "newpass123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Current password is incorrect."));
        login(email, PASSWORD);

        mockMvc.perform(withJson(put("/api/profile/password"), token, json("currentPassword", PASSWORD, "newPassword", "123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").value("Password must be at least 6 characters long"));

        mockMvc.perform(withJson(put("/api/profile/password"), token, json("currentPassword", PASSWORD, "newPassword", "newpass123")))
                .andExpect(status().isOk());
        login(email, "newpass123");
        mockMvc.perform(withJson(post("/api/auth/login"), null, json("email", email, "password", PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unenrollRemovesEnrolmentAndThatCoursesProgressOnly() throws Exception {
        String admin = adminToken();
        Long leaving = createPublishedCourse(admin, "Leaving", false, 1, null);
        Long staying = createPublishedCourse(admin, "Staying", false, 1, null);
        Long leavingLesson = addLesson(admin, leaving, "L");
        Long stayingLesson = addLesson(admin, staying, "S");

        String email = uniqueEmail("quitter");
        String token = studentToken(email);
        enroll(token, leaving);
        enroll(token, staying);
        completeLesson(token, leaving, leavingLesson);
        completeLesson(token, staying, stayingLesson);

        mockMvc.perform(auth(delete("/api/profile/courses/" + leaving), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Successfully unenrolled from course: Leaving"));

        assertThat(jdbc.queryForList("SELECT course_id FROM student_courses", Long.class)).containsExactly(staying);
        assertThat(jdbc.queryForList("SELECT lesson_id FROM lesson_progress", Long.class)).containsExactly(stayingLesson);

        mockMvc.perform(auth(get("/api/courses/" + leaving + "/progress"), token)).andExpect(status().isForbidden());

        // Re-enrolling starts from zero
        enroll(token, leaving);
        mockMvc.perform(auth(get("/api/courses/" + leaving + "/progress"), token))
                .andExpect(jsonPath("$.completedLessons").value(0));
    }

    @Test
    void unenrollFromCourseNotEnrolledOrMissing() throws Exception {
        String token = studentToken();
        Long course = createPublishedCourse(adminToken(), "Never joined", false, 1, null);

        mockMvc.perform(auth(delete("/api/profile/courses/" + course), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("You are not enrolled in this course."));
        mockMvc.perform(auth(delete("/api/profile/courses/999999"), token))
                .andExpect(status().isNotFound());
    }
}
