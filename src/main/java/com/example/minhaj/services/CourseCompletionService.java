package com.example.minhaj.services;

import com.example.minhaj.dtos.CompletionDTO;
import com.example.minhaj.models.Course;
import com.example.minhaj.models.User;
import com.example.minhaj.repositories.LessonProgressRepository;
import com.example.minhaj.repositories.LessonRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

// Course completion is derived, not stored: a course is completed when it has at least one lesson and the
// student has a progress row for every current lesson. completedAt is the latest of those rows, so adding a
// lesson makes the course incomplete again and deleting the last missing lesson completes it.
// Enrollment is checked by the callers.
@Service
public class CourseCompletionService {

    private final LessonProgressRepository progressRepository;
    private final LessonRepository lessonRepository;

    public CourseCompletionService(LessonProgressRepository progressRepository, LessonRepository lessonRepository) {
        this.progressRepository = progressRepository;
        this.lessonRepository = lessonRepository;
    }

    // Empty while the course is incomplete or has no lessons.
    public Optional<LocalDateTime> completedAt(User student, Course course) {
        long totalLessons = lessonRepository.countByCourseId(course.getId());
        LessonProgressRepository.CompletionSummary summary = progressRepository.summarize(student.getId(), course.getId());
        if (totalLessons == 0 || summary.getCompletedLessons() < totalLessons) {
            return Optional.empty();
        }
        return Optional.of(summary.getLastCompletedAt());
    }

    public Optional<CompletionDTO> findCompletion(User student, Course course) {
        return completedAt(student, course).map(completedAt -> new CompletionDTO(
                course.getId(),
                course.getTitle(),
                student.getName(),
                completedAt,
                (int) lessonRepository.countByCourseId(course.getId()),
                course.getInstructor() != null ? course.getInstructor().getName() : null));
    }
}
