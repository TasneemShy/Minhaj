package com.example.minhaj.config;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

// Documentation only: the shape GlobalExceptionHandler and SecurityProblemHandler produce for every error.
// SwaggerConfig registers it as the "ProblemDetail" schema; the API itself returns Spring's ProblemDetail.
@Schema(name = "ProblemDetail",
        description = "RFC 9457 Problem Details, served as application/problem+json. "
                + "Validation failures add `errors`, a map of field name to message.")
record ProblemDetailSchema(
        @Schema(format = "uri", example = "about:blank")
        String type,

        @Schema(example = "Not Found")
        String title,

        @Schema(example = "404")
        int status,

        @Schema(example = "Course not found with ID: 42")
        String detail,

        @Schema(format = "uri", example = "/api/courses/42")
        String instance,

        @Schema(description = "Only on validation failures", example = "{\"title\": \"Course title cannot be empty\"}")
        Map<String, String> errors
) {}
