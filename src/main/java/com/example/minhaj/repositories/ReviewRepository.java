package com.example.minhaj.repositories;

import com.example.minhaj.models.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByCourseId(Long courseId);
    List<Review> findByUserId(Long userId);

    boolean existsByCourseIdAndUserId(Long courseId, Long userId);
}
