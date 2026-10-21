package com.example.minhaj.services;

import com.example.minhaj.dtos.QuizAnalyticsDTO;
import com.example.minhaj.dtos.QuizAttemptRequest;
import com.example.minhaj.dtos.QuizAttemptResultDTO;
import com.example.minhaj.dtos.QuizRequest;
import com.example.minhaj.exceptions.ApiException;
import com.example.minhaj.exceptions.ResourceNotFoundException;
import com.example.minhaj.metrics.AppMetrics;
import com.example.minhaj.models.Course;
import com.example.minhaj.models.Lesson;
import com.example.minhaj.models.QuestionType;
import com.example.minhaj.models.Quiz;
import com.example.minhaj.models.QuizAttempt;
import com.example.minhaj.models.QuizAttemptAnswer;
import com.example.minhaj.models.QuizOption;
import com.example.minhaj.models.QuizQuestion;
import com.example.minhaj.models.User;
import com.example.minhaj.repositories.LessonRepository;
import com.example.minhaj.repositories.QuizAttemptRepository;
import com.example.minhaj.repositories.QuizRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Quiz authoring, grading and analytics. Permission checks (owner/admin, enrollment, published course) are done
// by the controllers before calling in.
@Service
public class QuizService {

    private final QuizRepository quizRepository;
    private final QuizAttemptRepository attemptRepository;
    private final LessonRepository lessonRepository;
    private final CourseCompletionService completionService;
    private final AppMetrics metrics;
    private final EntityManager entityManager;

    public QuizService(QuizRepository quizRepository, QuizAttemptRepository attemptRepository,
                       LessonRepository lessonRepository, CourseCompletionService completionService,
                       AppMetrics metrics, EntityManager entityManager) {
        this.quizRepository = quizRepository;
        this.attemptRepository = attemptRepository;
        this.lessonRepository = lessonRepository;
        this.completionService = completionService;
        this.metrics = metrics;
        this.entityManager = entityManager;
    }

