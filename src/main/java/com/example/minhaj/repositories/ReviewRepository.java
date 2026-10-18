package com.example.minhaj.repositories;

import com.example.minhaj.models.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    Page<Review> findByCourseId(Long courseId, Pageable pageable);
    Page<Review> findByUserId(Long userId, Pageable pageable);

    Optional<Review> findByIdAndCourseId(Long id, Long courseId);

    boolean existsByCourseIdAndUserId(Long courseId, Long userId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.course.id = :courseId")
    Double findAverageRatingByCourseId(@Param("courseId") Long courseId);

    long countByCourseId(Long courseId);
}
