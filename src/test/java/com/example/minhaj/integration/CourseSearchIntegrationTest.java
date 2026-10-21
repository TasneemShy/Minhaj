package com.example.minhaj.integration;

import com.example.minhaj.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Full-text search on GET /api/courses?search= (V5 search_vector, CourseSpecifications.matchesSearch).
class CourseSearchIntegrationTest extends IntegrationTestBase {

    private static final String SPRING_BASICS = "Spring Boot Basics";
    private static final String JAVA = "Java Fundamentals";
    private static final String GARDEN = "Boot camp for Spring gardeners";
    private static final String ARABIC = "مقدمة في البرمجة";
    private static final String KOTLIN = "Kotlin Coroutines";

    private String admin;
    private Long programming;
    private Long design;

    // Created oldest first, so the default createdAt-desc order is the reverse of this list; the relevance
    // test relies on that to show ranking, not creation time, decides the order.
    @BeforeEach
    void seedCatalog() throws Exception {
        admin = adminToken();
        programming = createCategory(admin, "Programming");
        design = createCategory(admin, "Design");

        publish(KOTLIN, "Asynchronous programming on the JVM", 50, programming);
        publish(ARABIC, "تعلم أساسيات البرمجة بلغة بايثون", 0, programming);
        publish(GARDEN, "Plant flowers in the spring", 0, design);
        publish(JAVA, "Variables, loops and classes: a first step before Spring Boot", 80, programming);
        publish(SPRING_BASICS, "Build web services in Java or Kotlin", 30, programming);
        postForId("/api/admin/courses", admin,
                courseJson("Spring Security Deep Dive", "Secure Spring Boot apps", 40, programming)); // stays a draft
    }

    private void publish(String title, String description, double price, Long categoryId) throws Exception {
        markPublished(postForId("/api/admin/courses", admin, courseJson(title, description, price, categoryId)));
    }

    private static String courseJson(String title, String description, double price, Long categoryId) {
        return "{\"title\":\"" + title + "\",\"description\":\"" + description + "\",\"free\":" + (price == 0)
                + ",\"price\":" + price + ",\"categoryId\":" + categoryId + "}";
    }

