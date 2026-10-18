package com.example.minhaj.web;

import com.example.minhaj.config.JwtRequestFilter;
import com.example.minhaj.config.JwtUtil;
import com.example.minhaj.config.SecurityConfig;
import com.example.minhaj.controllers.CategoryController;
import com.example.minhaj.repositories.CategoryRepository;
import com.example.minhaj.repositories.CourseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Fast slice (no database): the error-response format produced by GlobalExceptionHandler, with the real
// security chain and JWT filter in front of a controller whose repositories are mocked.
@WebMvcTest(CategoryController.class)
@Import({SecurityConfig.class, JwtRequestFilter.class, JwtUtil.class, GlobalExceptionHandlerWebMvcTest.JwtFilterOnlyInSecurityChain.class})
@ActiveProfiles("test")
class GlobalExceptionHandlerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private CategoryRepository categoryRepository;

    @MockitoBean
    private CourseRepository courseRepository;

    // @EnableJpaAuditing on MinhajApplication needs a JPA mapping context, which a web slice doesn't have
    @MockitoBean
    private JpaMetamodelMappingContext jpaMappingContext;

    // In the slice MockMvc would otherwise also run the JWT filter as a plain servlet filter ahead of the
    // security chain, where its authentication is discarded and the in-chain run is skipped as "already filtered".
    @TestConfiguration(proxyBeanMethods = false)
    static class JwtFilterOnlyInSecurityChain {
        @Bean
        FilterRegistrationBean<JwtRequestFilter> jwtFilterRegistration(JwtRequestFilter filter) {
            FilterRegistrationBean<JwtRequestFilter> registration = new FilterRegistrationBean<>(filter);
            registration.setEnabled(false);
            return registration;
        }
    }

    private String adminBearer() {
        return "Bearer " + jwtUtil.generateToken("admin@test.com", "ADMIN");
    }

    @Test
    void validationErrorsAreAFieldToMessageMap() throws Exception {
        mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").value("Category name cannot be empty"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void resourceNotFoundIs404WithErrorMessage() throws Exception {
        when(categoryRepository.findById(anyLong())).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/admin/categories/42").header("Authorization", adminBearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Category not found with ID: 42"));
    }

    @Test
    void malformedJsonIs400NotServerError() throws Exception {
        mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed or unreadable request body."));
    }

    @Test
    void unknownPathIs404NotServerError() throws Exception {
        mockMvc.perform(get("/api/admin/does-not-exist").header("Authorization", adminBearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void wrongPathVariableTypeIs400() throws Exception {
        mockMvc.perform(delete("/api/admin/categories/abc").header("Authorization", adminBearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid value 'abc' for parameter 'id'."));
    }

    @Test
    void unsupportedMethodIs405() throws Exception {
        mockMvc.perform(patch("/api/admin/categories/1")
                        .header("Authorization", adminBearer()))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void unexpectedExceptionsAreA500WithGenericMessage() throws Exception {
        when(categoryRepository.findAll(any(Sort.class)))
                .thenThrow(new IllegalStateException("database exploded"));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Something went wrong on our end!"));
    }

    @Test
    void securityStillAppliesInTheSlice() throws Exception {
        mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", "Bearer " + jwtUtil.generateToken("s@test.com", "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\"}"))
                .andExpect(status().isForbidden());
    }
}
