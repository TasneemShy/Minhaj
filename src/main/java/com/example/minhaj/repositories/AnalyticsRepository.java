package com.example.minhaj.repositories;

import com.example.minhaj.models.Course;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

// Read-only aggregate queries for the admin analytics endpoints. Everything is counted in the database.
public interface AnalyticsRepository extends Repository<Course, Long> {

    interface RoleCount {
        String getRole();
        long getCount();
    }

    interface CourseStats {
        Long getId();
        String getTitle();
        String getCategoryName();
        long getEnrollmentCount();
        Double getAverageRating();
        long getReviewCount();
    }

    interface CategoryStats {
        Long getId();
        String getName();
        long getCourseCount();
        long getEnrollmentCount();
        Double getAverageRating();
    }

    String COURSE_STATS = """
            select c.id as id, c.title as title, cat.name as categoryName,
                   (select count(s) from User s join s.enrolledCourses ec where ec = c) as enrollmentCount,
                   (select avg(r.rating) from Review r where r.course = c) as averageRating,
                   (select count(r) from Review r where r.course = c) as reviewCount
            from Course c left join c.category cat
            """;

    // Platform overview

    @Query("select count(u) from User u")
    long countUsers();

    @Query("select coalesce(u.role, 'UNKNOWN') as role, count(u) as count from User u group by u.role order by u.role")
    List<RoleCount> countUsersByRole();

    @Query("select count(c) from Course c")
    long countCourses();

    @Query("select count(c) from Course c where c.isFree = true")
    long countFreeCourses();

    @Query("select count(c) from Course c where c.status = com.example.minhaj.models.CourseStatus.PUBLISHED")
    long countPublishedCourses();

    @Query("select count(cat) from Category cat")
    long countCategories();

    @Query("select count(l) from Lesson l")
    long countLessons();

    // One row per student_courses entry
    @Query("select count(c) from User u join u.enrolledCourses c")
    long countEnrollments();

    @Query("select count(r) from Review r")
    long countReviews();

    @Query("select avg(r.rating) from Review r")
    Double averageRating();

    @Query("select count(lp) from LessonProgress lp")
    long countCompletedLessons();

    // Top courses

    @Query(COURSE_STATS + " order by enrollmentCount desc, c.id")
    List<CourseStats> findTopCoursesByEnrollments(Pageable pageable);

    @Query(COURSE_STATS + " order by averageRating desc nulls last, reviewCount desc, c.id")
    List<CourseStats> findTopCoursesByRating(Pageable pageable);

    @Query(COURSE_STATS + " order by reviewCount desc, c.id")
    List<CourseStats> findTopCoursesByReviews(Pageable pageable);

    // Categories

    @Query("""
            select cat.id as id, cat.name as name,
                   (select count(c) from Course c where c.category = cat) as courseCount,
                   (select count(s) from User s join s.enrolledCourses c where c.category = cat) as enrollmentCount,
                   (select avg(r.rating) from Review r where r.course.category = cat) as averageRating
            from Category cat
            order by cat.name
            """)
    List<CategoryStats> findCategoryStats();

    @Query("select count(c) from Course c where c.category is null")
    long countUncategorizedCourses();

    @Query("select count(c) from User u join u.enrolledCourses c where c.category is null")
    long countUncategorizedEnrollments();

    @Query("select avg(r.rating) from Review r where r.course.category is null")
    Double averageUncategorizedRating();

    // Single course

    @Query("select count(u) from User u join u.enrolledCourses c where c.id = :courseId")
    long countEnrollmentsByCourseId(@Param("courseId") Long courseId);

    @Query("select count(r) from Review r where r.course.id = :courseId")
    long countReviewsByCourseId(@Param("courseId") Long courseId);

    @Query("select avg(r.rating) from Review r where r.course.id = :courseId")
    Double averageRatingByCourseId(@Param("courseId") Long courseId);

    // Completions by students who are currently enrolled (progress left behind by others is ignored)
    @Query("""
            select count(lp) from LessonProgress lp
            where lp.lesson.course.id = :courseId
              and exists (select 1 from User u join u.enrolledCourses c where u = lp.user and c.id = :courseId)
            """)
    long countCompletedLessonsByEnrolledStudents(@Param("courseId") Long courseId);

    // Currently enrolled students who completed every current lesson; 0 for a course without lessons.
    // Same rule as CourseCompletionService.
    @Query("""
            select count(u) from User u join u.enrolledCourses c
            where c.id = :courseId
              and (select count(l) from Lesson l where l.course.id = :courseId) > 0
              and (select count(lp) from LessonProgress lp where lp.user = u and lp.lesson.course.id = :courseId)
                  = (select count(l) from Lesson l where l.course.id = :courseId)
            """)
    long countCompletionsByCourseId(@Param("courseId") Long courseId);
}
