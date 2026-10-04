-- M10: live doubt sessions. A host (teacher, mentor or admin) opens a session; students join under a
-- session-only alias, queue questions, upvote and raise hands. Text only: no audio or video. Pages poll
-- for updates every few seconds instead of holding a WebSocket open (see FEATURE_STATUS deviations).

create table live_sessions (
    id         uuid primary key,
    host_id    uuid        not null references users (id),
    title      text        not null check (char_length(btrim(title)) between 3 and 120),
    status     text        not null default 'scheduled' check (status in ('scheduled', 'live', 'ended')),
    locked     boolean     not null default false,
    created_at timestamptz not null default now(),
    started_at timestamptz,
    ended_at   timestamptz,
    check (status <> 'live' or started_at is not null),
    check ((status = 'ended') = (ended_at is not null))
);
create index live_sessions_open_idx on live_sessions (created_at desc) where status <> 'ended';

create table session_participants (
    session_id     uuid        not null references live_sessions (id) on delete cascade,
    user_id        uuid        not null references users (id) on delete cascade,
    alias_number   integer     not null check (alias_number >= 1),
    status         text        not null default 'active' check (status in ('active', 'left', 'removed')),
    hand_raised_at timestamptz,
    joined_at      timestamptz not null default now(),
    primary key (session_id, user_id),
    unique (session_id, alias_number)
);
comment on column session_participants.alias_number is 'Shown as "Participant N". Names are never shown in a session.';

create table session_questions (
    id          uuid primary key,
    session_id  uuid        not null references live_sessions (id) on delete cascade,
    user_id     uuid        not null references users (id) on delete cascade,
    body        text        not null check (char_length(btrim(body)) between 3 and 500),
    status      text        not null default 'queued' check (status in ('queued', 'answering', 'answered', 'dismissed')),
    created_at  timestamptz not null default now(),
    answered_at timestamptz
);
create index session_questions_session_idx on session_questions (session_id, created_at);

create table session_question_votes (
    question_id uuid not null references session_questions (id) on delete cascade,
    user_id     uuid not null references users (id) on delete cascade,
    primary key (question_id, user_id)
);
