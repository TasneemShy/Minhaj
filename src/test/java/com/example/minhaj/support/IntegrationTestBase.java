package com.example.minhaj.support;

import com.example.minhaj.models.User;
import com.example.minhaj.repositories.UserRepository;
import com.example.minhaj.services.RateLimiter;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Base for end-to-end tests: the real app (security, JWT, JPA, Flyway) against the Testcontainers Postgres.
// Every subclass shares one Spring context and one container; tables are emptied before each test.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTestBase {

    protected static final String PASSWORD = "secret123";

    private static final AtomicInteger EMAIL_COUNTER = new AtomicInteger();

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected RateLimiter rateLimiter;

    // No real SMTP in tests
    @MockitoBean
    protected JavaMailSender mailSender;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE users, categories, courses, lessons, reviews, student_courses, "
                + "password_reset_token, lesson_progress, refresh_tokens RESTART IDENTITY CASCADE");
        rateLimiter.reset();
    }

    // ---- users & tokens ----

    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + EMAIL_COUNTER.incrementAndGet() + "@test.com";
    }

    protected String register(String name, String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("name", name, "email", email, "password", password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    protected String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", email, "password", password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    // Full login response: accessToken, refreshToken, expiresIn, role, token.
    protected String loginResponse(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", email, "password", password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    protected String studentToken(String email) throws Exception {
        return register("Student " + email, email, PASSWORD);
    }

    protected String studentToken() throws Exception {
        return studentToken(uniqueEmail("student"));
    }

    // Registers a user, sets the role directly in the database, then logs in so the JWT carries it.
    protected String tokenWithRole(String email, String role) throws Exception {
        register(role + " " + email, email, PASSWORD);
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setRole(role);
        userRepository.save(user);
        return login(email, PASSWORD);
    }

    protected String adminToken() throws Exception {
        return tokenWithRole(uniqueEmail("admin"), "ADMIN");
    }

    protected String instructorToken(String email) throws Exception {
        return tokenWithRole(email, "INSTRUCTOR");
    }

    protected Long userId(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    // ---- requests ----

    protected static MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder request, String token, String body) {
        request.contentType(MediaType.APPLICATION_JSON).content(body);
        return token == null ? request : auth(request, token);
    }

    protected static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    protected Long postForId(String url, String token, String body) throws Exception {
        String response = mockMvc.perform(withJson(post(url), token, body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    // ---- seeding through the API ----

    protected Long createCategory(String adminToken, String name) throws Exception {
        return postForId("/api/admin/categories", adminToken, json("name", name));
    }

    protected Long createCourse(String adminToken, String title, boolean free, double price, Long categoryId) throws Exception {
        return postForId("/api/admin/courses", adminToken, courseJson(title, free, price, categoryId));
    }

    protected Long addLesson(String token, Long courseId, String title) throws Exception {
        return postForId("/api/admin/courses/" + courseId + "/lessons", token, json("title", title));
    }

    protected void enroll(String token, Long courseId) throws Exception {
        mockMvc.perform(auth(post("/api/courses/" + courseId + "/enroll"), token))
                .andExpect(status().isOk());
    }

    protected Long review(String token, Long courseId, int rating, String comment) throws Exception {
        return postForId("/api/courses/" + courseId + "/reviews", token,
                "{\"rating\":" + rating + ",\"comment\":\"" + comment + "\"}");
    }

    protected void completeLesson(String token, Long courseId, Long lessonId) throws Exception {
        mockMvc.perform(auth(post("/api/courses/" + courseId + "/lessons/" + lessonId + "/complete"), token))
                .andExpect(status().isOk());
    }

    // ---- JSON ----

    protected static String courseJson(String title, boolean free, double price, Long categoryId) {
        return "{\"title\":\"" + title + "\",\"description\":\"About " + title + "\",\"free\":" + free
                + ",\"price\":" + price + ",\"categoryId\":" + categoryId + "}";
    }

    // json("a", "1", "b", "2") -> {"a":"1","b":"2"}; values are plain strings without quotes to escape.
    protected static String json(String... keyValues) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < keyValues.length; i += 2) {
            if (i > 0) sb.append(',');
            sb.append('"').append(keyValues[i]).append("\":\"").append(keyValues[i + 1]).append('"');
        }
        return sb.append('}').toString();
    }

    protected long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }
}
