-- V2: lessons get an explicit order inside their course.
-- Existing lessons are numbered 1..n per course in id order (the order they were created in).

ALTER TABLE lessons ADD COLUMN position integer;

UPDATE lessons l
SET position = numbered.position
FROM (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY course_id ORDER BY id) AS position
    FROM lessons
) numbered
WHERE l.id = numbered.id;

ALTER TABLE lessons ALTER COLUMN position SET NOT NULL;

-- Deferred so a reorder (or closing the gap after a delete) can pass through duplicate positions
-- inside one transaction; uniqueness is checked at commit.
ALTER TABLE lessons ADD CONSTRAINT lessons_course_id_position_key
    UNIQUE (course_id, position) DEFERRABLE INITIALLY DEFERRED;
