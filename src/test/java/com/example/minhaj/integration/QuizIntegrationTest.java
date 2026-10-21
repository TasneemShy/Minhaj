package com.example.minhaj.integration;

import com.example.minhaj.support.IntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuizIntegrationTest extends IntegrationTestBase {

    private String admin;
    private String instructor;
    private String student;
    private String studentEmail;
    private Long courseId;
    private Long lesson1;
    private Long lesson2;

    // Ids from the quiz saved on lesson2 by seedQuiz: q1 single (a correct), q2 multiple (c and d correct),
    // q3 single (g correct).
    private Long quizId;
    private Long q1, a, b, q2, c, d, e, q3, f, g;

    @BeforeEach
    void seed() throws Exception {
        admin = adminToken();
        instructor = instructorToken(uniqueEmail("instructor"));
        courseId = postForId("/api/instructor/courses", instructor, courseJson("Quiz course", false, 10, null));
        lesson1 = postForId("/api/instructor/courses/" + courseId + "/lessons", instructor, json("title", "One"));
        lesson2 = postForId("/api/instructor/courses/" + courseId + "/lessons", instructor, json("title", "Two"));
        markPublished(courseId);
        studentEmail = uniqueEmail("student");
        student = studentToken(studentEmail);
        enroll(student, courseId);
    }

    // ---- helpers ----

    private String authorUrl(String prefix, Long course, Long lesson) {
        return "/api/" + prefix + "/courses/" + course + "/lessons/" + lesson + "/quiz";
    }

    private String quizUrl(Long course, Long lesson) {
        return "/api/courses/" + course + "/lessons/" + lesson + "/quiz";
    }

    private String attemptsUrl(Long course, Long lesson) {
        return quizUrl(course, lesson) + "/attempts";
    }

    private static String option(String text, boolean correct) {
        return "{\"text\":\"" + text + "\",\"correct\":" + correct + "}";
    }

    private static String question(String text, String type, String... options) {
        return "{\"text\":\"" + text + "\",\"type\":\"" + type + "\",\"options\":[" + String.join(",", options) + "]}";
    }

    private static String quizJson(Integer passPercentage, String... questions) {
        return "{\"title\":\"Check\"" + (passPercentage == null ? "" : ",\"passPercentage\":" + passPercentage)
                + ",\"questions\":[" + String.join(",", questions) + "]}";
    }

    private static String threeQuestions(Integer passPercentage) {
        return quizJson(passPercentage,
                question("Q1", "SINGLE_CHOICE", option("A", true), option("B", false)),
                question("Q2", "MULTIPLE_CHOICE", option("C", true), option("D", true), option("E", false)),
                question("Q3", "SINGLE_CHOICE", option("F", false), option("G", true)));
    }

    private static Long id(String body, String path) {
        return ((Number) JsonPath.read(body, path)).longValue();
    }

    private String saveQuiz(String token, Long lesson, String body) throws Exception {
        return mockMvc.perform(withJson(put(authorUrl("instructor", courseId, lesson)), token, body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private void seedQuiz(Integer passPercentage) throws Exception {
        String body = saveQuiz(instructor, lesson2, threeQuestions(passPercentage));
        quizId = id(body, "$.id");
        q1 = id(body, "$.questions[0].id");
        a = id(body, "$.questions[0].options[0].id");
        b = id(body, "$.questions[0].options[1].id");
        q2 = id(body, "$.questions[1].id");
        c = id(body, "$.questions[1].options[0].id");
        d = id(body, "$.questions[1].options[1].id");
        e = id(body, "$.questions[1].options[2].id");
        q3 = id(body, "$.questions[2].id");
        f = id(body, "$.questions[2].options[0].id");
        g = id(body, "$.questions[2].options[1].id");
    }

    // answers(q1, List.of(a), q2, List.of(c, d)) -> {"answers":[{"questionId":..,"optionIds":[..]}, ...]}
    private static String answers(Object... questionThenOptions) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < questionThenOptions.length; i += 2) {
            parts.add("{\"questionId\":" + questionThenOptions[i] + ",\"optionIds\":" + questionThenOptions[i + 1] + "}");
        }
        return "{\"answers\":[" + String.join(",", parts) + "]}";
    }

    private String allCorrect() {
        return answers(q1, List.of(a), q2, List.of(c, d), q3, List.of(g));
    }

    // q1 and q3 right, q2 only partly selected: 2 of 3 = 66
    private String twoOfThree() {
        return answers(q1, List.of(a), q2, List.of(c), q3, List.of(g));
    }

    private String submit(String token, String body) throws Exception {
        return mockMvc.perform(withJson(post(attemptsUrl(courseId, lesson2)), token, body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static boolean isCompletionEmail(SimpleMailMessage message) {
        return message.getSubject() != null && message.getSubject().startsWith("Congratulations on completing");
    }

    // ---- authoring ----

    @Test
    void ownerCreatesQuizAndAdminSeesItWithAnswers() throws Exception {
        mockMvc.perform(withJson(put(authorUrl("instructor", courseId, lesson2)), instructor, threeQuestions(null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lessonId").value(lesson2))
                .andExpect(jsonPath("$.courseId").value(courseId))
                .andExpect(jsonPath("$.passPercentage").value(70))
                .andExpect(jsonPath("$.questions[*].position", contains(1, 2, 3)))
                .andExpect(jsonPath("$.questions[1].type").value("MULTIPLE_CHOICE"))
                .andExpect(jsonPath("$.questions[1].options[*].text", contains("C", "D", "E")))
                .andExpect(jsonPath("$.questions[1].options[*].correct", contains(true, true, false)));

        String body = mockMvc.perform(auth(get(authorUrl("admin", courseId, lesson2)), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Check"))
                .andExpect(jsonPath("$.questions[0].options[*].correct", contains(true, false)))
                .andReturn().getResponse().getContentAsString();

        // createdAt is returned exactly as Postgres stores it (microseconds)
        LocalDateTime stored = jdbc.queryForObject("SELECT created_at FROM quizzes WHERE lesson_id = ?",
                Timestamp.class, lesson2).toLocalDateTime();
        assertThat(LocalDateTime.parse(JsonPath.read(body, "$.createdAt"))).isEqualTo(stored);
    }

    @Test
    void invalidQuizzesAreRejected() throws Exception {
        String url = authorUrl("instructor", courseId, lesson2);
        String two = option("x", true) + "," + option("y", false);

        mockMvc.perform(withJson(put(url), instructor, quizJson(70)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.questions").exists());
        mockMvc.perform(withJson(put(url), instructor, quizJson(70, question("Q", "SINGLE_CHOICE", option("only", true)))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withJson(put(url), instructor, quizJson(70,
                        question("Q", "SINGLE_CHOICE", option("x", true), option("y", true)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("exactly one correct option")));
        mockMvc.perform(withJson(put(url), instructor, quizJson(70,
                        question("Q", "MULTIPLE_CHOICE", option("x", false), option("y", false)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("at least one correct option")));
        mockMvc.perform(withJson(put(url), instructor, quizJson(101, question("Q", "SINGLE_CHOICE", two))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.passPercentage").exists());
        mockMvc.perform(withJson(put(url), instructor, quizJson(-1, question("Q", "SINGLE_CHOICE", two))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withJson(put(url), instructor, quizJson(70, question("Q", "TRUE_FALSE", two))))
                .andExpect(status().isBadRequest());

        assertThat(count("quizzes")).isZero();
    }

    @Test
    void onlyTheOwnerOrAnAdminManagesTheQuiz() throws Exception {
        seedQuiz(70);
        String otherInstructor = instructorToken(uniqueEmail("other"));
        String url = authorUrl("instructor", courseId, lesson2);

        mockMvc.perform(withJson(put(url), otherInstructor, threeQuestions(70))).andExpect(status().isForbidden());
        mockMvc.perform(auth(get(url), otherInstructor)).andExpect(status().isForbidden());
        mockMvc.perform(auth(get(url + "/analytics"), otherInstructor)).andExpect(status().isForbidden());
        mockMvc.perform(auth(delete(url), otherInstructor)).andExpect(status().isForbidden());

        mockMvc.perform(withJson(put(url), student, threeQuestions(70))).andExpect(status().isForbidden());
        mockMvc.perform(auth(get(authorUrl("admin", courseId, lesson2)), student)).andExpect(status().isForbidden());
        mockMvc.perform(auth(get(authorUrl("admin", courseId, lesson2)), instructor)).andExpect(status().isForbidden());
        mockMvc.perform(get(url)).andExpect(status().isUnauthorized());

        // A lesson of another course is not found through this course
        Long otherCourse = createCourse(admin, "Other", true, 0, null);
        Long otherLesson = addLesson(admin, otherCourse, "Elsewhere");
        mockMvc.perform(auth(get(authorUrl("instructor", courseId, otherLesson)), instructor)).andExpect(status().isNotFound());
        mockMvc.perform(auth(get(authorUrl("instructor", courseId, lesson1)), instructor)).andExpect(status().isNotFound());

        // Admins manage any course under both prefixes
        mockMvc.perform(withJson(put(authorUrl("admin", courseId, lesson2)), admin, threeQuestions(50)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passPercentage").value(50));
        mockMvc.perform(auth(get(url), admin)).andExpect(status().isOk());
    }

    @Test
    void quizCanBeReplacedUntilItHasAttempts() throws Exception {
        seedQuiz(70);

        String replaced = saveQuiz(instructor, lesson2, quizJson(80,
                question("New", "SINGLE_CHOICE", option("x", false), option("y", true))));
        assertThat(id(replaced, "$.id")).isEqualTo(quizId);
        assertThat(JsonPath.<List<?>>read(replaced, "$.questions")).hasSize(1);
        assertThat(count("quiz_questions")).isEqualTo(1);
        assertThat(count("quiz_options")).isEqualTo(2);

        // Back to the three-question quiz, then one attempt locks it
        seedQuiz(70);
        submit(student, allCorrect());
        mockMvc.perform(withJson(put(authorUrl("instructor", courseId, lesson2)), instructor, threeQuestions(70)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("already has attempts")));

        // Deleting removes the quiz and its attempts; the lesson stays completed
        mockMvc.perform(auth(delete(authorUrl("instructor", courseId, lesson2)), instructor)).andExpect(status().isOk());
        assertThat(count("quizzes")).isZero();
        assertThat(count("quiz_questions")).isZero();
        assertThat(count("quiz_attempts")).isZero();
        assertThat(count("quiz_attempt_answers")).isZero();
        assertThat(count("lesson_progress")).isEqualTo(1);
        mockMvc.perform(auth(get(authorUrl("instructor", courseId, lesson2)), instructor)).andExpect(status().isNotFound());
        mockMvc.perform(auth(delete(authorUrl("instructor", courseId, lesson2)), instructor)).andExpect(status().isNotFound());
    }

    // ---- taking ----

    @Test
    void studentsSeeTheQuizWithoutCorrectAnswers() throws Exception {
        seedQuiz(70);

        mockMvc.perform(auth(get(quizUrl(courseId, lesson2)), student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(quizId))
                .andExpect(jsonPath("$.passPercentage").value(70))
                .andExpect(jsonPath("$.questions", hasSize(3)))
                .andExpect(jsonPath("$.questions[1].type").value("MULTIPLE_CHOICE"))
                .andExpect(jsonPath("$.questions[1].options[*].text", contains("C", "D", "E")))
                .andExpect(content().string(not(containsString("correct"))));
    }

    @Test
    void allCorrectPassesAndRevealsTheAnswers() throws Exception {
        seedQuiz(70);

        String body = mockMvc.perform(withJson(post(attemptsUrl(courseId, lesson2)), student, allCorrect()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quizId").value(quizId))
                .andExpect(jsonPath("$.scorePercentage").value(100))
                .andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.correctQuestions").value(3))
                .andExpect(jsonPath("$.totalQuestions").value(3))
                .andExpect(jsonPath("$.lessonCompleted").value(true))
                .andExpect(jsonPath("$.questions[*].correct", contains(true, true, true)))
                .andExpect(jsonPath("$.questions[1].selectedOptionIds", contains(c.intValue(), d.intValue())))
                .andExpect(jsonPath("$.questions[1].correctOptionIds", contains(c.intValue(), d.intValue())))
                .andReturn().getResponse().getContentAsString();

        LocalDateTime stored = jdbc.queryForObject("SELECT submitted_at FROM quiz_attempts WHERE id = ?",
                Timestamp.class, id(body, "$.attemptId")).toLocalDateTime();
        assertThat(LocalDateTime.parse(JsonPath.read(body, "$.submittedAt"))).isEqualTo(stored);
        assertThat(count("quiz_attempt_answers")).isEqualTo(4);
    }

    @Test
    void partialMultipleChoiceSelectionScoresZeroForThatQuestionAndScoresRoundDown() throws Exception {
        seedQuiz(70);

        // 2 of 3 = 66.67 -> 66, below 70: failed, and the correct options stay hidden
        mockMvc.perform(withJson(post(attemptsUrl(courseId, lesson2)), student, twoOfThree()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scorePercentage").value(66))
                .andExpect(jsonPath("$.passed").value(false))
                .andExpect(jsonPath("$.lessonCompleted").value(false))
                .andExpect(jsonPath("$.questions[*].correct", contains(true, false, true)))
                .andExpect(jsonPath("$.questions[1].selectedOptionIds", contains(c.intValue())))
                .andExpect(jsonPath("$.questions[1].correctOptionIds").doesNotExist());

        // Selecting too many options is wrong too; unanswered questions score 0
        mockMvc.perform(withJson(post(attemptsUrl(courseId, lesson2)), student, answers(q2, List.of(c, d, e))))
                .andExpect(jsonPath("$.scorePercentage").value(0))
                .andExpect(jsonPath("$.questions[*].correct", contains(false, false, false)));
        mockMvc.perform(withJson(post(attemptsUrl(courseId, lesson2)), student, answers(q1, List.of(b), q3, List.of(f, g))))
                .andExpect(jsonPath("$.scorePercentage").value(0));
        mockMvc.perform(withJson(post(attemptsUrl(courseId, lesson2)), student, "{\"answers\":[]}"))
                .andExpect(jsonPath("$.scorePercentage").value(0));

        assertThat(count("lesson_progress")).isZero();
    }

    @Test
    void scoreEqualToThePassPercentagePasses() throws Exception {
        seedQuiz(66);

        mockMvc.perform(withJson(post(attemptsUrl(courseId, lesson2)), student, twoOfThree()))
                .andExpect(jsonPath("$.scorePercentage").value(66))
                .andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.questions[1].correctOptionIds", contains(c.intValue(), d.intValue())));
    }

    @Test
    void invalidAnswerIdsAreRejected() throws Exception {
        seedQuiz(70);
        String url = attemptsUrl(courseId, lesson2);

        mockMvc.perform(withJson(post(url), student, answers(999999, List.of(a))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("not part of quiz")));
        mockMvc.perform(withJson(post(url), student, answers(q1, List.of(c))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("is not an option of question " + q1)));
        mockMvc.perform(withJson(post(url), student, answers(q1, List.of(999999))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withJson(post(url), student, answers(q1, List.of(a), q1, List.of(b))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("more than once")));
        mockMvc.perform(withJson(post(url), student, "{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withJson(post(url), student, "{\"answers\":[{\"questionId\":" + q1 + "}]}"))
                .andExpect(status().isBadRequest());

        assertThat(count("quiz_attempts")).isZero();
    }

    @Test
    void draftsUnenrolledAndAnonymousCannotTakeTheQuiz() throws Exception {
        seedQuiz(70);

        mockMvc.perform(get(quizUrl(courseId, lesson2))).andExpect(status().isUnauthorized());
        mockMvc.perform(withJson(post(attemptsUrl(courseId, lesson2)), null, allCorrect())).andExpect(status().isUnauthorized());

        String outsider = studentToken();
        mockMvc.perform(auth(get(quizUrl(courseId, lesson2)), outsider)).andExpect(status().isForbidden());
        mockMvc.perform(withJson(post(attemptsUrl(courseId, lesson2)), outsider, allCorrect())).andExpect(status().isForbidden());

        // Lesson without a quiz
        mockMvc.perform(auth(get(quizUrl(courseId, lesson1)), student)).andExpect(status().isNotFound());

        // Unpublished: hidden even from enrolled students
        mockMvc.perform(auth(post("/api/instructor/courses/" + courseId + "/unpublish"), instructor)).andExpect(status().isOk());
        mockMvc.perform(auth(get(quizUrl(courseId, lesson2)), student)).andExpect(status().isNotFound());
        mockMvc.perform(withJson(post(attemptsUrl(courseId, lesson2)), student, allCorrect())).andExpect(status().isNotFound());

        assertThat(count("quiz_attempts")).isZero();
    }

    // ---- progress ----

    @Test
    void manualCompletionNeedsAPassingAttemptWhenTheLessonHasAQuiz() throws Exception {
        seedQuiz(70);
        String completeUrl = "/api/courses/" + courseId + "/lessons/" + lesson2 + "/complete";

        mockMvc.perform(auth(post(completeUrl), student))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("has a quiz")));

        submit(student, twoOfThree());
        mockMvc.perform(auth(post(completeUrl), student)).andExpect(status().isConflict());

        // Lessons without a quiz are unaffected
        completeLesson(student, courseId, lesson1);

        submit(student, allCorrect());
        mockMvc.perform(auth(post(completeUrl), student)).andExpect(status().isOk());

        // Un-completing stays allowed, and the earlier pass lets the student complete it again by hand
        mockMvc.perform(auth(delete(completeUrl), student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedLessonIds", contains(lesson1.intValue())));
        mockMvc.perform(auth(post(completeUrl), student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true));
    }

    @Test
    void passingTheQuizCompletesTheLessonAndTheCourseWithOneEmail() throws Exception {
        seedQuiz(70);
        completeLesson(student, courseId, lesson1);

        submit(student, twoOfThree());
        mockMvc.perform(auth(get("/api/courses/" + courseId + "/progress"), student))
                .andExpect(jsonPath("$.completed").value(false));
        verify(mailSender, after(300).never()).send(argThat((SimpleMailMessage m) -> isCompletionEmail(m)));

        submit(student, allCorrect());
        mockMvc.perform(auth(get("/api/courses/" + courseId + "/progress"), student))
                .andExpect(jsonPath("$.completedLessonIds", contains(lesson1.intValue(), lesson2.intValue())))
                .andExpect(jsonPath("$.completed").value(true));
        mockMvc.perform(auth(get("/api/courses/" + courseId + "/completion"), student)).andExpect(status().isOk());
        verify(mailSender, timeout(2000).times(1)).send(argThat((SimpleMailMessage m) ->
                isCompletionEmail(m) && studentEmail.equals(m.getTo()[0])));

        // Passing again, or failing afterwards, changes nothing
        submit(student, allCorrect());
        String failed = submit(student, answers());
        assertThat(JsonPath.<Boolean>read(failed, "$.lessonCompleted")).isTrue();
        verify(mailSender, after(300).times(1)).send(argThat((SimpleMailMessage m) -> isCompletionEmail(m)));
        assertThat(count("lesson_progress")).isEqualTo(2);
    }

    // ---- history & analytics ----

    @Test
    void studentsSeeOnlyTheirOwnAttemptHistory() throws Exception {
        seedQuiz(70);
        Long otherCourse = postForId("/api/instructor/courses", instructor, courseJson("Second", true, 0, null));
        Long otherLesson = postForId("/api/instructor/courses/" + otherCourse + "/lessons", instructor, json("title", "Only"));
        markPublished(otherCourse);
        enroll(student, otherCourse);
        String otherQuiz = mockMvc.perform(withJson(put(authorUrl("instructor", otherCourse, otherLesson)), instructor,
                        quizJson(50, question("Q", "SINGLE_CHOICE", option("x", true), option("y", false)))))
                .andReturn().getResponse().getContentAsString();
        Long otherQuestion = id(otherQuiz, "$.questions[0].id");
        Long otherOption = id(otherQuiz, "$.questions[0].options[0].id");

        String first = submit(student, twoOfThree());
        String second = submit(student, allCorrect());
        String third = mockMvc.perform(withJson(post(attemptsUrl(otherCourse, otherLesson)), student,
                        answers(otherQuestion, List.of(otherOption))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(auth(get("/api/my-quiz-attempts"), student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].attemptId", contains(id(third, "$.attemptId").intValue(),
                        id(second, "$.attemptId").intValue(), id(first, "$.attemptId").intValue())));
        mockMvc.perform(auth(get("/api/my-quiz-attempts").param("courseId", courseId.toString()), student))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].quizId").value(quizId))
                .andExpect(jsonPath("$[0].quizTitle").value("Check"))
                .andExpect(jsonPath("$[0].lessonId").value(lesson2))
                .andExpect(jsonPath("$[0].lessonTitle").value("Two"))
                .andExpect(jsonPath("$[0].courseId").value(courseId))
                .andExpect(jsonPath("$[0].scorePercentage").value(100))
                .andExpect(jsonPath("$[0].passed").value(true))
                .andExpect(jsonPath("$[1].scorePercentage").value(66))
                .andExpect(jsonPath("$[1].passed").value(false));

        mockMvc.perform(auth(get("/api/my-quiz-attempts"), studentToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
        mockMvc.perform(get("/api/my-quiz-attempts")).andExpect(status().isUnauthorized());
    }

    @Test
    void analyticsAreComputedFromAllAttempts() throws Exception {
        seedQuiz(70);
        String url = authorUrl("instructor", courseId, lesson2) + "/analytics";

        mockMvc.perform(auth(get(url), instructor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attempts").value(0))
                .andExpect(jsonPath("$.studentsAttempted").value(0))
                .andExpect(jsonPath("$.passRate").value(0.0))
                .andExpect(jsonPath("$.averageScore").value(0.0));

        String second = studentToken();
        enroll(second, courseId);

        submit(student, answers(q1, List.of(a)));  // 33, failed
        submit(student, allCorrect());              // 100, passed
        submit(second, twoOfThree());               // 66, failed

        mockMvc.perform(auth(get(url), instructor))
                .andExpect(jsonPath("$.quizId").value(quizId))
                .andExpect(jsonPath("$.lessonId").value(lesson2))
                .andExpect(jsonPath("$.courseId").value(courseId))
                .andExpect(jsonPath("$.attempts").value(3))
                .andExpect(jsonPath("$.studentsAttempted").value(2))
                .andExpect(jsonPath("$.studentsPassed").value(1))
                .andExpect(jsonPath("$.passRate").value(50.0))
                .andExpect(jsonPath("$.averageScore").value(66.3));
        mockMvc.perform(auth(get(authorUrl("admin", courseId, lesson2) + "/analytics"), admin))
                .andExpect(jsonPath("$.attempts").value(3));
        mockMvc.perform(auth(get(authorUrl("instructor", courseId, lesson1) + "/analytics"), instructor))
                .andExpect(status().isNotFound());
    }

    // ---- deletes ----

    @Test
    void deletingALessonRemovesItsQuizData() throws Exception {
        seedQuiz(70);
        submit(student, allCorrect());

        mockMvc.perform(auth(delete("/api/instructor/courses/" + courseId + "/lessons/" + lesson2), instructor))
                .andExpect(status().isOk());

        assertThat(count("quizzes")).isZero();
        assertThat(count("quiz_questions")).isZero();
        assertThat(count("quiz_options")).isZero();
        assertThat(count("quiz_attempts")).isZero();
        assertThat(count("quiz_attempt_answers")).isZero();
        mockMvc.perform(auth(get("/api/my-quiz-attempts"), student)).andExpect(jsonPath("$", empty()));
    }

    @Test
    void deletingACourseRemovesItsQuizData() throws Exception {
        seedQuiz(70);
        submit(student, twoOfThree());

        mockMvc.perform(auth(delete("/api/admin/courses/" + courseId), admin)).andExpect(status().isOk());

        assertThat(count("courses")).isZero();
        assertThat(count("quizzes")).isZero();
        assertThat(count("quiz_questions")).isZero();
        assertThat(count("quiz_options")).isZero();
        assertThat(count("quiz_attempts")).isZero();
        assertThat(count("quiz_attempt_answers")).isZero();
    }
}
