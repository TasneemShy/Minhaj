package com.example.minhaj.integration;

import com.example.minhaj.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpenApiDocsIntegrationTest extends IntegrationTestBase {

    @Test
    void apiDocsArePublicAndListEveryTag() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Minhaj API"))
                .andExpect(jsonPath("$.tags[*].name").value(containsInAnyOrder(
                        "Auth", "Courses", "Categories", "Lessons", "Progress", "Reviews", "Profile",
                        "Instructor", "Admin – Users", "Admin – Analytics", "System")));
    }

    @Test
    void bearerSchemeIsAppliedPerOperationNotGlobally() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/courses'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/profile'].get.security[0]").value(hasKey("bearerAuth")))
                .andExpect(jsonPath("$.paths['/api/admin/courses'].post.security[0]").value(hasKey("bearerAuth")));
    }

    @Test
    void errorResponsesUseTheProblemDetailSchema() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.ProblemDetail.properties").value(hasKey("detail")))
                .andExpect(jsonPath("$.components.responses.NotFound.content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath("$.paths['/api/courses/{id}'].get.responses['404']['$ref']")
                        .value("#/components/responses/NotFound"));
    }

    @Test
    void swaggerUiIsPublic() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
