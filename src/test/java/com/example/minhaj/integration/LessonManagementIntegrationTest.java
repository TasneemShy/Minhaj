package com.example.minhaj.integration;

import com.example.minhaj.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LessonManagementIntegrationTest extends IntegrationTestBase {

    private String admin;
    private Long courseId;
    private Long first;
    private Long second;
    private Long third;

    @BeforeEach
    void seed() throws Exception {
        admin = adminToken();
        courseId = createCourse(admin, "Java", false, 10, null);
        first = addLesson(admin, courseId, "One");
        second = addLesson(admin, courseId, "Two");
        third = addLesson(admin, courseId, "Three");
    }

    private String lessonUrl(String prefix, Long course, Long lesson) {
        return "/api/" + prefix + "/courses/" + course + "/lessons/" + lesson;
    }

    private String orderUrl(String prefix, Long course) {
        return "/api/" + prefix + "/courses/" + course + "/lessons/order";
    }

    private static String orderJson(Long... ids) {
        return "{\"lessonIds\":" + List.of(ids) + "}";
    }

    private static Integer[] ints(Long... ids) {
        Integer[] result = new Integer[ids.length];
        for (int i = 0; i < ids.length; i++) result[i] = ids[i].intValue();
        return result;
    }

    // ---- create & list ----

    @Test
    void newLessonsGoToTheEndOfTheirCourse() throws Exception {
        Long otherCourse = createCourse(admin, "Other", true, 0, null);
        Long otherLesson = addLesson(admin, otherCourse, "Elsewhere");

        mockMvc.perform(get("/api/courses/" + courseId + "/lessons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(ints(first, second, third))))
                .andExpect(jsonPath("$[*].position", contains(1, 2, 3)));
        // positions are counted per course
        mockMvc.perform(get("/api/courses/" + otherCourse + "/lessons"))
                .andExpect(jsonPath("$[0].id").value(otherLesson))
                .andExpect(jsonPath("$[0].position").value(1));
    }

    @Test
    void listIsSortedByPositionNotById() throws Exception {
        // Reverse the stored order behind the API's back (one statement, so the deferred unique check passes)
        jdbc.update("UPDATE lessons SET position = 4 - position WHERE course_id = ?", courseId);

        mockMvc.perform(get("/api/courses/" + courseId + "/lessons"))
                .andExpect(jsonPath("$[*].id", contains(ints(third, second, first))))
                .andExpect(jsonPath("$[*].position", contains(1, 2, 3)));
    }

    // ---- edit ----

    @Test
    void adminEditsLessonKeepingItsPosition() throws Exception {
        mockMvc.perform(withJson(put(lessonUrl("admin", courseId, second)), admin,
                        json("title", "Two v2", "videoUrl", "https://video/2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(second))
                .andExpect(jsonPath("$.title").value("Two v2"))
                .andExpect(jsonPath("$.videoUrl").value("https://video/2"))
                .andExpect(jsonPath("$.position").value(2));

        mockMvc.perform(get("/api/courses/" + courseId + "/lessons"))
                .andExpect(jsonPath("$[*].title", contains("One", "Two v2", "Three")));
    }

    @Test
    void editValidatesBodyAndLessonOwnership() throws Exception {
        Long otherCourse = createCourse(admin, "Other", true, 0, null);

        mockMvc.perform(withJson(put(lessonUrl("admin", courseId, first)), admin, json("title", " ")))
                .andExpect(status().isBadRequest());
        // lesson exists but belongs to another course
        mockMvc.perform(withJson(put(lessonUrl("admin", otherCourse, first)), admin, json("title", "X")))
                .andExpect(status().isNotFound());
        mockMvc.perform(withJson(put(lessonUrl("admin", courseId, 999999L)), admin, json("title", "X")))
                .andExpect(status().isNotFound());
        mockMvc.perform(withJson(put(lessonUrl("admin", 999999L, first)), admin, json("title", "X")))
                .andExpect(status().isNotFound());
    }

    // ---- delete ----

    @Test
    void deleteRemovesProgressCompactsPositionsAndUpdatesCounts() throws Exception {
        String alice = studentToken();
        String bob = studentToken();
        enroll(alice, courseId);
        enroll(bob, courseId);
        completeLesson(alice, courseId, first);
        completeLesson(alice, courseId, second);
        completeLesson(bob, courseId, second);

        mockMvc.perform(auth(get("/api/courses/" + courseId + "/progress"), alice))
                .andExpect(jsonPath("$.percentage").value(67));
        // 2 students x 3 lessons = 6 possible, 3 done
        mockMvc.perform(auth(get("/api/admin/analytics/courses/" + courseId), admin))
                .andExpect(jsonPath("$.averageCompletionPercentage").value(50.0));

        mockMvc.perform(auth(delete(lessonUrl("admin", courseId, second)), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Lesson deleted successfully!"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM lesson_progress WHERE lesson_id = ?", Long.class, second))
                .isZero();
        assertThat(count("lesson_progress")).isEqualTo(1);
        assertThat(count("lessons")).isEqualTo(2);

        mockMvc.perform(get("/api/courses/" + courseId + "/lessons"))
                .andExpect(jsonPath("$[*].id", contains(ints(first, third))))
                .andExpect(jsonPath("$[*].position", contains(1, 2)));
        mockMvc.perform(get("/api/courses/" + courseId))
                .andExpect(jsonPath("$.lessonCount").value(2));

        mockMvc.perform(auth(get("/api/courses/" + courseId + "/progress"), alice))
                .andExpect(jsonPath("$.completedLessons").value(1))
                .andExpect(jsonPath("$.totalLessons").value(2))
                .andExpect(jsonPath("$.percentage").value(50))
                .andExpect(jsonPath("$.completedLessonIds", contains(first.intValue())));
        mockMvc.perform(auth(get("/api/courses/" + courseId + "/progress"), bob))
                .andExpect(jsonPath("$.completedLessons").value(0))
                .andExpect(jsonPath("$.percentage").value(0));
        // 2 students x 2 lessons = 4 possible, 1 done
        mockMvc.perform(auth(get("/api/admin/analytics/courses/" + courseId), admin))
                .andExpect(jsonPath("$.lessonCount").value(2))
                .andExpect(jsonPath("$.completedLessons").value(1))
                .andExpect(jsonPath("$.averageCompletionPercentage").value(25.0));

        // a lesson added after the delete goes to the (new) end
        Long fourth = addLesson(admin, courseId, "Four");
        mockMvc.perform(get("/api/courses/" + courseId + "/lessons"))
                .andExpect(jsonPath("$[*].id", contains(ints(first, third, fourth))))
                .andExpect(jsonPath("$[*].position", contains(1, 2, 3)));
    }

    @Test
    void deleteReturns404ForLessonOutsideTheCourse() throws Exception {
        Long otherCourse = createCourse(admin, "Other", true, 0, null);

        mockMvc.perform(auth(delete(lessonUrl("admin", otherCourse, first)), admin))
                .andExpect(status().isNotFound());
        mockMvc.perform(auth(delete(lessonUrl("admin", courseId, 999999L)), admin))
                .andExpect(status().isNotFound());
        assertThat(count("lessons")).isEqualTo(3);
    }

    // ---- reorder ----

    @Test
    void reorderAssignsPositionsInTheGivenOrder() throws Exception {
        mockMvc.perform(withJson(put(orderUrl("admin", courseId)), admin, orderJson(third, first, second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(ints(third, first, second))))
                .andExpect(jsonPath("$[*].position", contains(1, 2, 3)));

        mockMvc.perform(get("/api/courses/" + courseId + "/lessons"))
                .andExpect(jsonPath("$[*].id", contains(ints(third, first, second))))
                .andExpect(jsonPath("$[*].position", contains(1, 2, 3)));
    }

    @Test
    void reorderRejectsAnythingButEachLessonOnce() throws Exception {
        Long otherCourse = createCourse(admin, "Other", true, 0, null);
        Long foreign = addLesson(admin, otherCourse, "Foreign");

        // missing id
        mockMvc.perform(withJson(put(orderUrl("admin", courseId)), admin, orderJson(first, second)))
                .andExpect(status().isBadRequest());
        // duplicate id (right length, one lesson missing)
        mockMvc.perform(withJson(put(orderUrl("admin", courseId)), admin, orderJson(first, first, second)))
                .andExpect(status().isBadRequest());
        // duplicate id on top of a complete list
        mockMvc.perform(withJson(put(orderUrl("admin", courseId)), admin, orderJson(first, second, third, third)))
                .andExpect(status().isBadRequest());
        // lesson of another course
        mockMvc.perform(withJson(put(orderUrl("admin", courseId)), admin, orderJson(first, second, foreign)))
                .andExpect(status().isBadRequest());
        // no list at all
        mockMvc.perform(withJson(put(orderUrl("admin", courseId)), admin, "{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withJson(put(orderUrl("admin", 999999L)), admin, orderJson(first)))
                .andExpect(status().isNotFound());

        // nothing changed
        mockMvc.perform(get("/api/courses/" + courseId + "/lessons"))
                .andExpect(jsonPath("$[*].id", contains(ints(first, second, third))));
    }

    // ---- who may do it ----

    @Test
    void owningInstructorManagesLessonsOfOwnCourse() throws Exception {
        String instructor = instructorToken(uniqueEmail("owner"));
        Long mine = postForId("/api/instructor/courses", instructor, courseJson("Mine", false, 5, null));
        Long a = postForId("/api/instructor/courses/" + mine + "/lessons", instructor, json("title", "A"));
        Long b = postForId("/api/instructor/courses/" + mine + "/lessons", instructor, json("title", "B"));
        Long c = postForId("/api/instructor/courses/" + mine + "/lessons", instructor, json("title", "C"));

        mockMvc.perform(withJson(put(orderUrl("instructor", mine)), instructor, orderJson(c, b, a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(ints(c, b, a))));
        mockMvc.perform(withJson(put(lessonUrl("instructor", mine, b)), instructor, json("title", "B v2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("B v2"))
                .andExpect(jsonPath("$.position").value(2));
        mockMvc.perform(auth(delete(lessonUrl("instructor", mine, c)), instructor))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/courses/" + mine + "/lessons"))
                .andExpect(jsonPath("$[*].id", contains(ints(b, a))))
                .andExpect(jsonPath("$[*].position", contains(1, 2)));
        mockMvc.perform(get("/api/courses/" + mine)).andExpect(jsonPath("$.lessonCount").value(2));

        // a lesson id from someone else's course is still a 404 inside my own course
        mockMvc.perform(auth(delete(lessonUrl("instructor", mine, first)), instructor))
                .andExpect(status().isNotFound());
    }

    @Test
    void instructorCannotTouchAnotherInstructorsLessons() throws Exception {
        String owner = instructorToken(uniqueEmail("owner"));
        String intruder = instructorToken(uniqueEmail("intruder"));
        Long theirs = postForId("/api/instructor/courses", owner, courseJson("Theirs", false, 5, null));
        Long lesson = postForId("/api/instructor/courses/" + theirs + "/lessons", owner, json("title", "Private"));

        mockMvc.perform(withJson(put(lessonUrl("instructor", theirs, lesson)), intruder, json("title", "Hacked")))
                .andExpect(status().isForbidden());
        mockMvc.perform(auth(delete(lessonUrl("instructor", theirs, lesson)), intruder))
                .andExpect(status().isForbidden());
        mockMvc.perform(withJson(put(orderUrl("instructor", theirs)), intruder, orderJson(lesson)))
                .andExpect(status().isForbidden());
        // admin-created courses have no owner, so instructors cannot touch them either
        mockMvc.perform(auth(delete(lessonUrl("instructor", courseId, first)), intruder))
                .andExpect(status().isForbidden());
        // nor the admin endpoints
        mockMvc.perform(auth(delete(lessonUrl("admin", theirs, lesson)), intruder))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/courses/" + theirs + "/lessons"))
                .andExpect(jsonPath("$[0].title").value("Private"));

        // an admin may use the instructor endpoints on any course
        mockMvc.perform(withJson(put(lessonUrl("instructor", theirs, lesson)), admin, json("title", "Fixed by admin")))
                .andExpect(status().isOk());
    }

    @Test
    void studentCannotManageLessons() throws Exception {
        String student = studentToken();

        for (String prefix : List.of("admin", "instructor")) {
            mockMvc.perform(withJson(put(lessonUrl(prefix, courseId, first)), student, json("title", "X")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(auth(delete(lessonUrl(prefix, courseId, first)), student))
                    .andExpect(status().isForbidden());
            mockMvc.perform(withJson(put(orderUrl(prefix, courseId)), student, orderJson(third, second, first)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(withJson(put(orderUrl("admin", courseId)), null, orderJson(third, second, first)))
                .andExpect(status().isUnauthorized());

        assertThat(count("lessons")).isEqualTo(3);
        mockMvc.perform(get("/api/courses/" + courseId + "/lessons"))
                .andExpect(jsonPath("$[*].title", contains("One", "Two", "Three")));
    }
}
