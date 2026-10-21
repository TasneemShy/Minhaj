-- V5: full-text search over course titles and descriptions (GET /api/courses?search=...).
--
-- search_vector is maintained by Postgres itself, so every insert and update (including rows written before
-- this migration) gets a vector with no application code involved. Title words weigh more (A) than
-- description words (B), so ts_rank puts title matches first.
--
-- Text search config 'simple': it lowercases and splits on word boundaries but does no stemming and has no
-- stop words. Courses are written in Arabic as well as English; the 'english' config would stem and drop
-- words by English rules (and do nothing useful for Arabic), and there is no single config for both.
-- The trade-off: "lessons" does not match "lesson"; the API's prefix matching covers most of that.
--
-- No unaccent: it is a trusted extension on Postgres 13+, but unaccent() is STABLE, not IMMUTABLE, so it
-- cannot be used in a generated column without an IMMUTABLE wrapper, and its default rules target Latin
-- accents, not Arabic diacritics.
--
-- Adding a STORED generated column rewrites the table under an ACCESS EXCLUSIVE lock; fine for a course
-- catalog of this size.

ALTER TABLE courses ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
    setweight(to_tsvector('simple', coalesce(title, '')), 'A') ||
    setweight(to_tsvector('simple', coalesce(description, '')), 'B')
) STORED;

CREATE INDEX idx_courses_search_vector ON courses USING GIN (search_vector);
