package com.example.minhaj.integration;

import com.example.minhaj.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InstructorIntegrationTest extends IntegrationTestBase {

    @Test
    void adminAssignsInstructorRoleWhichAppliesAfterNextLogin() throws Exception {
        String admin = adminToken();
        String email = uniqueEmail("teacher");
        String oldToken = studentToken(email);

        mockMvc.perform(withJson(put("/api/admin/users/" + userId(email) + "/role"), admin, json("role", "INSTRUCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("INSTRUCTOR"));

        // The old JWT still says STUDENT
        mockMvc.perform(auth(get("/api/instructor/courses"), oldToken)).andExpect(status().isForbidden());

        String newToken = login(email, PASSWORD);
        mockMvc.perform(auth(get("/api/instructor/courses"), newToken)).andExpect(status().isOk());
    }

    @Test
    void roleAssignmentValidatesRoleAndUser() throws Exception {
        String admin = adminToken();
        String email = uniqueEmail("x");
        studentToken(email);

        mockMvc.perform(withJson(put("/api/admin/users/" + userId(email) + "/role"), admin, json("role", "SUPERUSER")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withJson(put("/api/admin/users/999999/role"), admin, json("role", "ADMIN")))
                .andExpect(status().isNotFound());
        mockMvc.perform(withJson(put("/api/admin/users/" + userId(email) + "/role"), studentToken(), json("role", "ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentCannotUseInstructorEndpoints() throws Exception {
        mockMvc.perform(withJson(post("/api/instructor/courses"), studentToken(), courseJson("Nope", false, 1, null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void instructorCreatesListsAndEditsOwnCourse() throws Exception {
        String email = uniqueEmail("owner");
        String instructor = instructorToken(email);

        // instructor in the body is read-only: the server sets the owner
        Long id = postForId("/api/instructor/courses", instructor,
                "{\"title\":\"Mine\",\"price\":10,\"instructor\":{\"id\":424242}}");
        mockMvc.perform(get("/api/courses/" + id))
                .andExpect(jsonPath("$.instructorId").value(userId(email)));

        mockMvc.perform(withJson(put("/api/instructor/courses/" + id), instructor, courseJson("Mine v2", false, 12, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Mine v2"));

        Long lessonId = postForId("/api/instructor/courses/" + id + "/lessons", instructor, json("title", "Lesson 1"));
        assertThat(lessonId).isNotNull();

        mockMvc.perform(auth(get("/api/instructor/courses"), instructor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title", contains("Mine v2")))
                .andExpect(jsonPath("$.content[0].instructorId").value(userId(email)));

        mockMvc.perform(get("/api/courses?instructorId=" + userId(email)))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void instructorGets403OnAnotherInstructorsCourse() throws Exception {
        String alice = instructorToken(uniqueEmail("alice"));
        String bob = instructorToken(uniqueEmail("bob"));
        Long alicesCourse = postForId("/api/instructor/courses", alice, courseJson("Alice's", false, 10, null));

        mockMvc.perform(withJson(put("/api/instructor/courses/" + alicesCourse), bob, courseJson("Stolen", false, 1, null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("You can only manage your own courses."));
        mockMvc.perform(withJson(post("/api/instructor/courses/" + alicesCourse + "/lessons"), bob, json("title", "Spam")))
                .andExpect(status().isForbidden());
        mockMvc.perform(auth(delete("/api/instructor/courses/" + alicesCourse), bob))
                .andExpect(status().isForbidden());

        // Admin-created courses have no owner, so instructors cannot touch them either
        Long adminCourse = createCourse(adminToken(), "Admin's", false, 1, null);
        mockMvc.perform(auth(delete("/api/instructor/courses/" + adminCourse), bob))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/courses/" + alicesCourse)).andExpect(jsonPath("$.title").value("Alice's"));
    }

    @Test
    void adminCanManageAnyCourseThroughInstructorEndpoints() throws Exception {
        String alice = instructorToken(uniqueEmail("alice"));
        String admin = adminToken();
        Long alicesCourse = postForId("/api/instructor/courses", alice, courseJson("Alice's", false, 10, null));

        mockMvc.perform(withJson(put("/api/instructor/courses/" + alicesCourse), admin, courseJson("Moderated", false, 10, null)))
                .andExpect(status().isOk());
        mockMvc.perform(auth(delete("/api/instructor/courses/" + alicesCourse), admin))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/courses/" + alicesCourse)).andExpect(status().isNotFound());
    }

    @Test
    void deletingCourseWithLessonsReviewsEnrollmentsAndProgressLeavesNoOrphans() throws Exception {
        String instructor = instructorToken(uniqueEmail("owner"));
        Long doomed = postForId("/api/instructor/courses", instructor, courseJson("Doomed", false, 10, null));
        Long survivor = postForId("/api/instructor/courses", instructor, courseJson("Survivor", false, 10, null));

        Long doomedLesson = postForId("/api/instructor/courses/" + doomed + "/lessons", instructor, json("title", "L1"));
        postForId("/api/instructor/courses/" + doomed + "/lessons", instructor, json("title", "L2"));
        Long survivorLesson = postForId("/api/instructor/courses/" + survivor + "/lessons", instructor, json("title", "S1"));

        String student = studentToken();
        for (Long course : new Long[]{doomed, survivor}) {
            enroll(student, course);
            review(student, course, 4, "nice");
        }
        completeLesson(student, doomed, doomedLesson);
        completeLesson(student, survivor, survivorLesson);

        mockMvc.perform(auth(delete("/api/instructor/courses/" + doomed), instructor))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM courses WHERE id = ?", Long.class, doomed)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM lessons WHERE course_id = ?", Long.class, doomed)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reviews WHERE course_id = ?", Long.class, doomed)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM student_courses WHERE course_id = ?", Long.class, doomed)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM lesson_progress WHERE lesson_id = ?", Long.class, doomedLesson)).isZero();

        // The other course is untouched
        assertThat(count("courses")).isEqualTo(1);
        assertThat(count("lessons")).isEqualTo(1);
        assertThat(count("reviews")).isEqualTo(1);
        assertThat(count("student_courses")).isEqualTo(1);
        assertThat(count("lesson_progress")).isEqualTo(1);
        mockMvc.perform(auth(get("/api/courses/" + survivor + "/progress"), student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.percentage").value(100));
    }

    @Test
    void adminDeleteOfCourseWithDependentsAlsoSucceeds() throws Exception {
        String admin = adminToken();
        Long id = createCourse(admin, "Busy", false, 10, null);
        Long lesson = addLesson(admin, id, "L1");
        String student = studentToken();
        enroll(student, id);
        review(student, id, 5, "wow");
        completeLesson(student, id, lesson);

        mockMvc.perform(auth(delete("/api/admin/courses/" + id), admin)).andExpect(status().isOk());

        assertThat(count("courses") + count("lessons") + count("reviews")
                + count("student_courses") + count("lesson_progress")).isZero();
        mockMvc.perform(auth(get("/api/my-courses"), student))
                .andExpect(jsonPath("$.length()").value(0));
    }
}
