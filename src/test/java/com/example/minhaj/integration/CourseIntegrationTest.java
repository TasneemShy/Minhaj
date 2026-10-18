package com.example.minhaj.integration;

import com.example.minhaj.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseIntegrationTest extends IntegrationTestBase {

    private String admin;
    private Long programming;
    private Long design;

    @BeforeEach
    void seedCatalog() throws Exception {
        admin = adminToken();
        programming = createCategory(admin, "Programming");
        design = createCategory(admin, "Design");

        Long java = createCourse(admin, "Java Basics", true, 0, programming);
        Long spring = createCourse(admin, "Spring Boot Mastery", false, 49.99, programming);
        createCourse(admin, "Advanced Java", false, 120, programming);
        createCourse(admin, "Figma for Beginners", false, 19, design);

        // Ratings: Java Basics 5.0, Spring Boot Mastery 3.0
        review(studentToken(), java, 5, "great");
        review(studentToken(), spring, 3, "ok");
    }

    private ResultActions list(String query) throws Exception {
        return mockMvc.perform(get("/api/courses" + query));
    }

    @Test
    void listsAllCoursesPagedWithoutLogin() throws Exception {
        list("?size=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void filtersByCategory() throws Exception {
        list("?categoryId=" + design)
                .andExpect(jsonPath("$.content[*].title", contains("Figma for Beginners")))
                .andExpect(jsonPath("$.content[0].categoryName").value("Design"));
    }

    @Test
    void filtersByFree() throws Exception {
        list("?free=true").andExpect(jsonPath("$.content[*].title", contains("Java Basics")));
        list("?free=false").andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void filtersByPriceRange() throws Exception {
        list("?minPrice=20&maxPrice=120&sortBy=price&direction=asc")
                .andExpect(jsonPath("$.content[*].title", contains("Spring Boot Mastery", "Advanced Java")));
    }

    @Test
    void filtersByMinRating() throws Exception {
        list("?minRating=3").andExpect(jsonPath("$.content[*].title",
                containsInAnyOrder("Java Basics", "Spring Boot Mastery")));
        list("?minRating=4").andExpect(jsonPath("$.content[*].title", contains("Java Basics")));
    }

    @Test
    void searchIsCaseInsensitiveOnTitle() throws Exception {
        list("?search=JAVA").andExpect(jsonPath("$.content[*].title",
                containsInAnyOrder("Java Basics", "Advanced Java")));
    }

    @Test
    void combinesFiltersAndSorts() throws Exception {
        list("?categoryId=" + programming + "&free=false&sortBy=title&direction=asc")
                .andExpect(jsonPath("$.content[*].title", contains("Advanced Java", "Spring Boot Mastery")));
        list("?sortBy=averageRating&direction=desc&size=1")
                .andExpect(jsonPath("$.content[0].title").value("Java Basics"));
    }

    @Test
    void rejectsUnknownSortField() throws Exception {
        list("?sortBy=password")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("sortBy must be one of")));
    }

    @Test
    void getCourseByIdAnd404() throws Exception {
        Long id = createCourse(admin, "Lonely", false, 5, null);
        mockMvc.perform(get("/api/courses/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Lonely"));
        mockMvc.perform(get("/api/courses/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail", containsString("999999")));
    }

    @Test
    void adminCrud() throws Exception {
        // Create: a free course always has price 0
        Long id = postForId("/api/admin/courses", admin, courseJson("New Course", true, 30, design));
        mockMvc.perform(get("/api/courses/" + id))
                .andExpect(jsonPath("$.price").value(0.0))
                .andExpect(jsonPath("$.free").value(true))
                .andExpect(jsonPath("$.category.id").value(design));

        // Update
        mockMvc.perform(withJson(put("/api/admin/courses/" + id), admin, courseJson("Renamed", false, 15, programming)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed"))
                .andExpect(jsonPath("$.price").value(15.0))
                .andExpect(jsonPath("$.category.name").value("Programming"));

        // Delete
        mockMvc.perform(auth(delete("/api/admin/courses/" + id), admin)).andExpect(status().isOk());
        mockMvc.perform(get("/api/courses/" + id)).andExpect(status().isNotFound());
        mockMvc.perform(auth(delete("/api/admin/courses/" + id), admin)).andExpect(status().isNotFound());
    }

    @Test
    void createCourseValidatesBodyAndCategory() throws Exception {
        mockMvc.perform(withJson(post("/api/admin/courses"), admin, "{\"title\":\"\",\"price\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").value("Course title cannot be empty"))
                .andExpect(jsonPath("$.errors.price").value("Price cannot be negative"));

        mockMvc.perform(withJson(post("/api/admin/courses"), admin, courseJson("Bad category", false, 1, 424242L)))
                .andExpect(status().isNotFound());
    }

    @Test
    void studentsAreForbiddenOnAdminEndpoints() throws Exception {
        String student = studentToken();
        Long courseId = createCourse(admin, "Protected", false, 1, null);

        mockMvc.perform(withJson(post("/api/admin/courses"), student, courseJson("Hack", false, 1, null)))
                .andExpect(status().isForbidden());
        mockMvc.perform(auth(delete("/api/admin/courses/" + courseId), student)).andExpect(status().isForbidden());
        mockMvc.perform(withJson(post("/api/admin/categories"), student, json("name", "Hack"))).andExpect(status().isForbidden());
        mockMvc.perform(auth(get("/api/admin/users"), student)).andExpect(status().isForbidden());
        mockMvc.perform(auth(get("/api/admin/analytics/overview"), student)).andExpect(status().isForbidden());
    }

    @Test
    void studentOnAdminEndpointGets403ProblemDetail() throws Exception {
        mockMvc.perform(auth(get("/api/admin/users"), studentToken()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.detail").value("Access Denied"))
                .andExpect(jsonPath("$.instance").value("/api/admin/users"));
    }

    @Test
    void anonymousOnAdminEndpointGets401() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void missingCourseIs404ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/courses/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("Course not found with ID: 999999"))
                .andExpect(jsonPath("$.instance").value("/api/courses/999999"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void categoriesAreListedPubliclyAndNamesAreUnique() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("Design", "Programming")));

        mockMvc.perform(withJson(post("/api/admin/categories"), admin, json("name", "design")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("already exists")));

        mockMvc.perform(withJson(put("/api/admin/categories/" + design), admin, json("name", "UI Design", "description", "Pixels")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("UI Design"));
    }

    @Test
    void categoryDeleteIsRefusedWhileInUse() throws Exception {
        mockMvc.perform(auth(delete("/api/admin/categories/" + design), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("still has courses")));

        Long empty = createCategory(admin, "Empty");
        mockMvc.perform(auth(delete("/api/admin/categories/" + empty), admin)).andExpect(status().isOk());
        mockMvc.perform(auth(delete("/api/admin/categories/" + empty), admin)).andExpect(status().isNotFound());
    }

    @Test
    void lessonsAreListedPerCourseAndCountedOnTheCourse() throws Exception {
        Long id = createCourse(admin, "With lessons", false, 10, null);
        addLesson(admin, id, "Intro");
        addLesson(admin, id, "Deep dive");

        mockMvc.perform(get("/api/courses/" + id + "/lessons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", containsInAnyOrder("Intro", "Deep dive")));
        mockMvc.perform(get("/api/courses/" + id)).andExpect(jsonPath("$.lessonCount").value(2));
        mockMvc.perform(get("/api/courses/999999/lessons")).andExpect(status().isNotFound());
    }

    @Test
    void enrollAndListMyCourses() throws Exception {
        String student = studentToken();
        Long id = createCourse(admin, "Enrol me", false, 10, null);

        mockMvc.perform(auth(get("/api/my-courses"), student))
                .andExpect(jsonPath("$.message").value("You haven't enrolled in any courses yet."));

        enroll(student, id);
        enroll(student, id); // idempotent

        mockMvc.perform(auth(get("/api/my-courses"), student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", contains("Enrol me")));
        mockMvc.perform(post("/api/courses/" + id + "/enroll"))
                .andExpect(status().isUnauthorized());
    }
}
