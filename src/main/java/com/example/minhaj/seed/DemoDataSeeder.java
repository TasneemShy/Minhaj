package com.example.minhaj.seed;

import com.example.minhaj.dtos.CourseRequest;
import com.example.minhaj.dtos.LessonRequest;
import com.example.minhaj.models.Category;
import com.example.minhaj.models.Course;
import com.example.minhaj.models.LessonProgress;
import com.example.minhaj.models.Review;
import com.example.minhaj.models.User;
import com.example.minhaj.repositories.CategoryRepository;
import com.example.minhaj.repositories.LessonProgressRepository;
import com.example.minhaj.repositories.ReviewRepository;
import com.example.minhaj.repositories.UserRepository;
import com.example.minhaj.services.CourseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

// Demo data for exploring the API on a fresh database: users, categories, courses with lessons, enrollments,
// progress and reviews. Only runs under the "demo" profile (never by default, never in tests) and only once:
// if the demo admin already exists it does nothing. Runs after Flyway, in one transaction.
// All demo users share one fake password (app.demo.password) and are marked emailVerified without any email.
// Writes go through CourseService and the repositories, never the controllers, so nothing is emailed.
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    // example.com is reserved, so these addresses can never reach a real inbox.
    public static final String ADMIN_EMAIL = "demo-admin@example.com";
    public static final String INSTRUCTOR_1 = "demo-instructor1@example.com";
    public static final String INSTRUCTOR_2 = "demo-instructor2@example.com";
    public static final String STUDENT_1 = "demo-student1@example.com";
    public static final String STUDENT_2 = "demo-student2@example.com";
    public static final String STUDENT_3 = "demo-student3@example.com";

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ReviewRepository reviewRepository;
    private final LessonProgressRepository progressRepository;
    private final CourseService courseService;
    private final TransactionTemplate transaction;
    private final String password;

    // Same encoder the auth endpoints use
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public DemoDataSeeder(UserRepository userRepository, CategoryRepository categoryRepository,
                          ReviewRepository reviewRepository, LessonProgressRepository progressRepository,
                          CourseService courseService, PlatformTransactionManager transactionManager,
                          @Value("${app.demo.password:demo-password-123}") String password) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.reviewRepository = reviewRepository;
        this.progressRepository = progressRepository;
        this.courseService = courseService;
        this.transaction = new TransactionTemplate(transactionManager);
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (seed()) {
            log.warn("Demo data loaded: demo data, never enable the demo profile in production. "
                    + "Accounts (password: {}): admin {}, instructors {}, {}, students {}, {}, {}", password,
                    ADMIN_EMAIL, INSTRUCTOR_1, INSTRUCTOR_2, STUDENT_1, STUDENT_2, STUDENT_3);
        } else {
            log.warn("Demo profile active but demo data already present (found {}); nothing seeded. "
                    + "Never enable the demo profile in production.", ADMIN_EMAIL);
        }
    }

    // Returns false (and changes nothing) if the demo data is already there.
    public boolean seed() {
        return Boolean.TRUE.equals(transaction.execute(status -> {
            if (userRepository.findByEmail(ADMIN_EMAIL).isPresent()) {
                return false;
            }
            seedAll();
            return true;
        }));
    }

    private void seedAll() {
        user("Demo Admin", ADMIN_EMAIL, "ADMIN");
        User sara = user("Sara Al-Hassan", INSTRUCTOR_1, "INSTRUCTOR");
        User omar = user("Omar Khalid", INSTRUCTOR_2, "INSTRUCTOR");
        User layla = user("Layla Ahmed", STUDENT_1, "STUDENT");
        User yusuf = user("Yusuf Ali", STUDENT_2, "STUDENT");
        User mariam = user("Mariam Saleh", STUDENT_3, "STUDENT");

        Category programming = category("البرمجة", "دورات البرمجة وتطوير البرمجيات");
        Category data = category("Data Science", "Data analysis, SQL and machine learning");
        Category languages = category("Languages", "Learn a new language");
        Category design = category("Design", "UI and UX design");

        Course python = course(sara, programming, "أساسيات البرمجة بلغة بايثون",
                "دورة مجانية للمبتدئين: المتغيرات والشروط والحلقات والدوال بلغة بايثون، مع تمارين عملية بعد كل درس.",
                true, 0, true, "python",
                "مقدمة وتثبيت بايثون", "المتغيرات وأنواع البيانات", "الشروط والحلقات", "الدوال");
        Course spring = course(sara, programming, "Spring Boot REST APIs from Scratch",
                "Build a production-ready REST API with Spring Boot: controllers, JPA, validation, security with JWT and testing.",
                false, 29.99, true, "spring-boot",
                "Project setup", "Controllers and DTOs", "Persistence with JPA", "Securing the API with JWT", "Testing");
        Course sql = course(omar, data, "Introduction to Data Analysis with SQL",
                "Answer real business questions with SQL: filtering, joins, grouping and window functions on a sample dataset.",
                false, 19.99, true, "sql",
                "Your first query", "Filtering and sorting", "Joins", "Grouping and aggregates");
        Course arabic = course(omar, languages, "العربية للمبتدئين",
                "تعلم الحروف العربية والقراءة والكتابة والمحادثات اليومية البسيطة خطوة بخطوة.",
                true, 0, true, "arabic",
                "الحروف العربية", "القراءة والكتابة", "محادثات يومية");
        Course ui = course(sara, design, "UI Design Fundamentals",
                "Learn layout, typography, colour and accessibility, and design a small mobile app from wireframe to mockup.",
                false, 24.99, true, "ui-design",
                "Layout and grids", "Typography and colour", "From wireframe to mockup");
        // Left as a DRAFT: visible to its instructor and admins only, not in the public catalog.
        course(omar, data, "تعلم الآلة: مقدمة عملية",
                "مقدمة عملية إلى تعلم الآلة: تجهيز البيانات، الانحدار والتصنيف، وتقييم النماذج.",
                false, 39.99, false, "machine-learning",
                "ما هو تعلم الآلة؟", "تجهيز البيانات", "أول نموذج");

        enroll(layla, python, spring, arabic);
        enroll(yusuf, python, sql);
        enroll(mariam, spring, ui);

        // Layla completes every lesson of the Python course, so its completion certificate can be shown.
        complete(layla, python, Integer.MAX_VALUE);
        complete(layla, spring, 2);
        complete(yusuf, sql, 1);

        review(layla, python, 5, "دورة ممتازة وواضحة جداً، أنصح بها لكل مبتدئ.");
        review(yusuf, python, 4, "Great introduction, I would like more exercises.");
        review(layla, spring, 5, "Exactly what I needed to build my first API.");
        review(mariam, spring, 4, "Very practical, the security part was the best.");
        review(yusuf, sql, 4, "Clear explanations of joins.");
        review(mariam, ui, 4, "Nice overview of the basics.");
    }

    private User user(String name, String email, String role) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        // Demo only: real users verify through the emailed link.
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    private Category category(String name, String description) {
        Category category = new Category();
        category.setName(name);
        category.setDescription(description);
        return categoryRepository.save(category);
    }

    private Course course(User instructor, Category category, String title, String description, boolean free,
                          double price, boolean publish, String slug, String... lessonTitles) {
        Course course = courseService.create(new CourseRequest(title, description, price, free, null, category.getId()), instructor);
        for (int i = 0; i < lessonTitles.length; i++) {
            courseService.addLesson(course, new LessonRequest(lessonTitles[i],
                    "https://videos.example.com/demo/" + slug + "/" + (i + 1)));
        }
        return publish ? courseService.publish(course) : course;
    }

    private void enroll(User student, Course... courses) {
        student.getEnrolledCourses().addAll(List.of(courses));
        userRepository.save(student);
    }

    // Marks the first `count` lessons as completed, straight in the repository (no completion email).
    private void complete(User student, Course course, int count) {
        courseService.getLessons(course.getId()).stream().limit(count).forEach(lesson -> progressRepository.save(new LessonProgress(student, lesson)));
    }

    private void review(User student, Course course, int rating, String comment) {
        Review review = new Review();
        review.setUser(student);
        review.setCourse(course);
        review.setRating(rating);
        review.setComment(comment);
        reviewRepository.save(review);
        courseService.refreshRating(course);
    }
}
