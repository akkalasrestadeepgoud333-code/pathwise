-- M5 part 2: individual assignments. A teacher sets work, students submit, the teacher grades.
-- A graded submission becomes official evidence (weight 0.9) for the assignment's skills.

create table assignments (
    id           uuid primary key,
    room_id      uuid          not null references rooms (id) on delete cascade,
    author_id    uuid          references users (id) on delete set null,
    kind         text          not null default 'individual' check (kind in ('individual')),
    title        text          not null check (char_length(btrim(title)) between 2 and 160),
    instructions text          check (instructions is null or char_length(instructions) <= 8000),
    max_points   numeric(6, 2) not null check (max_points > 0 and max_points <= 1000),
    due_at       timestamptz,
    allow_late   boolean       not null default true,
    status       text          not null default 'draft' check (status in ('draft', 'published', 'archived')),
    created_at   timestamptz   not null default now(),
    updated_at   timestamptz   not null default now()
);
create index assignments_room_idx on assignments (room_id, due_at);

create table assignment_skills (
    assignment_id uuid not null references assignments (id) on delete cascade,
    skill_id      uuid not null references skills (id) on delete cascade,
    primary key (assignment_id, skill_id)
);

-- One submission per student per assignment; each new hand-in bumps the version.
create table submissions (
    id            uuid primary key,
    assignment_id uuid          not null references assignments (id) on delete cascade,
    user_id       uuid          not null references users (id) on delete cascade,
    version       integer       not null default 0 check (version >= 0),
    body          text          not null default '' check (char_length(body) <= 20000),
    status        text          not null default 'draft' check (status in ('draft', 'submitted', 'returned', 'graded')),
    submitted_at  timestamptz,
    is_late       boolean       not null default false,
    grade         numeric(6, 2) check (grade is null or grade >= 0),
    feedback      text          check (feedback is null or char_length(feedback) <= 8000),
    graded_by     uuid          references users (id) on delete set null,
    graded_at     timestamptz,
    updated_at    timestamptz   not null default now(),
    unique (assignment_id, user_id),
    check ((status = 'graded') = (grade is not null and graded_at is not null)),
    check (status = 'draft' or submitted_at is not null)
);
create index submissions_assignment_idx on submissions (assignment_id, status);

alter table room_unit_items add column assignment_id uuid references assignments (id) on delete cascade;
alter table room_unit_items drop constraint room_unit_items_check;
alter table room_unit_items add constraint room_unit_items_one_ref
    check (num_nonnulls(module_id, assessment_id, activity_id, assignment_id) = 1);

-- Graded assignments are official evidence too (blueprint §12).
alter table learning_evidence add column submission_id uuid references submissions (id) on delete cascade;
alter table learning_evidence drop constraint learning_evidence_one_source;
alter table learning_evidence add constraint learning_evidence_one_source
    check (num_nonnulls(attempt_item_id, response_id, submission_id) <= 1);
alter table learning_evidence drop constraint learning_evidence_official_is_assessment;
alter table learning_evidence add constraint learning_evidence_official_sources
    check (is_official = (source_kind in ('assessment', 'assignment')));
