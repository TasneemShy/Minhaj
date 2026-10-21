package com.example.minhaj.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

public record UpdateRoleRequest(
        @Schema(description = "New role", allowableValues = {"STUDENT", "INSTRUCTOR", "ADMIN"}, example = "INSTRUCTOR")
        String role
) {}
