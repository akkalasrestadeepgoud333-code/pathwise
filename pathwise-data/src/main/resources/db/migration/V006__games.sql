-- M4: games. A game is an activity built from a template (rules in pathwise-core) plus a config from a
-- content pack. Play is recorded as an activity attempt; the server replays the action log before any
-- evidence is written. Game evidence is informal: it is never official and never part of an assessment.

create table game_templates (
    key            text        primary key check (key ~ '^[a-z][a-z_]{2,40}$'),
    title          text        not null,
    engine_version smallint    not null check (engine_version >= 1),
    enabled        boolean     not null default true
);
comment on table game_templates is 'Registry of game templates this build can play and verify.';

insert into game_templates (key, title, engine_version) values
    ('traversal_race', 'Traversal Race', 1),
    ('match_pairs', 'Match Pairs', 1),
    ('bug_hunt', 'Bug Hunt', 1);

create table activities (
    id            uuid primary key,
    key           text        not null check (key ~ '^[a-z0-9][a-z0-9-]{1,80}$'),
    kind          text        not null check (kind in ('game')),
    game_template text        references game_templates (key),
    subject_id    uuid        not null references catalog_nodes (id),
    topic_id      uuid        references topics (id),
    title         text        not null check (char_length(btrim(title)) between 2 and 160),
    description   text,
    config        jsonb       not null,
    difficulty    smallint    not null default 2 check (difficulty between 1 and 5),
    est_minutes   smallint    check (est_minutes between 1 and 30),
    status        text        not null default 'published' check (status in ('published', 'archived')),
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    check ((kind = 'game') = (game_template is not null))
);
create unique index activities_key_uq on activities (key);
create index activities_topic_idx on activities (topic_id) where status = 'published';

-- Game attempts reuse activity_attempts.
alter table activity_attempts drop constraint activity_attempts_kind_check;
alter table activity_attempts add constraint activity_attempts_kind_check
    check (kind in ('practice', 'challenge', 'review', 'module_check', 'game'));
alter table activity_attempts drop constraint activity_attempts_status_check;
alter table activity_attempts add constraint activity_attempts_status_check
    check (status in ('in_progress', 'completed', 'abandoned', 'rejected'));

alter table activity_attempts
    add column activity_id    uuid references activities (id),
    add column seed           bigint,
    add column engine_version smallint,
    add column puzzle         jsonb,      -- what was played: kept so results can be explained and re-verified
    add column action_log     jsonb,      -- the client's actions, replayed by the server
    add column verified       boolean,
    add column reject_reason  text,
    add column accuracy       numeric(4, 3) check (accuracy between 0 and 1),
    add column duration_ms    integer check (duration_ms between 0 and 1800000),
    add column relaxed        boolean not null default false;

alter table activity_attempts add constraint activity_attempts_game_check
    check ((kind = 'game') = (activity_id is not null)
           and (kind <> 'game' or (seed is not null and puzzle is not null)));
alter table activity_attempts add constraint activity_attempts_verified_check
    check (status <> 'completed' or kind <> 'game' or verified)
    ;
create index activity_attempts_activity_idx on activity_attempts (activity_id, user_id) where activity_id is not null;

-- Game steps are about a skill, not a bank question.
alter table activity_attempt_items alter column question_id drop not null;
alter table activity_attempt_items
    add column skill_id uuid references skills (id),
    add column note     text;
alter table activity_attempt_items add constraint activity_attempt_items_subject_check
    check (question_id is not null or skill_id is not null);
