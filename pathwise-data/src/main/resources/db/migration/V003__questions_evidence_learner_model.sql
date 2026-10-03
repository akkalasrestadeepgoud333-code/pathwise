-- M2: question bank, practice attempts, learning evidence, learner model (skill mastery),
-- learning goals and recommendations. Evidence is append-only; mastery is derived from it.

-- Module sections can now hold inline check questions.
alter table module_sections drop constraint module_sections_kind_check;
alter table module_sections add constraint module_sections_kind_check
    check (kind in ('overview', 'explanation', 'example', 'visual', 'flashcards', 'check',
                    'summary', 'resources', 'common_doubts'));

-- ---------------------------------------------------------------------------------------------
-- Question bank
-- ---------------------------------------------------------------------------------------------
create table questions (
    id            uuid primary key,
    owner_room_id uuid,          -- room-owned questions arrive in M5
    key           text        not null check (key ~ '^[a-z0-9][a-z0-9-]{1,80}$'),
    type          text        not null check (type in ('single_choice', 'multi_choice', 'numeric', 'short_text')),
    stem          text        not null check (char_length(btrim(stem)) between 3 and 4000),
    spec          jsonb       not null default '{}',
    difficulty    smallint    not null check (difficulty between 1 and 5),
    hints         jsonb       not null default '[]',
    explanation   text,
    status        text        not null default 'published' check (status in ('draft', 'published', 'retired')),
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now()
);
comment on column questions.spec is 'Type-specific answer key (numeric: answer, tolerance; short_text: accepted). Never sent to clients before answering.';
create unique index questions_platform_key_uq on questions (key) where owner_room_id is null;
create trigger questions_touch before update on questions for each row execute function pw_touch_updated_at();

create table question_options (
    id          uuid primary key,
    question_id uuid    not null references questions (id) on delete cascade,
    position    integer not null,
    label       text    not null check (char_length(btrim(label)) between 1 and 1000),
    is_correct  boolean not null,
    rationale   text,
    unique (question_id, position)
);
comment on column question_options.rationale is 'Why this option is right or wrong. Shown after answering ("Explain my mistake").';

create table question_skills (
    question_id uuid not null references questions (id) on delete cascade,
    skill_id    uuid not null references skills (id) on delete cascade,
    primary key (question_id, skill_id)
);
create index question_skills_skill_idx on question_skills (skill_id);

-- ---------------------------------------------------------------------------------------------
-- Practice / check attempts (learning activities, never official results)
-- ---------------------------------------------------------------------------------------------
create table activity_attempts (
    id                uuid primary key,
    user_id           uuid        not null references users (id) on delete cascade,
    kind              text        not null check (kind in ('practice', 'challenge', 'review', 'module_check')),
    skill_id          uuid references skills (id) on delete set null,
    module_section_id uuid references module_sections (id) on delete set null,
    recommendation_id uuid,       -- FK added below, after recommendations exists
    status            text        not null default 'in_progress' check (status in ('in_progress', 'completed', 'abandoned')),
    started_at        timestamptz not null default now(),
    completed_at      timestamptz,
    score             numeric(6, 2),
    max_score         numeric(6, 2),
    check ((status = 'completed') = (completed_at is not null))
);
create index activity_attempts_user_idx on activity_attempts (user_id, started_at desc);

create table activity_attempt_items (
    id          bigint generated always as identity primary key,
    attempt_id  uuid        not null references activity_attempts (id) on delete cascade,
    position    integer     not null,
    question_id uuid        not null references questions (id),
    response    jsonb,
    correct     boolean,
    partial     numeric(4, 3) check (partial between 0 and 1),
    hints_used  smallint    not null default 0 check (hints_used between 0 and 10),
    answered_at timestamptz,
    unique (attempt_id, question_id),
    unique (attempt_id, position),
    check ((answered_at is null) = (correct is null))
);
create index activity_attempt_items_question_idx on activity_attempt_items (question_id, answered_at);

