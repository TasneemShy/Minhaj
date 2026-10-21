package com.example.minhaj.repositories;

import com.example.minhaj.models.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    interface QuizStats {
        long getAttempts();
        long getStudentsAttempted();
        Double getAverageScore();
    }

    boolean existsByQuizId(Long quizId);

    boolean existsByQuizIdAndUserIdAndPassedTrue(Long quizId, Long userId);

    // The student's attempts, newest first, with quiz, lesson and course loaded for the history DTO.
    // A null courseId means every course.
    @Query("""
            select a from QuizAttempt a
            join fetch a.quiz q join fetch q.lesson l join fetch l.course c
            where a.user.id = :userId and (:courseId is null or c.id = :courseId)
            order by a.submittedAt desc, a.id desc
            """)
    List<QuizAttempt> findHistory(@Param("userId") Long userId, @Param("courseId") Long courseId);

    // averageScore is null when there are no attempts.
    @Query("""
            select count(a) as attempts, count(distinct a.user.id) as studentsAttempted,
                   avg(a.scorePercentage) as averageScore
            from QuizAttempt a where a.quiz.id = :quizId
            """)
    QuizStats summarize(@Param("quizId") Long quizId);

    @Query("select count(distinct a.user.id) from QuizAttempt a where a.quiz.id = :quizId and a.passed = true")
    long countStudentsPassed(@Param("quizId") Long quizId);
}
