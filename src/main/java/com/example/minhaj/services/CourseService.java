package com.example.minhaj.services;

import com.example.minhaj.dtos.CourseRequest;
import com.example.minhaj.dtos.LessonRequest;
import com.example.minhaj.exceptions.ApiException;
import com.example.minhaj.exceptions.ResourceNotFoundException;
import com.example.minhaj.models.Category;
import com.example.minhaj.models.Course;
import com.example.minhaj.models.CourseStatus;
import com.example.minhaj.models.Lesson;
import com.example.minhaj.models.User;
import com.example.minhaj.repositories.CategoryRepository;
import com.example.minhaj.repositories.CourseRepository;
import com.example.minhaj.repositories.LessonProgressRepository;
import com.example.minhaj.repositories.LessonRepository;
import com.example.minhaj.repositories.ReviewRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    private Course findCourse(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with ID: " + id));
    }

    // Admins may manage any course; anyone else only a course they teach. Null authentication = anonymous.
    public boolean canManage(Course course, Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        User instructor = course.getInstructor();
        return isAdmin || (instructor != null && instructor.getEmail().equals(authentication.getName()));
    }

    // Published courses for everyone; drafts only for their instructor and admins. Everyone else gets
    // the same 404 as for a missing course, so a draft's existence is not leaked.
    public Course findVisibleCourse(Long id, Authentication authentication) {
        Course course = findCourse(id);
        if (!course.isPublished() && !canManage(course, authentication)) {
            throw new ResourceNotFoundException("Course not found with ID: " + id);
        }
        return course;
    }

    // For student actions (enroll, review): only published courses, whoever asks.
    public Course findPublishedCourse(Long id) {
        Course course = findCourse(id);
        if (!course.isPublished()) {
            throw new ResourceNotFoundException("Course not found with ID: " + id);
        }
        return course;
    }

    // Idempotent. publishedAt keeps the first publish time, so re-publishing does not bump the course
    // to the top of the "newest" catalog order.
    public Course publish(Course course) {
        if (lessonRepository.countByCourseId(course.getId()) == 0) {
            throw ApiException.badRequest("Course " + course.getId()
                    + " has no lessons. Add at least one lesson before publishing it.");
        }
        course.setStatus(CourseStatus.PUBLISHED);
        if (course.getPublishedAt() == null) {
            // Postgres timestamp(6) keeps microseconds; truncate so the response matches what is stored.
            course.setPublishedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
        }
        return courseRepository.save(course);
    }

    // Idempotent. Enrolled students keep their enrollment and progress.
    public Course unpublish(Course course) {
        course.setStatus(CourseStatus.DRAFT);
        return courseRepository.save(course);
    }

    // New courses start as DRAFT (the entity default) until they are published.
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

    // Called after every review change. Recompute from the reviews table instead of adjusting a running total,
    // so the values can't drift.
    public void refreshRating(Course course) {
        Double average = reviewRepository.findAverageRatingByCourseId(course.getId());
        long count = reviewRepository.countByCourseId(course.getId());

        course.setReviewCount((int) count);
        course.setAverageRating(average == null ? 0.0 : Math.round(average * 10) / 10.0);
        courseRepository.save(course);
    }

    // Removes everything that references the course first, otherwise Postgres rejects the delete
    // on the foreign keys. Progress goes before lessons because it references lessons. Quizzes, their questions
    // and attempts go with the lessons through ON DELETE CASCADE (V6__quizzes.sql).
    @Transactional
    public void deleteCourse(Course course) {
        Long courseId = course.getId();
        lessonProgressRepository.deleteByCourseId(courseId);
        lessonRepository.deleteByCourseId(courseId);
        reviewRepository.deleteByCourseId(courseId);
        courseRepository.deleteEnrollmentsByCourseId(courseId);
        courseRepository.deleteById(courseId);
    }

    // New lessons go to the end of the course.
    @Transactional
    public Lesson addLesson(Course course, LessonRequest request) {
        Lesson lesson = new Lesson();
        lesson.setTitle(request.title());
        lesson.setVideoUrl(request.videoUrl());
        lesson.setCourse(course);
        lesson.setPosition(lessonRepository.findMaxPositionByCourseId(course.getId()) + 1);
        Lesson savedLesson = lessonRepository.save(lesson);

        course.setLessonCount(course.getLessonCount() + 1);
        courseRepository.save(course);
        return savedLesson;
    }

    public List<Lesson> getLessons(Long courseId) {
        return lessonRepository.findByCourseIdOrderByPositionAsc(courseId);
    }

    private Lesson findLessonInCourse(Course course, Long lessonId) {
        return lessonRepository.findByIdAndCourseId(lessonId, course.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Lesson " + lessonId + " not found in course " + course.getId()));
    }

    public Lesson updateLesson(Course course, Long lessonId, LessonRequest request) {
        Lesson lesson = findLessonInCourse(course, lessonId);
        lesson.setTitle(request.title());
        lesson.setVideoUrl(request.videoUrl());
        return lessonRepository.save(lesson);
    }

    // Progress goes first because it references the lesson; the lesson's quiz and its attempts are removed by
    // ON DELETE CASCADE (V6__quizzes.sql). The lessons after it then move up one place.
    @Transactional
    public void deleteLesson(Course course, Long lessonId) {
        Lesson lesson = findLessonInCourse(course, lessonId);
        lessonProgressRepository.deleteByLessonId(lesson.getId());
        lessonRepository.delete(lesson);
        lessonRepository.shiftPositionsAfter(course.getId(), lesson.getPosition());

        course.setLessonCount(Math.max(0, course.getLessonCount() - 1));
        courseRepository.save(course);
    }

    // lessonIds must name every lesson of the course exactly once; they get positions 1..n in that order.
    @Transactional
    public List<Lesson> reorderLessons(Course course, List<Long> lessonIds) {
        List<Lesson> lessons = lessonRepository.findByCourseIdOrderByPositionAsc(course.getId());
        Map<Long, Lesson> byId = lessons.stream().collect(Collectors.toMap(Lesson::getId, Function.identity()));

        if (lessonIds.size() != lessons.size() || !byId.keySet().equals(new HashSet<>(lessonIds))) {
            throw ApiException.badRequest("lessonIds must contain each lesson of course " + course.getId()
                    + " exactly once (expected " + lessons.size() + " ids).");
        }

        List<Lesson> ordered = new ArrayList<>(lessonIds.size());
        for (int i = 0; i < lessonIds.size(); i++) {
            Lesson lesson = byId.get(lessonIds.get(i));
            lesson.setPosition(i + 1);
            ordered.add(lesson);
        }
        return ordered;
    }
}
