package com.example.minhaj.repositories;

import com.example.minhaj.models.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LessonRepository extends JpaRepository<Lesson, Long> {
    List<Lesson> findByCourseId(Long courseId);

    long countByCourseId(Long courseId);

    @Modifying
    @Query("DELETE FROM Lesson l WHERE l.course.id = :courseId")
    void deleteByCourseId(@Param("courseId") Long courseId);
}