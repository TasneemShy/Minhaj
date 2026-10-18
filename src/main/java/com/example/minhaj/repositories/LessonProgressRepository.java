package com.example.minhaj.repositories;

import com.example.minhaj.models.LessonProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LessonProgressRepository extends JpaRepository<LessonProgress, Long> {
    boolean existsByUserIdAndLessonId(Long userId, Long lessonId);

    Optional<LessonProgress> findByUserIdAndLessonId(Long userId, Long lessonId);

    List<LessonProgress> findByUserIdAndLessonCourseId(Long userId, Long courseId);
}
