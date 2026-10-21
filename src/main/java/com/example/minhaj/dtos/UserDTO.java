package com.example.minhaj.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

public record UserDTO(
        Long id,
        String name,
        String email,
        @Schema(description = "Role", allowableValues = {"STUDENT", "INSTRUCTOR", "ADMIN"}, example = "STUDENT")
        String role
) {}
