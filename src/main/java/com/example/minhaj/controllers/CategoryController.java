package com.example.minhaj.controllers;

import com.example.minhaj.exceptions.ApiException;
import com.example.minhaj.exceptions.ResourceNotFoundException;
import com.example.minhaj.models.Category;
import com.example.minhaj.repositories.CategoryRepository;
import com.example.minhaj.repositories.CourseRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CategoryController {

    private final CategoryRepository categoryRepository;
    private final CourseRepository courseRepository;

    public CategoryController(CategoryRepository categoryRepository, CourseRepository courseRepository) {
        this.categoryRepository = categoryRepository;
        this.courseRepository = courseRepository;
    }

    @GetMapping("/categories")
    public ResponseEntity<List<Category>> getAllCategories() {
        return ResponseEntity.ok(categoryRepository.findAll(Sort.by("name")));
    }

    @PostMapping("/admin/categories")
    public ResponseEntity<?> createCategory(@Valid @RequestBody Category category) {
        if (categoryRepository.existsByNameIgnoreCase(category.getName())) {
            throw ApiException.badRequest("A category with this name already exists.");
        }
        category.setId(null);
        return ResponseEntity.ok(categoryRepository.save(category));
    }

    @PutMapping("/admin/categories/{id}")
    public ResponseEntity<?> updateCategory(@PathVariable Long id, @Valid @RequestBody Category categoryDetails) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + id));

        if (!category.getName().equalsIgnoreCase(categoryDetails.getName())
                && categoryRepository.existsByNameIgnoreCase(categoryDetails.getName())) {
            throw ApiException.badRequest("A category with this name already exists.");
        }

        category.setName(categoryDetails.getName());
        category.setDescription(categoryDetails.getDescription());
        return ResponseEntity.ok(categoryRepository.save(category));
    }

    @DeleteMapping("/admin/categories/{id}")
    public ResponseEntity<?> deleteCategory(@PathVariable Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + id));

        if (courseRepository.existsByCategoryId(id)) {
            throw ApiException.badRequest("Cannot delete a category that still has courses. Move or delete those courses first.");
        }

        categoryRepository.delete(category);
        return ResponseEntity.ok(Map.of("message", "Category deleted successfully!"));
    }
}
