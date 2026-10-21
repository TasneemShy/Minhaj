package com.example.minhaj.integration;

import com.example.minhaj.support.IntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Draft/published lifecycle: who can see a draft, who can publish it, and what students can do with it.
class CourseLifecycleIntegrationTest extends IntegrationTestBase {

    private String admin;
    private String ownerEmail;
    private String owner;
    private Long draftId;

    @BeforeEach
    void seed() throws Exception {
        admin = adminToken();
        ownerEmail = uniqueEmail("owner");
        owner = instructorToken(ownerEmail);
        draftId = postForId("/api/instructor/courses", owner, courseJson("Draft course", false, 10, null));
        postForId("/api/instructor/courses/" + draftId + "/lessons", owner, json("title", "Lesson 1"));
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String token) {
        return token == null ? request : auth(request, token);
    }

    @Test
    void newCoursesStartAsDrafts() throws Exception {
        mockMvc.perform(auth(get("/api/courses/" + draftId), owner))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.publishedAt").value(nullValue()));

        mockMvc.perform(withJson(post("/api/admin/courses"), admin, courseJson("Admin's", false, 1, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void draftsAreHiddenFromStudentsAndAnonymousUsers() throws Exception {
        String student = studentToken();
        for (String token : new String[]{null, student, instructorToken(uniqueEmail("other"))}) {
            mockMvc.perform(as(get("/api/courses"), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
            mockMvc.perform(as(get("/api/courses/" + draftId), token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Course not found with ID: " + draftId));
            mockMvc.perform(as(get("/api/courses/" + draftId + "/lessons"), token))
                    .andExpect(status().isNotFound());
            mockMvc.perform(as(get("/api/courses/" + draftId + "/reviews"), token))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void ownerAndAdminCanFetchDraftButCatalogStillHidesIt() throws Exception {
        for (String token : new String[]{owner, admin}) {
            mockMvc.perform(auth(get("/api/courses/" + draftId), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("Draft course"));
            mockMvc.perform(auth(get("/api/courses/" + draftId + "/lessons"), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].title").value("Lesson 1"));
            mockMvc.perform(auth(get("/api/courses/" + draftId + "/reviews"), token))
                    .andExpect(status().isOk());
            mockMvc.perform(auth(get("/api/courses"), token))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
    }

    @Test
    void publishMakesCourseVisibleAndKeepsFirstPublishTime() throws Exception {
        String body = mockMvc.perform(auth(post("/api/instructor/courses/" + draftId + "/publish"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").value(notNullValue()))
                .andReturn().getResponse().getContentAsString();
        String firstPublishedAt = JsonPath.read(body, "$.publishedAt");

        mockMvc.perform(get("/api/courses/" + draftId)).andExpect(status().isOk());
        mockMvc.perform(get("/api/courses/" + draftId + "/lessons")).andExpect(status().isOk());
        mockMvc.perform(get("/api/courses")).andExpect(jsonPath("$.content[0].id").value(draftId));

        // Unpublish hides it again; re-publishing keeps the original publish time
        mockMvc.perform(auth(post("/api/instructor/courses/" + draftId + "/unpublish"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
        mockMvc.perform(get("/api/courses/" + draftId)).andExpect(status().isNotFound());

        mockMvc.perform(auth(post("/api/admin/courses/" + draftId + "/publish"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").value(firstPublishedAt));
        mockMvc.perform(auth(post("/api/admin/courses/" + draftId + "/unpublish"), admin))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void publishingWithoutLessonsIsRejected() throws Exception {
        Long empty = postForId("/api/instructor/courses", owner, courseJson("Empty", false, 1, null));

        mockMvc.perform(auth(post("/api/instructor/courses/" + empty + "/publish"), owner))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("no lessons")));
        mockMvc.perform(auth(post("/api/admin/courses/" + empty + "/publish"), admin))
                .andExpect(status().isBadRequest());
        mockMvc.perform(auth(get("/api/courses/" + empty), owner))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void onlyOwnerOrAdminCanPublish() throws Exception {
        String other = instructorToken(uniqueEmail("other"));
        String student = studentToken();

        for (String action : new String[]{"publish", "unpublish"}) {
            mockMvc.perform(auth(post("/api/instructor/courses/" + draftId + "/" + action), other))
                    .andExpect(status().isForbidden());
            mockMvc.perform(auth(post("/api/instructor/courses/" + draftId + "/" + action), student))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/instructor/courses/" + draftId + "/" + action))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(auth(post("/api/admin/courses/" + draftId + "/" + action), owner))
                    .andExpect(status().isForbidden());
            mockMvc.perform(auth(post("/api/admin/courses/999999/" + action), admin))
                    .andExpect(status().isNotFound());
        }

        // Admins may use the instructor endpoint on any course
        mockMvc.perform(auth(post("/api/instructor/courses/" + draftId + "/publish"), admin))
                .andExpect(status().isOk());
    }

    @Test
    void enrollingInOrReviewingADraftIsRejected() throws Exception {
        String student = studentToken();

        mockMvc.perform(auth(post("/api/courses/" + draftId + "/enroll"), student))
                .andExpect(status().isNotFound());
        mockMvc.perform(withJson(post("/api/courses/" + draftId + "/reviews"), student, "{\"rating\":5,\"comment\":\"Hi\"}"))
                .andExpect(status().isNotFound());
        // Not even the owner or an admin enrolls in a draft
        mockMvc.perform(auth(post("/api/courses/" + draftId + "/enroll"), admin))
                .andExpect(status().isNotFound());
        assertCount("student_courses", 0);
        assertCount("reviews", 0);
    }

    @Test
    void enrolledStudentsKeepUnpublishedCourse() throws Exception {
        String studentEmail = uniqueEmail("student");
        String student = studentToken(studentEmail);
        mockMvc.perform(auth(post("/api/instructor/courses/" + draftId + "/publish"), owner)).andExpect(status().isOk());
        enroll(student, draftId);
        Long lessonId = ((Number) JsonPath.read(mockMvc.perform(get("/api/courses/" + draftId + "/lessons"))
                .andReturn().getResponse().getContentAsString(), "$[0].id")).longValue();
        completeLesson(student, draftId, lessonId);

        mockMvc.perform(auth(post("/api/instructor/courses/" + draftId + "/unpublish"), owner)).andExpect(status().isOk());

        mockMvc.perform(auth(get("/api/my-courses"), student))
                .andExpect(jsonPath("$[0].id").value(draftId))
                .andExpect(jsonPath("$[0].status").value("DRAFT"));
        mockMvc.perform(auth(get("/api/courses/" + draftId + "/progress"), student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedLessons").value(1));
        // ... but the course itself is hidden and closed to new reviews
        mockMvc.perform(auth(get("/api/courses/" + draftId), student)).andExpect(status().isNotFound());
        mockMvc.perform(withJson(post("/api/courses/" + draftId + "/reviews"), student, "{\"rating\":4,\"comment\":\"Ok\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void instructorListIncludesDraftsAndFiltersByStatus() throws Exception {
        Long published = postForId("/api/instructor/courses", owner, courseJson("Published one", false, 1, null));
        postForId("/api/instructor/courses/" + published + "/lessons", owner, json("title", "L"));
        mockMvc.perform(auth(post("/api/instructor/courses/" + published + "/publish"), owner)).andExpect(status().isOk());

        mockMvc.perform(auth(get("/api/instructor/courses"), owner))
                .andExpect(jsonPath("$.content[*].title", containsInAnyOrder("Draft course", "Published one")));
        mockMvc.perform(auth(get("/api/instructor/courses?status=DRAFT"), owner))
                .andExpect(jsonPath("$.content[*].title", containsInAnyOrder("Draft course")));
        mockMvc.perform(auth(get("/api/instructor/courses?status=PUBLISHED"), owner))
                .andExpect(jsonPath("$.content[*].title", containsInAnyOrder("Published one")));
        mockMvc.perform(auth(get("/api/instructor/courses?status=ARCHIVED"), owner))
                .andExpect(status().isBadRequest());
    }

    @Test
    void analyticsOverviewCountsPublishedAndDrafts() throws Exception {
        createPublishedCourse(admin, "Live", false, 1, null);

        mockMvc.perform(auth(get("/api/admin/analytics/overview"), admin))
                .andExpect(jsonPath("$.totalCourses").value(2))
                .andExpect(jsonPath("$.publishedCourses").value(1))
                .andExpect(jsonPath("$.draftCourses").value(1));
    }

    private void assertCount(String table, long expected) {
        org.assertj.core.api.Assertions.assertThat(count(table)).isEqualTo(expected);
    }
}
