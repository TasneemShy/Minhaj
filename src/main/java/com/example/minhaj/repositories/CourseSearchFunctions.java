package com.example.minhaj.repositories;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.BasicTypeRegistry;
import org.hibernate.type.StandardBasicTypes;

// Postgres full-text search functions for Criteria queries (see CourseSpecifications.matchesSearch).
// JPA has no syntax for the @@ operator, so these are registered as Hibernate pattern functions; the
// ?n placeholders are rendered as bound parameters or column references, never as concatenated input.
// Registered through META-INF/services/org.hibernate.boot.model.FunctionContributor.
//
// Arguments: ?1 the tsvector column, ?2 the raw user text for websearch_to_tsquery, ?3 a to_tsquery
// prefix query built by CourseSearchQuery (only letters, digits and our own operators). The two
// queries are OR-ed with tsquery's || operator; an empty query on either side leaves the other one.
public class CourseSearchFunctions implements FunctionContributor {

    static final String MATCHES = "course_search_matches";
    static final String RANK = "course_search_rank";

    private static final String QUERY = "(websearch_to_tsquery('simple', ?2) || to_tsquery('simple', ?3))";

    @Override
    public void contributeFunctions(FunctionContributions contributions) {
        BasicTypeRegistry types = contributions.getTypeConfiguration().getBasicTypeRegistry();
        contributions.getFunctionRegistry().registerPattern(
                MATCHES, "(?1 @@ " + QUERY + ")", types.resolve(StandardBasicTypes.BOOLEAN));
        contributions.getFunctionRegistry().registerPattern(
                RANK, "ts_rank(?1, " + QUERY + ")", types.resolve(StandardBasicTypes.DOUBLE));
    }
}