    public Lesson findLessonInCourse(Course course, Long lessonId) {
        return lessonRepository.findByIdAndCourseId(lessonId, course.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Lesson " + lessonId + " not found in course " + course.getId()));
    }

    public Quiz findQuiz(Lesson lesson) {
        return quizRepository.findByLessonId(lesson.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Lesson " + lesson.getId() + " has no quiz."));
    }

    public boolean hasQuiz(Lesson lesson) {
        return quizRepository.existsByLessonId(lesson.getId());
    }

    public boolean hasPassed(User student, Lesson lesson) {
        return quizRepository.findByLessonId(lesson.getId())
                .map(quiz -> attemptRepository.existsByQuizIdAndUserIdAndPassedTrue(quiz.getId(), student.getId()))
                .orElse(false);
    }

    // ---- authoring ----

    // Creates the lesson's quiz, or replaces its title, pass mark and all questions. A quiz that already has
    // attempts cannot be replaced (409): its stored answers and scores refer to the current questions.
    // Delete it (which deletes the attempts) to start over.
    @Transactional
    public Quiz saveQuiz(Lesson lesson, QuizRequest request) {
        validate(request);

        Quiz quiz = quizRepository.findByLessonId(lesson.getId()).orElse(null);
        if (quiz == null) {
            quiz = new Quiz(lesson);
        } else {
            if (attemptRepository.existsByQuizId(quiz.getId())) {
                throw ApiException.conflict("The quiz of lesson " + lesson.getId() + " already has attempts and cannot be "
                        + "replaced. Delete it first (this also deletes its attempts), then create it again.");
            }
            // Delete the old questions before inserting the new ones; Hibernate would otherwise insert first and
            // hit the unique (quiz_id, position) constraint.
            quiz.getQuestions().clear();
            entityManager.flush();
        }

        quiz.setTitle(request.title());
        quiz.setPassPercentage(request.passPercentageOrDefault());
        for (int i = 0; i < request.questions().size(); i++) {
            QuizRequest.Question q = request.questions().get(i);
            QuizQuestion question = new QuizQuestion(i + 1, q.text(), q.type());
            for (int j = 0; j < q.options().size(); j++) {
                QuizRequest.Option o = q.options().get(j);
                question.addOption(new QuizOption(j + 1, o.text(), o.correct()));
            }
            quiz.addQuestion(question);
        }
        return quizRepository.save(quiz);
    }

    // Bean validation covers sizes and blanks; this checks the number of correct options per question type.
    private static void validate(QuizRequest request) {
        for (int i = 0; i < request.questions().size(); i++) {
            QuizRequest.Question question = request.questions().get(i);
            long correct = question.options().stream().filter(QuizRequest.Option::correct).count();
            if (question.type() == QuestionType.SINGLE_CHOICE && correct != 1) {
                throw ApiException.badRequest("questions[" + i + "] is SINGLE_CHOICE and must have exactly one correct option (has "
                        + correct + ").");
            }
            if (question.type() == QuestionType.MULTIPLE_CHOICE && correct < 1) {
                throw ApiException.badRequest("questions[" + i + "] is MULTIPLE_CHOICE and must have at least one correct option.");
            }
        }
    }

    // Removes the quiz with its questions, options and attempts. Lesson progress (including progress earned by
    // passing this quiz) is kept.
    @Transactional
    public void deleteQuiz(Lesson lesson) {
        quizRepository.delete(findQuiz(lesson));
    }

    // ---- taking ----

    // Grades the answers, stores the attempt and, when it passes, marks the lesson completed (which can complete
    // the course and send its email). A question scores only if the selected options are exactly its correct ones.
    @Transactional
    public QuizAttemptResultDTO submit(Quiz quiz, User student, QuizAttemptRequest request) {
        Map<Long, QuizQuestion> questions = new HashMap<>();
        Map<Long, QuizOption> options = new HashMap<>();
        for (QuizQuestion question : quiz.getQuestions()) {
            questions.put(question.getId(), question);
            question.getOptions().forEach(option -> options.put(option.getId(), option));
        }

        Map<Long, Set<Long>> selected = new HashMap<>();
        for (QuizAttemptRequest.Answer answer : request.answers()) {
            Long questionId = answer.questionId();
            if (!questions.containsKey(questionId)) {
                throw ApiException.badRequest("Question " + questionId + " is not part of quiz " + quiz.getId() + ".");
            }
            if (selected.containsKey(questionId)) {
                throw ApiException.badRequest("Question " + questionId + " is answered more than once.");
            }
            Set<Long> optionIds = new LinkedHashSet<>();
            for (Long optionId : answer.optionIds()) {
                QuizOption option = options.get(optionId);
                if (option == null || !option.getQuestion().getId().equals(questionId)) {
                    throw ApiException.badRequest("Option " + optionId + " is not an option of question " + questionId + ".");
                }
                optionIds.add(optionId);
            }
            selected.put(questionId, optionIds);
        }

        List<QuizQuestion> ordered = quiz.getQuestions();
        int correctQuestions = 0;
        List<QuestionGrade> grades = new ArrayList<>(ordered.size());
        Set<QuizAttemptAnswer> answers = new HashSet<>();
        for (QuizQuestion question : ordered) {
            Set<Long> chosen = selected.getOrDefault(question.getId(), Set.of());
            List<Long> correctIds = question.getOptions().stream().filter(QuizOption::isCorrect).map(QuizOption::getId).toList();
            boolean correct = chosen.equals(new HashSet<>(correctIds));
            if (correct) {
                correctQuestions++;
            }
            grades.add(new QuestionGrade(question.getId(), correct, chosen.stream().sorted().toList(), correctIds));
            chosen.forEach(optionId -> answers.add(new QuizAttemptAnswer(question.getId(), optionId)));
        }

        int total = ordered.size();
        int score = total == 0 ? 0 : correctQuestions * 100 / total;
        boolean passed = score >= quiz.getPassPercentage();

        QuizAttempt attempt = attemptRepository.save(new QuizAttempt(quiz, student, score, passed, answers));
        metrics.quizSubmitted(passed);

        Lesson lesson = quiz.getLesson();
        if (passed) {
            completionService.recordLessonCompleted(student, lesson);
        }
        boolean lessonCompleted = passed || completionService.isLessonCompleted(student, lesson);

        // Correct answers are revealed only after passing, so a failed attempt cannot be used to look them up.
        List<QuizAttemptResultDTO.QuestionResult> results = grades.stream()
                .map(grade -> new QuizAttemptResultDTO.QuestionResult(grade.questionId(), grade.correct(),
                        grade.selectedOptionIds(), passed ? grade.correctOptionIds() : null))
                .toList();

        return new QuizAttemptResultDTO(attempt.getId(), quiz.getId(), lesson.getId(), lesson.getCourse().getId(),
                score, quiz.getPassPercentage(), passed, correctQuestions, total, lessonCompleted,
                attempt.getSubmittedAt(), results);
    }

    private record QuestionGrade(Long questionId, boolean correct, List<Long> selectedOptionIds, List<Long> correctOptionIds) {}

    // ---- analytics ----

    @Transactional(readOnly = true)
    public QuizAnalyticsDTO analytics(Quiz quiz) {
        QuizAttemptRepository.QuizStats stats = attemptRepository.summarize(quiz.getId());
        long studentsPassed = attemptRepository.countStudentsPassed(quiz.getId());
        double passRate = stats.getStudentsAttempted() == 0 ? 0.0
                : Math.round(studentsPassed * 1000.0 / stats.getStudentsAttempted()) / 10.0;
        double averageScore = stats.getAverageScore() == null ? 0.0 : Math.round(stats.getAverageScore() * 10.0) / 10.0;
        return new QuizAnalyticsDTO(quiz.getId(), quiz.getLesson().getId(), quiz.getLesson().getCourse().getId(),
                quiz.getTitle(), stats.getAttempts(), stats.getStudentsAttempted(), studentsPassed, passRate, averageScore);
    }
}
