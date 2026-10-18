package com.example.minhaj.dtos;

public record UserDTO(
        Long id,
        String name,
        String email,
        String role
) {}
