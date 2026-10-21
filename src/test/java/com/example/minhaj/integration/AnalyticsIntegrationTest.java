package com.example.minhaj.integration;

import com.example.minhaj.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnalyticsIntegrationTest extends IntegrationTestBase {

    private String admin;
    private Long courseA;
    private Long courseB;
    private Long courseC;
    private Long courseD;

    // 4 users (1 admin, 1 instructor, 2 students), 3 categories (one empty), 4 courses (2 free, one uncategorized),
    // 3 lessons, 3 enrollments, 3 reviews (avg 4.0), 4 completed lessons.
    @BeforeEach
    void seed() throws Exception {
        admin = adminToken();
        instructorToken(uniqueEmail("instructor"));
        String s1 = studentToken();
        String s2 = studentToken();

        Long programming = createCategory(admin, "Programming");
        Long design = createCategory(admin, "Design");
        createCategory(admin, "Empty");

        courseA = createPublishedCourse(admin, "A", true, 0, programming);
        courseB = createPublishedCourse(admin, "B", false, 20, programming);
        courseC = createPublishedCourse(admin, "C", false, 30, design);
        courseD = createPublishedCourse(admin, "D", true, 0, null);

        Long a1 = addLesson(admin, courseA, "A1");
        Long a2 = addLesson(admin, courseA, "A2");
        Long b1 = addLesson(admin, courseB, "B1");

        enroll(s1, courseA);
        enroll(s1, courseB);
        enroll(s2, courseA);

        review(s1, courseA, 5, "s1 A");
        review(s2, courseA, 4, "s2 A");
        review(s1, courseB, 3, "s1 B");

        completeLesson(s1, courseA, a1);
        completeLesson(s1, courseB, b1);
        completeLesson(s2, courseA, a1);
        completeLesson(s2, courseA, a2);
    }

    @Test
    void overviewMatchesSeededData() throws Exception {
        mockMvc.perform(auth(get("/api/admin/analytics/overview"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(4))
                .andExpect(jsonPath("$.usersByRole.ADMIN").value(1))
                .andExpect(jsonPath("$.usersByRole.INSTRUCTOR").value(1))
                .andExpect(jsonPath("$.usersByRole.STUDENT").value(2))
                .andExpect(jsonPath("$.totalCourses").value(4))
                .andExpect(jsonPath("$.freeCourses").value(2))
                .andExpect(jsonPath("$.paidCourses").value(2))
                .andExpect(jsonPath("$.totalCategories").value(3))
                .andExpect(jsonPath("$.totalLessons").value(3))
                .andExpect(jsonPath("$.totalEnrollments").value(3))
                .andExpect(jsonPath("$.totalReviews").value(3))
                .andExpect(jsonPath("$.averageRating").value(4.0))
                .andExpect(jsonPath("$.completedLessons").value(4));
    }

    @Test
    void topCoursesByEachOrdering() throws Exception {
        mockMvc.perform(auth(get("/api/admin/analytics/top-courses"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", contains("A", "B", "C", "D")))
                .andExpect(jsonPath("$[0].enrollmentCount").value(2))
                .andExpect(jsonPath("$[0].categoryName").value("Programming"));

        mockMvc.perform(auth(get("/api/admin/analytics/top-courses?by=rating&limit=2"), admin))
                .andExpect(jsonPath("$[*].title", contains("A", "B")))
                .andExpect(jsonPath("$[0].averageRating").value(4.5));

        mockMvc.perform(auth(get("/api/admin/analytics/top-courses?by=reviews&limit=1"), admin))
                .andExpect(jsonPath("$[*].title", contains("A")))
                .andExpect(jsonPath("$[0].reviewCount").value(2));

        mockMvc.perform(auth(get("/api/admin/analytics/top-courses?by=price"), admin)).andExpect(status().isBadRequest());
        mockMvc.perform(auth(get("/api/admin/analytics/top-courses?limit=51"), admin)).andExpect(status().isBadRequest());
    }

    @Test
    void categoryStatsIncludeEmptyAndUncategorized() throws Exception {
        mockMvc.perform(auth(get("/api/admin/analytics/categories"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].categoryName", contains("Design", "Empty", "Programming", "Uncategorized")))
                .andExpect(jsonPath("$[0].courseCount").value(1))
                .andExpect(jsonPath("$[0].enrollmentCount").value(0))
                .andExpect(jsonPath("$[1].courseCount").value(0))
                .andExpect(jsonPath("$[2].courseCount").value(2))
                .andExpect(jsonPath("$[2].enrollmentCount").value(3))
                .andExpect(jsonPath("$[2].averageRating").value(4.0))
                .andExpect(jsonPath("$[3].categoryId").doesNotExist())
                .andExpect(jsonPath("$[3].courseCount").value(1));
    }

    @Test
    void singleCourseStats() throws Exception {
        // A: 2 students x 2 lessons = 4 possible completions, 3 done
        mockMvc.perform(auth(get("/api/admin/analytics/courses/" + courseA), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrollmentCount").value(2))
                .andExpect(jsonPath("$.lessonCount").value(2))
                .andExpect(jsonPath("$.reviewCount").value(2))
                .andExpect(jsonPath("$.averageRating").value(4.5))
                .andExpect(jsonPath("$.completedLessons").value(3))
                .andExpect(jsonPath("$.averageCompletionPercentage").value(75.0));

        mockMvc.perform(auth(get("/api/admin/analytics/courses/" + courseC), admin))
                .andExpect(jsonPath("$.enrollmentCount").value(0))
                .andExpect(jsonPath("$.averageRating").value(0.0))
                .andExpect(jsonPath("$.averageCompletionPercentage").value(0.0));

        mockMvc.perform(auth(get("/api/admin/analytics/courses/" + courseD), admin))
                .andExpect(jsonPath("$.categoryName").doesNotExist())
                .andExpect(jsonPath("$.completions").value(0))
                .andExpect(jsonPath("$.completionRate").value(0.0));
        mockMvc.perform(auth(get("/api/admin/analytics/courses/999999"), admin)).andExpect(status().isNotFound());
    }

    @Test
    void courseCompletionsCountStudentsWhoFinishedEveryLesson() throws Exception {
        // A: s2 completed both lessons, s1 only one -> 1 of 2 enrolled
        mockMvc.perform(auth(get("/api/admin/analytics/courses/" + courseA), admin))
                .andExpect(jsonPath("$.completions").value(1))
                .andExpect(jsonPath("$.completionRate").value(50.0));

        // B: its only student completed its only lesson
        mockMvc.perform(auth(get("/api/admin/analytics/courses/" + courseB), admin))
                .andExpect(jsonPath("$.completions").value(1))
                .andExpect(jsonPath("$.completionRate").value(100.0));

        // C: nobody enrolled
        mockMvc.perform(auth(get("/api/admin/analytics/courses/" + courseC), admin))
                .andExpect(jsonPath("$.completions").value(0))
                .andExpect(jsonPath("$.completionRate").value(0.0));

        // A third student in A with no progress lowers the rate to 1 of 3
        enroll(studentToken(), courseA);
        mockMvc.perform(auth(get("/api/admin/analytics/courses/" + courseA), admin))
                .andExpect(jsonPath("$.completions").value(1))
                .andExpect(jsonPath("$.completionRate").value(33.3));

        // A new lesson in B means nobody has completed it any more
        addLesson(admin, courseB, "B2");
        mockMvc.perform(auth(get("/api/admin/analytics/courses/" + courseB), admin))
                .andExpect(jsonPath("$.completions").value(0))
                .andExpect(jsonPath("$.completionRate").value(0.0));
    }

    @Test
    void courseWithoutLessonsHasNoCompletions() throws Exception {
        enroll(studentToken(), courseD);

        mockMvc.perform(auth(get("/api/admin/analytics/courses/" + courseD), admin))
                .andExpect(jsonPath("$.enrollmentCount").value(1))
                .andExpect(jsonPath("$.completions").value(0))
                .andExpect(jsonPath("$.completionRate").value(0.0));
    }

    @Test
    void instructorsCannotSeeAnalytics() throws Exception {
        mockMvc.perform(auth(get("/api/admin/analytics/courses/" + courseB), instructorToken(uniqueEmail("nosy"))))
                .andExpect(status().isForbidden());
    }
}
