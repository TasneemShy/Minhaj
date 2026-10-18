package com.example.minhaj.dtos;

import com.example.minhaj.models.Category;

public record CategoryDTO(
        Long id,
        String name,
        String description
) {
    public static CategoryDTO from(Category category) {
        return new CategoryDTO(category.getId(), category.getName(), category.getDescription());
    }
}
