package com.example.minhaj.services;

import com.example.minhaj.dtos.CourseDTO;
import com.example.minhaj.exceptions.ResourceNotFoundException;
import com.example.minhaj.models.Category;
import com.example.minhaj.models.Course;
import com.example.minhaj.models.Lesson;
import com.example.minhaj.models.User;
import com.example.minhaj.repositories.CategoryRepository;
import com.example.minhaj.repositories.CourseRepository;
import com.example.minhaj.repositories.LessonRepository;
import org.springframework.stereotype.Service;

// Course write logic shared by the admin endpoints and the instructor endpoints.
@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final CategoryRepository categoryRepository;
    private final LessonRepository lessonRepository;

    public CourseService(CourseRepository courseRepository, CategoryRepository categoryRepository,
                         LessonRepository lessonRepository) {
        this.courseRepository = courseRepository;
        this.categoryRepository = categoryRepository;
        this.lessonRepository = lessonRepository;
    }

    // The request sends the category as {"id": ...}; load the real entity so a bad ID fails with 404.
    private Category resolveCategory(Category requested) {
        if (requested == null || requested.getId() == null) {
            return null;
        }
        return categoryRepository.findById(requested.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + requested.getId()));
    }

    public Course create(Course course, User instructor) {
        if (course.isFree()) {
            course.setPrice(0.0);
        }
        course.setCategory(resolveCategory(course.getCategory()));
        course.setInstructor(instructor);
        return courseRepository.save(course);
    }

    public Course update(Course existingCourse, Course courseDetails) {
        existingCourse.setTitle(courseDetails.getTitle());
        existingCourse.setDescription(courseDetails.getDescription());
        existingCourse.setFree(courseDetails.isFree());
        existingCourse.setPrice(courseDetails.isFree() ? 0.0 : courseDetails.getPrice());
        existingCourse.setCategory(resolveCategory(courseDetails.getCategory()));
        return courseRepository.save(existingCourse);
    }

    public Lesson addLesson(Course course, Lesson lesson) {
        lesson.setCourse(course);
        Lesson savedLesson = lessonRepository.save(lesson);

        course.setLessonCount(course.getLessonCount() + 1);
        courseRepository.save(course);
        return savedLesson;
    }

    public static CourseDTO toDTO(Course course) {
        return new CourseDTO(
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                course.getPrice(),
                course.isFree(),
                course.getAverageRating(),
                course.getReviewCount(),
                course.getImageUrl(),
                course.getCategory() != null ? course.getCategory().getId() : null,
                course.getCategory() != null ? course.getCategory().getName() : null,
                course.getInstructor() != null ? course.getInstructor().getId() : null,
                course.getInstructor() != null ? course.getInstructor().getName() : null
        );
    }
}
