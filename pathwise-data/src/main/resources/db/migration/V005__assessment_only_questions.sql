-- Questions written for official assessments must not appear in practice, or students would see test
-- items in advance.
alter table questions add column practice_enabled boolean not null default true;
comment on column questions.practice_enabled is 'False for assessment-only items: excluded from practice selection.';