-- ---------------------------------------------------------------------------------------------
-- Learning evidence (append-only) and the learner model
-- ---------------------------------------------------------------------------------------------
create table learning_evidence (
    id              bigint generated always as identity primary key,
    user_id         uuid          not null references users (id) on delete cascade,
    skill_id        uuid          not null references skills (id) on delete cascade,
    room_id         uuid,
    source_kind     text          not null check (source_kind in ('practice', 'challenge', 'review', 'module_check',
                                                                  'game', 'assessment', 'assignment', 'flashcard_self',
                                                                  'teacher_observation')),
    attempt_item_id bigint references activity_attempt_items (id) on delete cascade,
    outcome         numeric(4, 3) not null check (outcome between 0 and 1),
    base_weight     numeric(4, 3) not null check (base_weight > 0 and base_weight <= 1),
    difficulty      smallint      not null check (difficulty between 1 and 5),
    hints_used      smallint      not null default 0,
    is_official     boolean       not null default false,
    occurred_at     timestamptz   not null default now()
);
comment on table learning_evidence is 'One observation about one skill. Append-only; game/practice evidence is never official.';
create index learning_evidence_user_skill_idx on learning_evidence (user_id, skill_id, occurred_at desc);
create index learning_evidence_user_time_idx on learning_evidence (user_id, occurred_at desc);

-- Append-only, except that deleting a user (or skill) may cascade.
create function pw_evidence_guard() returns trigger
language plpgsql as $$
begin
    if tg_op = 'UPDATE' then
        raise exception 'learning_evidence is append-only' using errcode = 'P0001';
    end if;
    if pg_trigger_depth() < 2 then
        raise exception 'learning_evidence rows are only removed by cascading deletes' using errcode = 'P0001';
    end if;
    return old;
end;
$$;
create trigger learning_evidence_append_only before update or delete on learning_evidence
    for each row execute function pw_evidence_guard();

create table skill_mastery (
    user_id          uuid          not null references users (id) on delete cascade,
    skill_id         uuid          not null references skills (id) on delete cascade,
    estimate         numeric(4, 3) not null check (estimate between 0 and 1),
    confidence       numeric(4, 3) not null check (confidence between 0 and 1),
    state            text          not null check (state in ('not_started', 'exploring', 'needs_support', 'developing',
                                                             'proficient', 'mastered')),
    evidence_count   integer       not null check (evidence_count >= 0),
    official_count   integer       not null default 0 check (official_count >= 0),
    last_evidence_at timestamptz,
    review_due_at    timestamptz,
    model_version    text          not null,
    updated_at       timestamptz   not null default now(),
    primary key (user_id, skill_id)
);
comment on table skill_mastery is 'Derived model state. Rebuildable from learning_evidence at any time.';
create index skill_mastery_user_state_idx on skill_mastery (user_id, state);

-- ---------------------------------------------------------------------------------------------
-- Goals and recommendations
-- ---------------------------------------------------------------------------------------------
create table learning_goals (
    id          uuid primary key,
    user_id     uuid        not null references users (id) on delete cascade,
    kind        text        not null check (kind in ('master_topic')),
    topic_id    uuid        not null references topics (id) on delete cascade,
    target_date date,
    status      text        not null default 'active' check (status in ('active', 'achieved', 'abandoned')),
    created_at  timestamptz not null default now()
);
create unique index learning_goals_one_active_per_topic on learning_goals (user_id, topic_id) where status = 'active';

create table recommendations (
    id             uuid primary key,
    user_id        uuid          not null references users (id) on delete cascade,
    slot           text          not null check (slot in ('continue', 'strengthen', 'challenge', 'explore', 'revise', 'teacher')),
    rank           smallint      not null default 1 check (rank between 1 and 5),
    source         text          not null default 'engine' check (source in ('engine', 'teacher')),
    target_kind    text          not null check (target_kind in ('module', 'practice', 'challenge', 'review')),
    module_id      uuid references modules (id) on delete cascade,
    skill_id       uuid references skills (id) on delete cascade,
    topic_id       uuid          not null references topics (id) on delete cascade,
    title          text          not null,
    reason_code    text          not null,
    reason_text    text          not null,
    factors        jsonb         not null default '{}',
    score          numeric(6, 3) not null,
    status         text          not null default 'active'
        check (status in ('active', 'accepted', 'completed', 'dismissed', 'snoozed', 'expired')),
    feedback       text check (feedback in ('already_know', 'not_relevant', 'later')),
    snoozed_until  timestamptz,
    engine_version text          not null,
    generated_at   timestamptz   not null default now(),
    acted_at       timestamptz,
    -- exclusive arc: a module target has a module; practice targets have a skill
    check ((target_kind = 'module') = (module_id is not null)),
    check ((target_kind <> 'module') = (skill_id is not null)),
    check ((status = 'snoozed') = (snoozed_until is not null))
);
create index recommendations_user_active_idx on recommendations (user_id, slot, rank) where status = 'active';
create index recommendations_user_history_idx on recommendations (user_id, acted_at desc);

alter table activity_attempts add constraint activity_attempts_recommendation_fk
    foreign key (recommendation_id) references recommendations (id) on delete set null;
