package com.example.minhaj.repositories;

import com.example.minhaj.models.Course;
import com.example.minhaj.models.CourseStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class CourseSpecifications {

    private CourseSpecifications() {}

    // Only courses with this status; a null status matches every course.
    public static Specification<Course> hasStatus(CourseStatus status) {
        return (root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    // Builds a filter from the optional catalog query params; null params are ignored.
    public static Specification<Course> withFilters(CourseSearchQuery search, Long categoryId, Boolean free,
                                                    Double minPrice, Double maxPrice, Double minRating,
                                                    Long instructorId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null) {
                predicates.add(cb.isTrue(searchFunction(CourseSearchFunctions.MATCHES, Boolean.class, search, root, cb)));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (instructorId != null) {
                predicates.add(cb.equal(root.get("instructor").get("id"), instructorId));
            }
            if (free != null) {
                predicates.add(cb.equal(root.get("isFree"), free));
            }
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            }
            if (minRating != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("averageRating"), minRating));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // Orders by full-text relevance (ts_rank; title words weigh more than description words), then newest
    // first, then id so pages are stable. Use with an unsorted Pageable: Spring Data replaces this ordering
    // when the Pageable carries a Sort. Count queries are left unordered.
    public static Specification<Course> orderByRelevance(CourseSearchQuery search, boolean ascending) {
        return (root, query, cb) -> {
            if (query != null && !Long.class.equals(query.getResultType())) {
                Expression<Double> rank = searchFunction(CourseSearchFunctions.RANK, Double.class, search, root, cb);
                query.orderBy(ascending ? cb.asc(rank) : cb.desc(rank),
                        cb.desc(root.get("createdAt")), cb.asc(root.get("id")));
            }
            return cb.conjunction();
        };
    }

    // The user's text reaches SQL only as bound parameters: Hibernate renders cb.literal() strings inline
    // (escaped) inside function calls, while HibernateCriteriaBuilder.value() is always a bind parameter.
    private static <T> Expression<T> searchFunction(String name, Class<T> type, CourseSearchQuery search,
                                                    Root<Course> root, CriteriaBuilder cb) {
        HibernateCriteriaBuilder hcb = (HibernateCriteriaBuilder) cb;
        return cb.function(name, type, root.get("searchVector"),
                hcb.value(search.text()), hcb.value(search.prefixQuery()));
    }
}
