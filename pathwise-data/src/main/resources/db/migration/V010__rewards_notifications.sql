-- M7: XP, streak days, badges, mastery transitions and in-app notifications.

-- Every XP change is an event with a unique key, so handlers can run twice without double-counting.
create table xp_events (
    id         bigint generated always as identity primary key,
    user_id    uuid        not null references users (id) on delete cascade,
    rule       text        not null,
    xp         integer     not null check (xp between -1000 and 1000),
    unique_key text        not null,
    note       text,
    created_at timestamptz not null default now()
);
create unique index xp_events_key_uq on xp_events (unique_key);
create index xp_events_user_idx on xp_events (user_id, created_at desc);

-- One row per learner per local day with activity; meaningful = counts toward the streak.
create table activity_days (
    user_id        uuid    not null references users (id) on delete cascade,
    day            date    not null,
    practice_count integer not null default 0 check (practice_count >= 0),
    meaningful     boolean not null default false,
    primary key (user_id, day)
);

create table badge_awards (
    user_id    uuid        not null references users (id) on delete cascade,
    badge_key  text        not null,
    awarded_at timestamptz not null default now(),
    snapshot   jsonb       not null,
    primary key (user_id, badge_key)
);

-- Kept so growth badges (e.g. Comeback) can be checked: every change of a skill's state.
create table mastery_transitions (
    id         bigint generated always as identity primary key,
    user_id    uuid        not null references users (id) on delete cascade,
    skill_id   uuid        not null references skills (id) on delete cascade,
    from_state text        not null,
    to_state   text        not null,
    at         timestamptz not null default now()
);
create index mastery_transitions_user_idx on mastery_transitions (user_id, skill_id, at);

create table notifications (
    id         uuid primary key,
    user_id    uuid        not null references users (id) on delete cascade,
    type       text        not null,
    title      text        not null check (char_length(title) between 1 and 200),
    body       text        check (body is null or char_length(body) <= 500),
    link       text        check (link is null or link ~ '^/'),
    group_key  text,
    created_at timestamptz not null default now(),
    read_at    timestamptz
);
create index notifications_user_idx on notifications (user_id, created_at desc);
create index notifications_unread_idx on notifications (user_id) where read_at is null;
