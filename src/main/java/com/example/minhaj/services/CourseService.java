package com.example.minhaj.services;

import com.example.minhaj.dtos.CourseRequest;
import com.example.minhaj.dtos.LessonRequest;
import com.example.minhaj.exceptions.ResourceNotFoundException;
import com.example.minhaj.models.Category;
import com.example.minhaj.models.Course;
import com.example.minhaj.models.Lesson;
import com.example.minhaj.models.User;
import com.example.minhaj.repositories.CategoryRepository;
import com.example.minhaj.repositories.CourseRepository;
import com.example.minhaj.repositories.LessonProgressRepository;
import com.example.minhaj.repositories.LessonRepository;
import com.example.minhaj.repositories.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Course write logic shared by the admin endpoints and the instructor endpoints.
@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final CategoryRepository categoryRepository;
    private final LessonRepository lessonRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final ReviewRepository reviewRepository;

    public CourseService(CourseRepository courseRepository, CategoryRepository categoryRepository,
                         LessonRepository lessonRepository, LessonProgressRepository lessonProgressRepository,
                         ReviewRepository reviewRepository) {
        this.courseRepository = courseRepository;
        this.categoryRepository = categoryRepository;
        this.lessonRepository = lessonRepository;
        this.lessonProgressRepository = lessonProgressRepository;
        this.reviewRepository = reviewRepository;
    }

    // Load the real category so a bad ID fails with 404.
    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + categoryId));
    }

    // Copies only the client-editable fields; counters, rating, timestamps and the instructor stay server-controlled.
    private void apply(Course course, CourseRequest request) {
        boolean free = Boolean.TRUE.equals(request.free());
        course.setTitle(request.title());
        course.setDescription(request.description());
        course.setFree(free);
        course.setPrice(free || request.price() == null ? 0.0 : request.price());
        course.setImageUrl(request.imageUrl());
        course.setCategory(resolveCategory(request.categoryId()));
    }

    public Course create(CourseRequest request, User instructor) {
        Course course = new Course();
        apply(course, request);
        course.setInstructor(instructor);
        return courseRepository.save(course);
    }

    public Course update(Course existingCourse, CourseRequest request) {
        apply(existingCourse, request);
        return courseRepository.save(existingCourse);
    }

    // Removes everything that references the course first, otherwise Postgres rejects the delete
    // on the foreign keys. Progress goes before lessons because it references lessons.
    @Transactional
    public void deleteCourse(Course course) {
        Long courseId = course.getId();
        lessonProgressRepository.deleteByCourseId(courseId);
        lessonRepository.deleteByCourseId(courseId);
        reviewRepository.deleteByCourseId(courseId);
        courseRepository.deleteEnrollmentsByCourseId(courseId);
        courseRepository.deleteById(courseId);
    }

    public Lesson addLesson(Course course, LessonRequest request) {
        Lesson lesson = new Lesson();
        lesson.setTitle(request.title());
        lesson.setVideoUrl(request.videoUrl());
        lesson.setCourse(course);
        Lesson savedLesson = lessonRepository.save(lesson);

        course.setLessonCount(course.getLessonCount() + 1);
        courseRepository.save(course);
        return savedLesson;
    }
}
