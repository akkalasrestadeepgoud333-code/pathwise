-- Pathwise baseline (M0): identity and platform tables.
-- Later milestones add their own versioned migrations; applied migrations are never edited.
-- Conventions: uuid (v7, generated in Java) primary keys; text + CHECK instead of enum types;
-- timestamptz everywhere; append-only logs are protected by a trigger.

-- ---------------------------------------------------------------------------------------------
-- Shared trigger functions
-- ---------------------------------------------------------------------------------------------
create function pw_touch_updated_at() returns trigger
language plpgsql as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

create function pw_reject_modification() returns trigger
language plpgsql as $$
begin
    raise exception 'table % is append-only', tg_table_name using errcode = 'P0001';
end;
$$;

-- ---------------------------------------------------------------------------------------------
-- Identity
-- ---------------------------------------------------------------------------------------------
create table users (
    id                   uuid primary key,
    email                text        not null
        check (char_length(email) between 3 and 254 and position('@' in email) > 1),
    email_verified_at    timestamptz,
    password_hash        text        not null,
    display_name         text        not null check (char_length(btrim(display_name)) between 2 and 60),
    status               text        not null default 'active'
        check (status in ('active', 'suspended', 'deleted')),
    trust_level          smallint    not null default 0 check (trust_level between 0 and 4),
    is_verified_educator boolean     not null default false,
    last_seen_at         timestamptz,
    created_at           timestamptz not null default now(),
    updated_at           timestamptz not null default now(),
    deleted_at           timestamptz,
    check ((status = 'deleted') = (deleted_at is not null))
);
comment on table users is 'People with a Pathwise account. Rows are anonymised, never hard-deleted.';
comment on column users.trust_level is 'Community trust 0-4 (blueprint §28); drives posting limits.';

-- One live account per email, case-insensitively.
create unique index users_email_live_uq on users (lower(email)) where deleted_at is null;

create trigger users_touch before update on users
    for each row execute function pw_touch_updated_at();

create table user_platform_roles (
    user_id    uuid        not null references users (id) on delete cascade,
    role       text        not null check (role in ('student', 'teacher', 'mentor', 'admin')),
    granted_by uuid        references users (id),
    created_at timestamptz not null default now(),
    primary key (user_id, role)
);
create index user_platform_roles_role_idx on user_platform_roles (role);

create table sessions (
    id           uuid primary key,
    user_id      uuid        not null references users (id) on delete cascade,
    token_hash   text        not null unique check (char_length(token_hash) = 64),
    client       text        not null check (client in ('web', 'android')),
    user_agent   text        check (char_length(user_agent) <= 300),
    ip_hash      text,
    created_at   timestamptz not null default now(),
    last_used_at timestamptz not null default now(),
    expires_at   timestamptz not null,
    revoked_at   timestamptz,
    reauth_at    timestamptz,
    check (expires_at > created_at)
);
comment on column sessions.token_hash is 'SHA-256 of the bearer/web token. Raw tokens are never stored.';
create index sessions_user_active_idx on sessions (user_id) where revoked_at is null;
create index sessions_expires_idx on sessions (expires_at);

create table auth_tokens (
    id         uuid primary key,
    user_id    uuid        not null references users (id) on delete cascade,
    purpose    text        not null check (purpose in ('password_reset', 'email_verify', 'admin_reset')),
    token_hash text        not null unique check (char_length(token_hash) = 64),
    expires_at timestamptz not null,
    used_at    timestamptz,
    created_at timestamptz not null default now()
);
create index auth_tokens_user_idx on auth_tokens (user_id);

create table learner_profiles (
    user_id                 uuid primary key references users (id) on delete cascade,
    timezone                text        not null default 'Asia/Kolkata',
    daily_goal_minutes      smallint    check (daily_goal_minutes between 5 and 240),
    preferred_modalities    text[]      not null default '{}',
    onboarding_completed_at timestamptz,
    leaderboard_opt_in      boolean     not null default false,
    ai_opt_in               boolean     not null default true,
    theme                   text        not null default 'system' check (theme in ('system', 'light', 'dark')),
    created_at              timestamptz not null default now(),
    updated_at              timestamptz not null default now()
);
comment on column learner_profiles.preferred_modalities is 'Stated by the student. Never inferred.';

create trigger learner_profiles_touch before update on learner_profiles
    for each row execute function pw_touch_updated_at();

-- ---------------------------------------------------------------------------------------------
-- Platform
-- ---------------------------------------------------------------------------------------------
create table domain_events (
    id           bigint generated always as identity primary key,
    type         text        not null check (char_length(type) between 3 and 100),
    payload      jsonb       not null default '{}',
    created_at   timestamptz not null default now(),
    available_at timestamptz not null default now(),
    processed_at timestamptz,
    failed_at    timestamptz,
    attempts     integer     not null default 0 check (attempts >= 0),
    last_error   text
);
comment on table domain_events is 'Transactional outbox: written in the same transaction as the change it describes.';
create index domain_events_pending_idx on domain_events (available_at)
    where processed_at is null and failed_at is null;

create table audit_log (
    id          bigint generated always as identity primary key,
    actor_id    uuid        references users (id),
    action      text        not null check (char_length(action) between 3 and 100),
    entity_kind text,
    entity_id   text,
    metadata    jsonb       not null default '{}',
    ip_hash     text,
    created_at  timestamptz not null default now()
);
create index audit_log_actor_idx on audit_log (actor_id, created_at desc);
create index audit_log_entity_idx on audit_log (entity_kind, entity_id);

create trigger audit_log_append_only before update or delete on audit_log
    for each row execute function pw_reject_modification();

create table platform_settings (
    key        text primary key check (key ~ '^[a-z][a-z0-9_.]{2,80}$'),
    value      jsonb       not null,
    updated_by uuid        references users (id),
    updated_at timestamptz not null default now()
);

insert into platform_settings (key, value) values
    ('registration.open', 'true'),
    ('ai.enabled', 'false');
