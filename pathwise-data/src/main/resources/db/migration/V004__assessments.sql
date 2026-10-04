-- M3: official assessments (diagnostics, quizzes, topic tests). Kept strictly separate from practice:
-- results live only here, and only graded attempts produce official evidence.

create table assessments (
    id              uuid primary key,
    owner_room_id   uuid,        -- room-owned (teacher) assessments arrive in M5
    key             text        not null check (key ~ '^[a-z0-9][a-z0-9-]{1,80}$'),
    subject_id      uuid        not null references catalog_nodes (id),
    topic_id        uuid references topics (id),
    kind            text        not null check (kind in ('diagnostic', 'quiz', 'topic_test', 'practice_test', 'challenge')),
    title           text        not null check (char_length(btrim(title)) between 2 and 160),
    instructions    text,
    time_limit_sec  integer check (time_limit_sec between 60 and 14400),
    max_attempts    smallint    not null default 1 check (max_attempts between 1 and 20),
    shuffle_items   boolean     not null default false,
    ai_policy       text        not null default 'blocked' check (ai_policy in ('blocked', 'hints_only', 'allowed')),
    feedback_policy text        not null default 'immediate' check (feedback_policy in ('immediate', 'after_due', 'manual_release')),
    available_from  timestamptz,
    due_at          timestamptz,
    status          text        not null default 'published' check (status in ('draft', 'published', 'archived')),
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    check (due_at is null or available_from is null or due_at > available_from),
    check (feedback_policy <> 'after_due' or due_at is not null)
);
create unique index assessments_platform_key_uq on assessments (key) where owner_room_id is null;
create index assessments_subject_idx on assessments (subject_id) where status = 'published';
create trigger assessments_touch before update on assessments for each row execute function pw_touch_updated_at();

create table assessment_items (
    id            uuid primary key,
    assessment_id uuid          not null references assessments (id) on delete cascade,
    question_id   uuid          not null references questions (id),
    position      integer       not null,
    points        numeric(6, 2) not null default 1 check (points > 0),
    unique (assessment_id, question_id),
    unique (assessment_id, position)
);

create table assessment_attempts (
    id            uuid primary key,
    assessment_id uuid          not null references assessments (id),
    user_id       uuid          not null references users (id) on delete cascade,
    attempt_no    smallint      not null check (attempt_no >= 1),
    seed          bigint        not null,
    started_at    timestamptz   not null default now(),
    deadline_at   timestamptz,
    submitted_at  timestamptz,
    status        text          not null default 'in_progress' check (status in ('in_progress', 'graded', 'voided')),
    auto_submitted boolean      not null default false,
    score         numeric(7, 2),
    max_score     numeric(7, 2),
    is_late       boolean       not null default false,
    unique (assessment_id, user_id, attempt_no),
    check ((status = 'graded') = (submitted_at is not null)),
    check (deadline_at is null or deadline_at > started_at)
);
create index assessment_attempts_user_idx on assessment_attempts (user_id, started_at desc);
create index assessment_attempts_open_idx on assessment_attempts (deadline_at) where status = 'in_progress';
-- At most one attempt in progress per student per assessment.
create unique index assessment_attempts_one_open on assessment_attempts (assessment_id, user_id) where status = 'in_progress';

create table attempt_responses (
    id                uuid primary key,
    attempt_id        uuid          not null references assessment_attempts (id) on delete cascade,
    item_id           uuid          not null references assessment_items (id),
    response          jsonb,
    is_correct        boolean,
    credit            numeric(4, 3) check (credit between 0 and 1),
    points_awarded    numeric(6, 2),
    question_snapshot jsonb,
    saved_at          timestamptz   not null default now(),
    unique (attempt_id, item_id)
);
comment on column attempt_responses.question_snapshot is 'Exactly what was graded (stem, options, key) — later content edits never change past results.';

-- Assessment evidence points at its response (exclusive arc with practice items).
alter table learning_evidence add column response_id uuid references attempt_responses (id) on delete cascade;
alter table learning_evidence add constraint learning_evidence_one_source
    check (num_nonnulls(attempt_item_id, response_id) <= 1);
alter table learning_evidence add constraint learning_evidence_official_is_assessment
    check (is_official = (source_kind = 'assessment'));
