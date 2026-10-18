package com.example.minhaj.repositories;

import com.example.minhaj.models.Course;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class CourseSpecifications {

    private CourseSpecifications() {}

    // Builds a filter from the optional catalog query params; null params are ignored.
    public static Specification<Course> withFilters(String search, Long categoryId, Boolean free,
                                                    Double minPrice, Double maxPrice, Double minRating) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.trim().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("title")), "%" + search.trim().toLowerCase() + "%"));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
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
}
