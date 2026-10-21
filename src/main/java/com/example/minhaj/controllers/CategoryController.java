package com.example.minhaj.controllers;

import com.example.minhaj.dtos.CategoryDTO;
import com.example.minhaj.dtos.CategoryRequest;
import com.example.minhaj.exceptions.ApiException;
import com.example.minhaj.exceptions.ResourceNotFoundException;
import com.example.minhaj.models.Category;
import com.example.minhaj.repositories.CategoryRepository;
import com.example.minhaj.repositories.CourseRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static com.example.minhaj.config.SwaggerConfig.*;

@RestController
@RequestMapping("/api")
@Tag(name = "Categories", description = "Course categories: public listing, admin management.")
public class CategoryController {

    private final CategoryRepository categoryRepository;
    private final CourseRepository courseRepository;

    public CategoryController(CategoryRepository categoryRepository, CourseRepository courseRepository) {
        this.categoryRepository = categoryRepository;
        this.courseRepository = courseRepository;
    }

    @Operation(summary = "List categories", description = "Public. All categories sorted by name.")
    @ApiResponse(responseCode = "200", description = "Categories")
    @GetMapping("/categories")
    public ResponseEntity<List<CategoryDTO>> getAllCategories() {
        return ResponseEntity.ok(categoryRepository.findAll(Sort.by("name")).stream().map(CategoryDTO::from).toList());
    }

    @Operation(summary = "Create a category",
            description = "Admin only. Names are unique, ignoring case; a duplicate name gives 400.",
            security = @SecurityRequirement(name = BEARER_AUTH))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The created category"),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN)
    })
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

    @Operation(summary = "Update a category",
            description = "Admin only. Renaming to another category's name (ignoring case) gives 400.",
            security = @SecurityRequirement(name = BEARER_AUTH))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated category"),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
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

    @Operation(summary = "Delete a category",
            description = "Admin only. A category that still has courses cannot be deleted (400).",
            security = @SecurityRequirement(name = BEARER_AUTH))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deleted", content = @Content(schema = @Schema(ref = MESSAGE_SCHEMA))),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
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
