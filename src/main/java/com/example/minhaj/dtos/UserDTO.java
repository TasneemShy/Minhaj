package com.example.minhaj.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

public record UserDTO(
        Long id,
        String name,
        String email,
        @Schema(description = "Role", allowableValues = {"STUDENT", "INSTRUCTOR", "ADMIN"}, example = "STUDENT")
        String role,
        @Schema(description = "Whether the user has confirmed their email address; needed to enroll and to post reviews")
        boolean emailVerified
) {}
