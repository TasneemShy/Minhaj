-- V3: courses get a draft/published lifecycle.
-- Every existing course was already public, so it becomes PUBLISHED with published_at = created_at
-- (nothing currently visible disappears). New courses are created as DRAFT by the application.

ALTER TABLE courses ADD COLUMN status varchar(20);
ALTER TABLE courses ADD COLUMN published_at timestamp(6) without time zone;

UPDATE courses SET status = 'PUBLISHED', published_at = created_at;

ALTER TABLE courses ALTER COLUMN status SET NOT NULL;
ALTER TABLE courses ADD CONSTRAINT courses_status_check CHECK (status IN ('DRAFT', 'PUBLISHED'));
