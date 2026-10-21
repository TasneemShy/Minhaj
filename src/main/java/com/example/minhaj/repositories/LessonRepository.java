package com.example.minhaj.repositories;

import com.example.minhaj.models.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LessonRepository extends JpaRepository<Lesson, Long> {
    List<Lesson> findByCourseIdOrderByPositionAsc(Long courseId);

    Optional<Lesson> findByIdAndCourseId(Long id, Long courseId);

    @Query("SELECT COALESCE(MAX(l.position), 0) FROM Lesson l WHERE l.course.id = :courseId")
    int findMaxPositionByCourseId(@Param("courseId") Long courseId);

    // Closes the gap left by a deleted lesson; the unique (course_id, position) constraint is checked at commit.
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Lesson l SET l.position = l.position - 1 WHERE l.course.id = :courseId AND l.position > :position")
    void shiftPositionsAfter(@Param("courseId") Long courseId, @Param("position") int position);

    long countByCourseId(Long courseId);

    @Modifying
    @Query("DELETE FROM Lesson l WHERE l.course.id = :courseId")
    void deleteByCourseId(@Param("courseId") Long courseId);
}