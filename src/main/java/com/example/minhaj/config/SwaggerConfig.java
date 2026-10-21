package com.example.minhaj.config;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

// OpenAPI document served at /v3/api-docs and rendered by Swagger UI.
// Controllers reference the shared pieces below by name: the bearer scheme on secured operations,
// the RFC 9457 error responses, and the small Message / AuthResponse bodies returned as maps.
@Configuration
public class SwaggerConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    public static final String BAD_REQUEST = "#/components/responses/BadRequest";
    public static final String UNAUTHORIZED = "#/components/responses/Unauthorized";
    public static final String FORBIDDEN = "#/components/responses/Forbidden";
    public static final String NOT_FOUND = "#/components/responses/NotFound";
    public static final String CONFLICT = "#/components/responses/Conflict";

    public static final String MESSAGE_SCHEMA = "#/components/schemas/Message";
    public static final String AUTH_RESPONSE_SCHEMA = "#/components/schemas/AuthResponse";

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String PROBLEM_SCHEMA = "#/components/schemas/ProblemDetail";

    private final String version;

    public SwaggerConfig(@Value("${info.app.version:dev}") String version) {
        this.version = version;
    }

    @Bean
    public OpenAPI minhajOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Minhaj API")
                        .description("REST API of the Minhaj learning platform: courses, categories, lessons, enrollment, "
                                + "lesson progress, reviews, profiles, instructor course management and admin tools.\n\n"
                                + "Secured operations need a JWT from `POST /api/auth/login` or `POST /api/auth/register`: "
                                + "click **Authorize** and paste the token. Errors are RFC 9457 Problem Details "
                                + "(`application/problem+json`).")
                        .version(version))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .name(BEARER_AUTH)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT returned by the login and register endpoints, without the `Bearer ` prefix."))
                        .addSchemas("Message", new ObjectSchema()
                                .description("Confirmation message.")
                                .addProperty("message", new StringSchema().example("Course deleted successfully!")))
                        .addSchemas("AuthResponse", new ObjectSchema()
                                .description("Issued JWT and the role it carries.")
                                .addProperty("message", new StringSchema().example("Login successful"))
                                .addProperty("token", new StringSchema().example("eyJhbGciOiJIUzI1NiJ9..."))
                                .addProperty("role", new StringSchema()._enum(List.of("STUDENT", "INSTRUCTOR", "ADMIN"))
                                        .example("STUDENT")))
                        .addResponses("BadRequest", problem("Invalid input: failed validation (field messages in `errors`), "
                                + "a malformed body or parameter, or a rule the request breaks (see `detail`)."))
                        .addResponses("Unauthorized", problem("Missing, invalid or expired JWT."))
                        .addResponses("Forbidden", problem("Authenticated, but not allowed to perform this action."))
                        .addResponses("NotFound", problem("The referenced resource does not exist."))
                        .addResponses("Conflict", problem("The request conflicts with existing data.")));
    }

    // Added after springdoc has copied the OpenAPI bean above: that copy cannot read back a schema with a
    // typed map (the `errors` field) and logs a warning. Built from the annotated ProblemDetailSchema record.
    @Bean
    public OpenApiCustomizer problemDetailSchemaCustomizer() {
        return openApi -> openApi.getComponents().addSchemas("ProblemDetail",
                ModelConverters.getInstance(true).read(ProblemDetailSchema.class).get("ProblemDetail"));
    }

    private static ApiResponse problem(String description) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(PROBLEM_JSON,
                        new MediaType().schema(new Schema<>().$ref(PROBLEM_SCHEMA))));
    }
}
