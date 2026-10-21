package com.example.minhaj.integration;

import com.example.minhaj.support.IntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseCompletionIntegrationTest extends IntegrationTestBase {

    private String admin;
    private String student;
    private String studentEmail;
    private Long courseId;
    private Long lesson1;
    private Long lesson2;

    @BeforeEach
    void seed() throws Exception {
        admin = adminToken();
        String instructorEmail = uniqueEmail("instructor");
        String instructor = instructorToken(instructorEmail);
        courseId = postForId("/api/instructor/courses", instructor, courseJson("Two lessons", false, 10, null));
        markPublished(courseId);
        lesson1 = addLesson(admin, courseId, "One");
        lesson2 = addLesson(admin, courseId, "Two");
        studentEmail = uniqueEmail("student");
        student = studentToken(studentEmail);
        enroll(student, courseId);
    }

    private String completeUrl(Long lesson) {
        return "/api/courses/" + courseId + "/lessons/" + lesson + "/complete";
    }

    private String progressUrl() {
        return "/api/courses/" + courseId + "/progress";
    }

    private String completionUrl(Long course) {
        return "/api/courses/" + course + "/completion";
    }

    private static boolean isCompletionEmail(SimpleMailMessage message) {
        return message.getSubject() != null && message.getSubject().startsWith("Congratulations on completing");
    }

    private LocalDateTime storedCompletedAt(Long lessonId) {
        return jdbc.queryForObject("SELECT completed_at FROM lesson_progress WHERE user_id = ? AND lesson_id = ?",
                Timestamp.class, userId(studentEmail), lessonId).toLocalDateTime();
    }

    @Test
    void courseIsCompletedAfterTheLastLesson() throws Exception {
        mockMvc.perform(auth(post(completeUrl(lesson1)), student))
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.completedAt").doesNotExist());

        String body = mockMvc.perform(auth(post(completeUrl(lesson2)), student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.percentage").value(100))
                .andExpect(jsonPath("$.completed").value(true))
                .andReturn().getResponse().getContentAsString();

        // completedAt is the latest lesson completion, exactly as Postgres stores it (microseconds)
        LocalDateTime completedAt = LocalDateTime.parse(JsonPath.read(body, "$.completedAt"));
        assertThat(completedAt).isEqualTo(storedCompletedAt(lesson2));
        assertThat(completedAt).isAfterOrEqualTo(storedCompletedAt(lesson1));

        mockMvc.perform(auth(get(progressUrl()), student))
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.completedAt").value(JsonPath.<String>read(body, "$.completedAt")));
        mockMvc.perform(auth(get("/api/my-courses/progress"), student))
                .andExpect(jsonPath("$[0].completed").value(true));

        String completion = mockMvc.perform(auth(get(completionUrl(courseId)), student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").value(courseId))
                .andExpect(jsonPath("$.courseTitle").value("Two lessons"))
                .andExpect(jsonPath("$.studentName").value("Student " + studentEmail))
                .andExpect(jsonPath("$.lessonCount").value(2))
                .andExpect(jsonPath("$.instructorName").value(org.hamcrest.Matchers.startsWith("INSTRUCTOR ")))
                .andReturn().getResponse().getContentAsString();
        assertThat(LocalDateTime.parse(JsonPath.read(completion, "$.completedAt"))).isEqualTo(completedAt);

        mockMvc.perform(auth(get("/api/my-courses/completed"), student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].courseId", contains(courseId.intValue())));
    }

    @Test
    void uncompletingALessonMakesTheCourseIncomplete() throws Exception {
        completeLesson(student, courseId, lesson1);
        completeLesson(student, courseId, lesson2);

        mockMvc.perform(auth(delete(completeUrl(lesson1)), student))
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.completedAt").doesNotExist());
        mockMvc.perform(auth(get(completionUrl(courseId)), student))
                .andExpect(status().isNotFound());
    }

    @Test
    void courseWithoutLessonsIsNeverCompleted() throws Exception {
        Long empty = createPublishedCourse(admin, "Empty", false, 1, null);
        enroll(student, empty);

        mockMvc.perform(auth(get("/api/courses/" + empty + "/progress"), student))
                .andExpect(jsonPath("$.totalLessons").value(0))
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.completedAt").doesNotExist());
        mockMvc.perform(auth(get(completionUrl(empty)), student))
                .andExpect(status().isNotFound());
    }

    @Test
    void addingALessonMakesTheCourseIncompleteAgain() throws Exception {
        completeLesson(student, courseId, lesson1);
        completeLesson(student, courseId, lesson2);

        Long lesson3 = addLesson(admin, courseId, "Three");

        mockMvc.perform(auth(get(progressUrl()), student))
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.completedAt").doesNotExist());
        mockMvc.perform(auth(get(completionUrl(courseId)), student))
                .andExpect(status().isNotFound());
        mockMvc.perform(auth(get("/api/my-courses/completed"), student))
                .andExpect(jsonPath("$", empty()));

        // Completing the new lesson completes the course again, now dated by the new lesson
        String body = mockMvc.perform(auth(post(completeUrl(lesson3)), student))
                .andExpect(jsonPath("$.completed").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(LocalDateTime.parse(JsonPath.read(body, "$.completedAt"))).isEqualTo(storedCompletedAt(lesson3));
    }

    @Test
    void deletingTheOnlyMissingLessonCompletesTheCourse() throws Exception {
        completeLesson(student, courseId, lesson1);
        mockMvc.perform(auth(get(progressUrl()), student))
                .andExpect(jsonPath("$.completed").value(false));

        mockMvc.perform(auth(delete("/api/admin/courses/" + courseId + "/lessons/" + lesson2), admin))
                .andExpect(status().isOk());

        String body = mockMvc.perform(auth(get(progressUrl()), student))
                .andExpect(jsonPath("$.totalLessons").value(1))
                .andExpect(jsonPath("$.completed").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(LocalDateTime.parse(JsonPath.read(body, "$.completedAt"))).isEqualTo(storedCompletedAt(lesson1));
        mockMvc.perform(auth(get(completionUrl(courseId)), student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lessonCount").value(1));
    }

    @Test
    void congratulationsEmailIsSentOnlyOnTheTransitionToComplete() throws Exception {
        completeLesson(student, courseId, lesson1);
        verify(mailSender, after(300).never()).send(argThat((SimpleMailMessage m) -> isCompletionEmail(m)));

        completeLesson(student, courseId, lesson2);
        verify(mailSender, timeout(2000).times(1)).send(argThat((SimpleMailMessage m) ->
                isCompletionEmail(m) && studentEmail.equals(m.getTo()[0]) && m.getSubject().contains("Two lessons")));

        // Repeating completions of an already completed course sends nothing more
        completeLesson(student, courseId, lesson2);
        completeLesson(student, courseId, lesson1);
        mockMvc.perform(auth(get(progressUrl()), student)).andExpect(status().isOk());
        verify(mailSender, after(300).times(1)).send(argThat((SimpleMailMessage m) -> isCompletionEmail(m)));
    }

    @Test
    void completingAgainAfterUndoingALessonSendsAnotherEmail() throws Exception {
        completeLesson(student, courseId, lesson1);
        completeLesson(student, courseId, lesson2);
        verify(mailSender, timeout(2000).times(1)).send(argThat((SimpleMailMessage m) -> isCompletionEmail(m)));

        mockMvc.perform(auth(delete(completeUrl(lesson2)), student)).andExpect(status().isOk());
        completeLesson(student, courseId, lesson2);

        // Completion is not stored, so the second incomplete -> complete transition is congratulated too
        verify(mailSender, timeout(2000).times(2)).send(argThat((SimpleMailMessage m) -> isCompletionEmail(m)));
    }

    @Test
    void completionIs404ForNonEnrolledOrIncompleteStudents() throws Exception {
        String outsider = studentToken();
        mockMvc.perform(auth(get(completionUrl(courseId)), outsider))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("You have not completed course " + courseId + "."));

        // Enrolled, one lesson of two done
        completeLesson(student, courseId, lesson1);
        mockMvc.perform(auth(get(completionUrl(courseId)), student))
                .andExpect(status().isNotFound());

        mockMvc.perform(auth(get(completionUrl(999999L)), student))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(completionUrl(courseId)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(auth(get("/api/my-courses/completed"), student))
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    void completionIsPerStudent() throws Exception {
        String other = studentToken();
        enroll(other, courseId);
        completeLesson(student, courseId, lesson1);
        completeLesson(student, courseId, lesson2);

        mockMvc.perform(auth(get(completionUrl(courseId)), student)).andExpect(status().isOk());
        mockMvc.perform(auth(get(completionUrl(courseId)), other)).andExpect(status().isNotFound());
        mockMvc.perform(auth(get(progressUrl()), other))
                .andExpect(jsonPath("$.completed").value(false));
    }

    @Test
    void unverifiedStudentsCanStillCompleteCoursesTheyJoined() throws Exception {
        // Progress does not require a verified email (only enrolling does), so neither does completion.
        jdbc.update("UPDATE users SET email_verified = FALSE WHERE email = ?", studentEmail);
        completeLesson(student, courseId, lesson1);
        completeLesson(student, courseId, lesson2);

        mockMvc.perform(auth(get(completionUrl(courseId)), student)).andExpect(status().isOk());
    }
}
