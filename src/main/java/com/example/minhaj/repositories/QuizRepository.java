package com.example.minhaj.repositories;

import com.example.minhaj.models.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, Long> {

    Optional<Quiz> findByLessonId(Long lessonId);

    boolean existsByLessonId(Long lessonId);
}
