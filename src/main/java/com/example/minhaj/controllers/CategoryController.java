package com.example.minhaj.controllers;

import com.example.minhaj.dtos.CategoryDTO;
import com.example.minhaj.dtos.CategoryRequest;
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
    public ResponseEntity<List<CategoryDTO>> getAllCategories() {
        return ResponseEntity.ok(categoryRepository.findAll(Sort.by("name")).stream().map(CategoryDTO::from).toList());
    }

    @PostMapping("/admin/categories")
    public ResponseEntity<CategoryDTO> createCategory(@Valid @RequestBody CategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.name())) {
            throw ApiException.badRequest("A category with this name already exists.");
        }
        Category category = new Category();
        category.setName(request.name());
        category.setDescription(request.description());
        return ResponseEntity.ok(CategoryDTO.from(categoryRepository.save(category)));
    }

    @PutMapping("/admin/categories/{id}")
    public ResponseEntity<CategoryDTO> updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + id));

        if (!category.getName().equalsIgnoreCase(request.name())
                && categoryRepository.existsByNameIgnoreCase(request.name())) {
            throw ApiException.badRequest("A category with this name already exists.");
        }

        category.setName(request.name());
        category.setDescription(request.description());
        return ResponseEntity.ok(CategoryDTO.from(categoryRepository.save(category)));
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