    private ResultActions search(String text, String... otherParams) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/courses").param("search", text);
        for (int i = 0; i < otherParams.length; i += 2) {
            request.param(otherParams[i], otherParams[i + 1]);
        }
        return mockMvc.perform(request).andExpect(status().isOk());
    }

    @Test
    void everyWordMustMatchInTitleOrDescription() throws Exception {
        search("spring boot").andExpect(jsonPath("$.content[*].title", containsInAnyOrder(SPRING_BASICS, JAVA, GARDEN)));
        search("spring flowers").andExpect(jsonPath("$.content[*].title", contains(GARDEN)));
    }

    @Test
    void matchesWordsOnlyInTheDescription() throws Exception {
        search("VARIABLES").andExpect(jsonPath("$.content[*].title", contains(JAVA)));
    }

    @Test
    void partialWordsMatchAsPrefixes() throws Exception {
        search("corout").andExpect(jsonPath("$.content[*].title", contains(KOTLIN)));
        search("spring bo").andExpect(jsonPath("$.content[*].title", containsInAnyOrder(SPRING_BASICS, JAVA, GARDEN)));
        search("asynchron").andExpect(jsonPath("$.content[*].title", contains(KOTLIN)));
    }

    @Test
    void matchesArabicText() throws Exception {
        search("البرمجة").andExpect(jsonPath("$.content[*].title", contains(ARABIC)));
        search("بايث").andExpect(jsonPath("$.content[*].title", contains(ARABIC)));
        search("مقدمة البرمجة").andExpect(jsonPath("$.content[*].title", contains(ARABIC)));
    }

    @Test
    void quotedPhraseNeedsTheWordsTogetherInOrder() throws Exception {
        search("\"spring boot\"").andExpect(jsonPath("$.content[*].title", containsInAnyOrder(SPRING_BASICS, JAVA)));
        search("\"boot spring\"").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void minusExcludesAWord() throws Exception {
        search("spring -gardeners").andExpect(jsonPath("$.content[*].title", containsInAnyOrder(SPRING_BASICS, JAVA)));
        search("spring -java").andExpect(jsonPath("$.content[*].title", contains(GARDEN)));
    }

    @Test
    void orMatchesEitherWord() throws Exception {
        search("coroutines or البرمجة").andExpect(jsonPath("$.content[*].title", containsInAnyOrder(KOTLIN, ARABIC)));
    }

    @Test
    void noMatchIsAnEmptyPage() throws Exception {
        search("haskell")
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void combinesWithCategoryAndPriceFilters() throws Exception {
        search("spring", "categoryId", programming.toString())
                .andExpect(jsonPath("$.content[*].title", containsInAnyOrder(SPRING_BASICS, JAVA)));
        search("spring", "categoryId", design.toString())
                .andExpect(jsonPath("$.content[*].title", contains(GARDEN)));
        search("spring", "minPrice", "50").andExpect(jsonPath("$.content[*].title", contains(JAVA)));
        search("spring", "categoryId", programming.toString(), "maxPrice", "40")
                .andExpect(jsonPath("$.content[*].title", contains(SPRING_BASICS)));
        search("spring", "free", "true").andExpect(jsonPath("$.content[*].title", contains(GARDEN)));
    }

    @Test
    void draftsAreNeverReturned() throws Exception {
        search("security").andExpect(jsonPath("$.totalElements").value(0));
        search("secure").andExpect(jsonPath("$.totalElements").value(0));
        search("spring").andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void titleMatchesRankAboveDescriptionMatches() throws Exception {
        // Kotlin is in KOTLIN's title and only in SPRING_BASICS's description; SPRING_BASICS is newer.
        search("kotlin").andExpect(jsonPath("$.content[*].title", contains(KOTLIN, SPRING_BASICS)));
        search("kotlin", "sortBy", "relevance").andExpect(jsonPath("$.content[*].title", contains(KOTLIN, SPRING_BASICS)));
        search("kotlin", "sortBy", "relevance", "direction", "asc")
                .andExpect(jsonPath("$.content[*].title", contains(SPRING_BASICS, KOTLIN)));
        search("java").andExpect(jsonPath("$.content[*].title", contains(JAVA, SPRING_BASICS)));
    }

    @Test
    void relevanceOrderIsKeptAcrossPages() throws Exception {
        search("kotlin", "size", "1", "page", "0")
                .andExpect(jsonPath("$.content[*].title", contains(KOTLIN)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
        search("kotlin", "size", "1", "page", "1").andExpect(jsonPath("$.content[*].title", contains(SPRING_BASICS)));
    }

    @Test
    void explicitSortReplacesRelevance() throws Exception {
        search("kotlin", "sortBy", "title", "direction", "desc")
                .andExpect(jsonPath("$.content[*].title", contains(SPRING_BASICS, KOTLIN)));
        search("spring", "sortBy", "price", "direction", "asc")
                .andExpect(jsonPath("$.content[*].title", contains(GARDEN, SPRING_BASICS, JAVA)));
    }

    @Test
    void relevanceWithoutSearchSortsNewestFirst() throws Exception {
        mockMvc.perform(get("/api/courses").param("sortBy", "relevance").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title", contains(SPRING_BASICS, JAVA)))
                .andExpect(jsonPath("$.totalElements").value(5));
    }

    @Test
    void blankSearchListsEverything() throws Exception {
        search("   ").andExpect(jsonPath("$.totalElements").value(5));
    }

    @ParameterizedTest
    @ValueSource(strings = {"') OR 1=1 --", ":*&|!", "'; DROP TABLE courses; --", "\\", "!!!", "&", "\"",
            "spring:* | 'x", "a & | b", "(((", "-", "\"unclosed phrase", "java\0", "%_%"})
    void hostileInputNeverFailsOrLeaksRows(String input) throws Exception {
        String body = search(input).andReturn().getResponse().getContentAsString();
        assertThat(count("courses")).isEqualTo(6);
        // No input may widen the search to the whole catalog or reach the draft.
        assertThat(body).doesNotContain("Spring Security Deep Dive");
        if (!input.startsWith("java")) {
            search(input).andExpect(jsonPath("$.totalElements").value(0));
        }
    }

    @Test
    void tooLongSearchIs400() throws Exception {
        mockMvc.perform(get("/api/courses").param("search", "a".repeat(201)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("at most 200 characters")));
        search("a".repeat(200)).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void unknownSortFieldIsStill400() throws Exception {
        mockMvc.perform(get("/api/courses").param("search", "spring").param("sortBy", "search_vector"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("relevance")));
    }
}
