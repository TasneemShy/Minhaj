package com.example.minhaj.repositories;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// The catalog's ?search= text, prepared for CourseSearchFunctions.
//
// text: the trimmed user input, passed as-is (as a bound parameter) to websearch_to_tsquery, which never
// raises a syntax error: "quoted phrases", OR, and -exclusions work, anything else is ignored.
//
// prefixQuery: a to_tsquery fallback so a partial last word ("spring bo") still finds "Spring Boot".
// Every word becomes word:* and the words are AND-ed. It is built only for plain input (letters, digits
// and spaces, no OR); when the user writes quotes, minus signs or other punctuation, websearch syntax
// decides alone, so a prefix match can't undo an exclusion or loosen a phrase. Because the tokens contain
// nothing but letters and digits, the string is always a valid to_tsquery expression.
public record CourseSearchQuery(String text, String prefixQuery) {

    // Letters (including Arabic), combining marks (Arabic diacritics), digits and whitespace.
    private static final Pattern PLAIN = Pattern.compile("[\\p{L}\\p{M}\\p{N}\\s]+");

    // null for missing or blank input: no search filter. NUL characters (?search=%00) are dropped because
    // Postgres rejects them in text parameters.
    public static CourseSearchQuery parse(String search) {
        if (search == null) {
            return null;
        }
        String text = search.replace("\0", " ").trim();
        if (text.isEmpty()) {
            return null;
        }
        return new CourseSearchQuery(text, prefixQueryFor(text));
    }

    private static String prefixQueryFor(String text) {
        if (!PLAIN.matcher(text).matches()) {
            return "";
        }
        String[] words = text.split("\\s+");
        if (Arrays.stream(words).anyMatch(word -> word.toLowerCase(Locale.ROOT).equals("or"))) {
            return "";
        }
        return Arrays.stream(words).map(word -> word + ":*").collect(Collectors.joining(" & "));
    }
}
