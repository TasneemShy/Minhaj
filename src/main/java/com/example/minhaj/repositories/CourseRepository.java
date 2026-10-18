package com.example.minhaj.repositories;

import com.example.minhaj.models.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long>, JpaSpecificationExecutor<Course> {
    boolean existsByCategoryId(Long categoryId);

    // student_courses is a plain @ManyToMany join table with no entity of its own.
    @Modifying
    @Query(value = "DELETE FROM student_courses WHERE course_id = :courseId", nativeQuery = true)
    void deleteEnrollmentsByCourseId(@Param("courseId") Long courseId);
}
