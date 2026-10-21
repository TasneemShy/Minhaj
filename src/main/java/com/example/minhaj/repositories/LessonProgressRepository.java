package com.example.minhaj.repositories;

import com.example.minhaj.models.LessonProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LessonProgressRepository extends JpaRepository<LessonProgress, Long> {
    boolean existsByUserIdAndLessonId(Long userId, Long lessonId);

    Optional<LessonProgress> findByUserIdAndLessonId(Long userId, Long lessonId);

    List<LessonProgress> findByUserIdAndLessonCourseId(Long userId, Long courseId);

    @Modifying
    @Query("DELETE FROM LessonProgress p WHERE p.lesson.id IN (SELECT l.id FROM Lesson l WHERE l.course.id = :courseId)")
    void deleteByCourseId(@Param("courseId") Long courseId);

    @Modifying
    @Query("DELETE FROM LessonProgress p WHERE p.lesson.id = :lessonId")
    void deleteByLessonId(@Param("lessonId") Long lessonId);
}
