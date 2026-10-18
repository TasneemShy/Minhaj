package com.example.minhaj.integration;

import com.example.minhaj.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReviewIntegrationTest extends IntegrationTestBase {

    private String admin;
    private Long courseId;

    @BeforeEach
    void seed() throws Exception {
        admin = adminToken();
        courseId = createCourse(admin, "Reviewed", false, 10, null);
    }

    private String reviewsUrl() {
        return "/api/courses/" + courseId + "/reviews";
    }

    private ResultActions expectRating(double average, int count) throws Exception {
        return mockMvc.perform(get("/api/courses/" + courseId))
                .andExpect(jsonPath("$.averageRating").value(average))
                .andExpect(jsonPath("$.reviewCount").value(count));
    }

    @Test
    void averageAndCountAreRecalculatedOnCreateEditDelete() throws Exception {
        String a = studentToken();
        String b = studentToken();
        String c = studentToken();

        review(a, courseId, 5, "a");
        expectRating(5.0, 1);
        Long bReview = review(b, courseId, 4, "b");
        expectRating(4.5, 2);
        review(c, courseId, 4, "c");
        expectRating(4.3, 3); // 13 / 3 rounded to one decimal

        mockMvc.perform(withJson(put(reviewsUrl() + "/" + bReview), b, "{\"rating\":1,\"comment\":\"changed my mind\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(1))
                .andExpect(jsonPath("$.comment").value("changed my mind"));
        expectRating(3.3, 3);

        mockMvc.perform(auth(delete(reviewsUrl() + "/" + bReview), b)).andExpect(status().isOk());
        expectRating(4.5, 2);
    }

    @Test
    void onlyOneReviewPerStudentPerCourse() throws Exception {
        String student = studentToken();
        review(student, courseId, 5, "first");

        mockMvc.perform(withJson(post(reviewsUrl()), student, "{\"rating\":3,\"comment\":\"second\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("You have already reviewed this course."));
        expectRating(5.0, 1);
    }

    @Test
    void ratingMustBeBetweenOneAndFive() throws Exception {
        String student = studentToken();
        mockMvc.perform(withJson(post(reviewsUrl()), student, "{\"rating\":0,\"comment\":\"bad\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.rating").value("Rating must be at least 1"));
        mockMvc.perform(withJson(post(reviewsUrl()), student, "{\"rating\":6,\"comment\":\"too good\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.rating").value("Rating must be at most 5"));
        mockMvc.perform(withJson(post(reviewsUrl()), student, "{\"rating\":3,\"comment\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.comment").value("The review needs a comment"));

        Long id = review(student, courseId, 3, "fine");
        mockMvc.perform(withJson(put(reviewsUrl() + "/" + id), student, "{\"rating\":9,\"comment\":\"edit\"}"))
                .andExpect(status().isBadRequest());
        expectRating(3.0, 1);
    }

    @Test
    void onlyTheAuthorCanEditAndAuthorOrAdminCanDelete() throws Exception {
        String author = studentToken();
        String stranger = studentToken();
        Long id = review(author, courseId, 4, "mine");

        mockMvc.perform(withJson(put(reviewsUrl() + "/" + id), stranger, "{\"rating\":1,\"comment\":\"vandalism\"}"))
                .andExpect(status().isForbidden());
        // Not even an admin can put words in someone's mouth
        mockMvc.perform(withJson(put(reviewsUrl() + "/" + id), admin, "{\"rating\":1,\"comment\":\"moderated\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(auth(delete(reviewsUrl() + "/" + id), stranger))
                .andExpect(status().isForbidden());

        mockMvc.perform(auth(delete(reviewsUrl() + "/" + id), admin)).andExpect(status().isOk());
        expectRating(0.0, 0);
        mockMvc.perform(auth(delete(reviewsUrl() + "/" + id), admin)).andExpect(status().isNotFound());
    }

    @Test
    void reviewMustBelongToTheCourseInThePath() throws Exception {
        String author = studentToken();
        Long id = review(author, courseId, 4, "mine");
        Long otherCourse = createCourse(admin, "Other", false, 1, null);

        mockMvc.perform(withJson(put("/api/courses/" + otherCourse + "/reviews/" + id), author, "{\"rating\":2,\"comment\":\"x\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(auth(delete("/api/courses/" + otherCourse + "/reviews/" + id), author))
                .andExpect(status().isNotFound());
    }

    @Test
    void clientSuppliedIdCannotOverwriteAnotherReview() throws Exception {
        Long victim = review(studentToken(), courseId, 5, "original");
        Long otherCourse = createCourse(admin, "Other", false, 1, null);

        mockMvc.perform(withJson(post("/api/courses/" + otherCourse + "/reviews"), studentToken(),
                        "{\"id\":" + victim + ",\"rating\":1,\"comment\":\"hijack\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(reviewsUrl()))
                .andExpect(jsonPath("$.content[0].comment").value("original"));
    }

    @Test
    void reviewsAreListedNewestFirstPerCourseAndPerUser() throws Exception {
        String studentEmail = uniqueEmail("reviewer");
        String student = studentToken(studentEmail);
        review(studentToken(), courseId, 5, "older");
        review(student, courseId, 3, "newer");

        mockMvc.perform(get(reviewsUrl()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].comment", contains("newer", "older")));
        mockMvc.perform(get("/api/users/" + userId(studentEmail) + "/reviews").header("Authorization", "Bearer " + student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].comment", contains("newer")));
        mockMvc.perform(get(reviewsUrl() + "?size=0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/courses/999999/reviews")).andExpect(status().isNotFound());
    }
}
