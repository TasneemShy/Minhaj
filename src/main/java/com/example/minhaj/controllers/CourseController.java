package com.example.minhaj.controllers;

import com.example.minhaj.exceptions.ApiException;
import com.example.minhaj.exceptions.ResourceNotFoundException;
import com.example.minhaj.models.Course;
import com.example.minhaj.models.User;
import com.example.minhaj.repositories.CourseRepository;
import com.example.minhaj.repositories.CourseSpecifications;
import com.example.minhaj.repositories.UserRepository;
import com.example.minhaj.services.CourseService;
import com.example.minhaj.services.EmailVerificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.example.minhaj.dtos.CourseDTO;
import com.example.minhaj.dtos.CourseRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.example.minhaj.config.SwaggerConfig.*;

@RestController
@RequestMapping("/api")
@Tag(name = "Courses", description = "Course catalogue, admin course management and student enrollment.")
public class CourseController {

    private static final List<String> SORTABLE_FIELDS = List.of("createdAt", "title", "price", "averageRating", "reviewCount");

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final CourseService courseService;

    public CourseController(CourseRepository courseRepository, UserRepository userRepository, CourseService courseService) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.courseService = courseService;
    }

    @Operation(summary = "List courses",
            description = "Public. Returns a page of courses (Spring Data page: `content`, `totalElements`, `totalPages`, "
                    + "`number`, `size`, ...). All filters are optional and combine with AND.")
    @Parameter(name = "page", in = ParameterIn.QUERY, description = "Zero-based page index", example = "0")
    @Parameter(name = "size", in = ParameterIn.QUERY, description = "Page size", example = "10")
    @Parameter(name = "search", in = ParameterIn.QUERY, description = "Case-insensitive text contained in the title", example = "java")
    @Parameter(name = "categoryId", in = ParameterIn.QUERY, description = "Only courses in this category", example = "1")
    @Parameter(name = "free", in = ParameterIn.QUERY, description = "true for free courses only, false for paid only")
    @Parameter(name = "minPrice", in = ParameterIn.QUERY, description = "Lowest price (inclusive)", example = "0")
    @Parameter(name = "maxPrice", in = ParameterIn.QUERY, description = "Highest price (inclusive)", example = "50")
    @Parameter(name = "minRating", in = ParameterIn.QUERY, description = "Lowest average rating (inclusive, 0-5)", example = "4")
    @Parameter(name = "instructorId", in = ParameterIn.QUERY, description = "Only courses taught by this instructor", example = "2")
    @Parameter(name = "sortBy", in = ParameterIn.QUERY, description = "Field to sort by",
            schema = @Schema(type = "string", defaultValue = "createdAt",
                    allowableValues = {"createdAt", "title", "price", "averageRating", "reviewCount"}))
    @Parameter(name = "direction", in = ParameterIn.QUERY, description = "Sort direction; anything other than asc sorts descending",
            schema = @Schema(type = "string", defaultValue = "desc", allowableValues = {"asc", "desc"}))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A page of courses"),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST)
    })
    @GetMapping("/courses")
    public ResponseEntity<?> getAllCourses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean free,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Long instructorId,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        if (!SORTABLE_FIELDS.contains(sortBy)) {
            throw ApiException.badRequest("sortBy must be one of: " + String.join(", ", SORTABLE_FIELDS));
        }

        Sort sort = Sort.by("asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Course> courses = courseRepository.findAll(
                CourseSpecifications.withFilters(search, categoryId, free, minPrice, maxPrice, minRating, instructorId), pageable);

        Page<CourseDTO> courseDTOs = courses.map(CourseDTO::from);

        return ResponseEntity.ok(courseDTOs);
    }

    @Operation(summary = "Get a course", description = "Public.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The course"),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
    @GetMapping("/courses/{id}")
    public ResponseEntity<CourseDTO> getCourseById(@PathVariable Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with ID: " + id));
        return ResponseEntity.ok(CourseDTO.from(course));
    }

    @Operation(summary = "Create a course (admin)",
            description = "Admin only. Creates a course with no instructor; instructors use `POST /api/instructor/courses`. "
                    + "Free courses are stored with price 0.",
            security = @SecurityRequirement(name = BEARER_AUTH))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The created course"),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
    @PostMapping("/admin/courses")
    public CourseDTO createCourse(@Valid @RequestBody CourseRequest request) {
        return CourseDTO.from(courseService.create(request, null));
    }

    @Operation(summary = "Update a course (admin)",
            description = "Admin only. Replaces the editable fields of any course. 404 if the course or the given category does not exist.",
            security = @SecurityRequirement(name = BEARER_AUTH))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated course"),
            @ApiResponse(responseCode = "400", ref = BAD_REQUEST),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
    @PutMapping("/admin/courses/{id}")
    public ResponseEntity<CourseDTO> updateCourse(@PathVariable Long id, @Valid @RequestBody CourseRequest request) {

        Course existingCourse = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cannot update. Course not found with ID: " + id));

        return ResponseEntity.ok(CourseDTO.from(courseService.update(existingCourse, request)));
    }

    @Operation(summary = "Delete a course (admin)",
            description = "Admin only. Deletes the course with its lessons, reviews, enrollments and progress.",
            security = @SecurityRequirement(name = BEARER_AUTH))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deleted", content = @Content(schema = @Schema(ref = MESSAGE_SCHEMA))),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
    @DeleteMapping("/admin/courses/{id}")
    public ResponseEntity<?> deleteCourse(@PathVariable Long id) {

        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cannot delete. Course not found with ID: " + id));

        courseService.deleteCourse(course);
        return ResponseEntity.ok(Map.of("message", "Course deleted successfully!"));
    }

    @Operation(summary = "Enroll in a course",
            description = "Authenticated users with a verified email (403 otherwise). Enrolls the caller; enrolling again "
                    + "in the same course has no effect.",
            security = @SecurityRequirement(name = BEARER_AUTH))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Enrolled", content = @Content(schema = @Schema(ref = MESSAGE_SCHEMA))),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED),
            @ApiResponse(responseCode = "403", ref = FORBIDDEN),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND)
    })
    @PostMapping("/courses/{id}/enroll")
    public ResponseEntity<?> enrollInCourse(@PathVariable Long id, java.security.Principal principal) {

        if (principal == null) {
            throw ApiException.unauthorized("You must be logged in to enroll in a course.");
        }

        String studentEmail = principal.getName();
        User student = userRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found in the database."));
        EmailVerificationService.requireVerified(student, "enroll in courses");

        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with ID: " + id));

        student.getEnrolledCourses().add(course);
        userRepository.save(student);

        return ResponseEntity.ok(Map.of("message", "Successfully enrolled in course: " + course.getTitle()));
    }

    @Operation(summary = "List my enrolled courses",
            description = "Authenticated users. The caller's enrolled courses ordered by id; empty when none.",
            security = @SecurityRequirement(name = BEARER_AUTH))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Enrolled courses"),
            @ApiResponse(responseCode = "401", ref = UNAUTHORIZED)
    })
    @GetMapping("/my-courses")
    public ResponseEntity<List<CourseDTO>> getMyCourses(java.security.Principal principal) {

        if (principal == null) {
            throw ApiException.unauthorized("You must be logged in to view your courses.");
        }

        String studentEmail = principal.getName();
        User student = userRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found in the database."));

        // An empty list when the student has not enrolled anywhere yet
        List<CourseDTO> myCourses = student.getEnrolledCourses().stream()
                .sorted(Comparator.comparing(Course::getId))
                .map(CourseDTO::from)
                .toList();
        return ResponseEntity.ok(myCourses);
    }
}