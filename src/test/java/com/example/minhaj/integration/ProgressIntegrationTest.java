package com.example.minhaj.integration;

import com.example.minhaj.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProgressIntegrationTest extends IntegrationTestBase {

    private String student;
    private Long courseId;
    private Long lesson1;
    private Long lesson2;
    private Long lesson3;

    @BeforeEach
    void seed() throws Exception {
        String admin = adminToken();
        courseId = createCourse(admin, "Three lessons", false, 10, null);
        lesson1 = addLesson(admin, courseId, "One");
        lesson2 = addLesson(admin, courseId, "Two");
        lesson3 = addLesson(admin, courseId, "Three");
        student = studentToken();
    }

    private String completeUrl(Long course, Long lesson) {
        return "/api/courses/" + course + "/lessons/" + lesson + "/complete";
    }

    @Test
    void cannotTrackProgressWithoutEnrolment() throws Exception {
        mockMvc.perform(auth(post(completeUrl(courseId, lesson1)), student))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("You must be enrolled in this course to track your progress."));
        mockMvc.perform(auth(delete(completeUrl(courseId, lesson1)), student))
                .andExpect(status().isForbidden());
        mockMvc.perform(auth(get("/api/courses/" + courseId + "/progress"), student))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(completeUrl(courseId, lesson1)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void completeAndUncompleteUpdatePercentage() throws Exception {
        enroll(student, courseId);

        mockMvc.perform(auth(get("/api/courses/" + courseId + "/progress"), student))
                .andExpect(jsonPath("$.completedLessons").value(0))
                .andExpect(jsonPath("$.totalLessons").value(3))
                .andExpect(jsonPath("$.percentage").value(0));

        mockMvc.perform(auth(post(completeUrl(courseId, lesson1)), student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedLessons").value(1))
                .andExpect(jsonPath("$.percentage").value(33));

        mockMvc.perform(auth(post(completeUrl(courseId, lesson3)), student))
                .andExpect(jsonPath("$.percentage").value(67))
                .andExpect(jsonPath("$.completedLessonIds", contains(lesson1.intValue(), lesson3.intValue())));

        // Completing twice is a no-op
        mockMvc.perform(auth(post(completeUrl(courseId, lesson3)), student))
                .andExpect(jsonPath("$.completedLessons").value(2));

        mockMvc.perform(auth(post(completeUrl(courseId, lesson2)), student))
                .andExpect(jsonPath("$.percentage").value(100));

        mockMvc.perform(auth(delete(completeUrl(courseId, lesson1)), student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedLessons").value(2))
                .andExpect(jsonPath("$.percentage").value(67));

        // Uncompleting a lesson that was never completed is a no-op too
        mockMvc.perform(auth(delete(completeUrl(courseId, lesson1)), student))
                .andExpect(jsonPath("$.completedLessons").value(2));
    }

    @Test
    void progressIsPerStudent() throws Exception {
        String other = studentToken();
        enroll(student, courseId);
        enroll(other, courseId);
        completeLesson(student, courseId, lesson1);

        mockMvc.perform(auth(get("/api/courses/" + courseId + "/progress"), other))
                .andExpect(jsonPath("$.completedLessons").value(0))
                .andExpect(jsonPath("$.completedLessonIds", empty()));
    }

    @Test
    void lessonFromAnotherCourseIs404() throws Exception {
        String admin = adminToken();
        Long otherCourse = createCourse(admin, "Other", false, 1, null);
        Long foreignLesson = addLesson(admin, otherCourse, "Foreign");
        enroll(student, courseId);

        mockMvc.perform(auth(post(completeUrl(courseId, foreignLesson)), student))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Lesson " + foreignLesson + " does not belong to course " + courseId));
        mockMvc.perform(auth(post(completeUrl(courseId, 999999L)), student))
                .andExpect(status().isNotFound());
        mockMvc.perform(auth(post(completeUrl(999999L, lesson1)), student))
                .andExpect(status().isNotFound());
    }

    @Test
    void courseWithoutLessonsIsZeroPercent() throws Exception {
        Long empty = createCourse(adminToken(), "Empty", false, 1, null);
        enroll(student, empty);

        mockMvc.perform(auth(get("/api/courses/" + empty + "/progress"), student))
                .andExpect(jsonPath("$.totalLessons").value(0))
                .andExpect(jsonPath("$.percentage").value(0));
    }

    @Test
    void myCoursesProgressListsEveryEnrolledCourse() throws Exception {
        Long second = createCourse(adminToken(), "Second", false, 1, null);
        enroll(student, courseId);
        enroll(student, second);
        completeLesson(student, courseId, lesson2);

        mockMvc.perform(auth(get("/api/my-courses/progress"), student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].courseId", contains(courseId.intValue(), second.intValue())))
                .andExpect(jsonPath("$[0].percentage").value(33))
                .andExpect(jsonPath("$[1].percentage").value(0));
    }
}
