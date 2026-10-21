package com.example.minhaj.repositories;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CourseSearchQueryTest {

    @Test
    void blankInputMeansNoSearch() {
        assertThat(CourseSearchQuery.parse(null)).isNull();
        assertThat(CourseSearchQuery.parse("   ")).isNull();
        assertThat(CourseSearchQuery.parse("\0")).isNull();
    }

    @Test
    void plainWordsBecomeAndedPrefixTerms() {
        CourseSearchQuery query = CourseSearchQuery.parse("  Spring   bo ");
        assertThat(query.text()).isEqualTo("Spring   bo");
        assertThat(query.prefixQuery()).isEqualTo("Spring:* & bo:*");
    }

    @Test
    void arabicWordsWithDiacriticsArePlain() {
        assertThat(CourseSearchQuery.parse("مُقَدِّمَة البرمجة").prefixQuery()).isEqualTo("مُقَدِّمَة:* & البرمجة:*");
    }

    @Test
    void nulIsDropped() {
        assertThat(CourseSearchQuery.parse("java\0script").text()).isEqualTo("java script");
    }

    // Websearch syntax and anything that could be a to_tsquery operator turns the prefix fallback off.
    @ParameterizedTest
    @ValueSource(strings = {"\"spring boot\"", "java -spring", "java or kotlin", "java OR kotlin",
            "') OR 1=1 --", ":*&|!", "c++", "node.js", "a:* | b", "x'y", "back\\slash"})
    void syntaxOrPunctuationDisablesPrefixQuery(String input) {
        CourseSearchQuery query = CourseSearchQuery.parse(input);
        assertThat(query.text()).isEqualTo(input.trim());
        assertThat(query.prefixQuery()).isEmpty();
    }
}
