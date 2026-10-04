-- M6: doubts, the public doubt network, moderation and the knowledge base.
-- Authors are always stored (accountability); public identity is a per-thread alias.

create table threads (
    id               uuid primary key,
    scope            text        not null check (scope in ('private_teacher', 'public')),
    room_id          uuid        references rooms (id) on delete cascade,
    author_id        uuid        not null references users (id) on delete cascade,
    category         text        not null check (category in ('concept', 'problem_solving', 'coding', 'debugging',
                                     'resources', 'where_wrong', 'teacher_help')),
    title            text        not null check (char_length(btrim(title)) between 8 and 160),
    body             text        not null check (char_length(btrim(body)) between 10 and 8000),
    topic_id         uuid        references topics (id) on delete set null,
    show_name        boolean     not null default false,
    status           text        not null default 'open' check (status in ('open', 'resolved', 'hidden', 'removed')),
    accepted_post_id uuid,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    search           tsvector generated always as (to_tsvector('english', title || ' ' || body)) stored,
    check ((scope = 'private_teacher') = (room_id is not null))
);
create index threads_public_idx on threads (created_at desc) where scope = 'public' and status in ('open', 'resolved');
create index threads_room_idx on threads (room_id, created_at desc) where room_id is not null;
create index threads_author_idx on threads (author_id, created_at desc);
create index threads_search_idx on threads using gin (search);

create table posts (
    id               uuid primary key,
    thread_id        uuid        not null references threads (id) on delete cascade,
    author_id        uuid        not null references users (id) on delete cascade,
    body             text        not null check (char_length(btrim(body)) between 2 and 8000),
    show_name        boolean     not null default false,
    teacher_verified boolean     not null default false,
    verified_by      uuid        references users (id) on delete set null,
    status           text        not null default 'visible' check (status in ('visible', 'hidden', 'removed')),
    created_at       timestamptz not null default now()
);
create index posts_thread_idx on posts (thread_id, created_at);
alter table threads add constraint threads_accepted_post_fk foreign key (accepted_post_id) references posts (id) on delete set null;

create table post_votes (
    post_id    uuid        not null references posts (id) on delete cascade,
    user_id    uuid        not null references users (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (post_id, user_id)
);

create table anonymous_aliases (
    thread_id uuid    not null references threads (id) on delete cascade,
    user_id   uuid    not null references users (id) on delete cascade,
    alias     integer not null check (alias between 1000 and 9999),
    primary key (thread_id, user_id),
    unique (thread_id, alias)
);

create table user_blocks (
    blocker_id uuid        not null references users (id) on delete cascade,
    blocked_id uuid        not null references users (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (blocker_id, blocked_id),
    check (blocker_id <> blocked_id)
);

create table reports (
    id          uuid primary key,
    reporter_id uuid        not null references users (id) on delete cascade,
    target_kind text        not null check (target_kind in ('thread', 'post')),
    target_id   uuid        not null,
    reason      text        not null check (reason in ('spam', 'harassment', 'inappropriate', 'cheating', 'misinformation', 'self_harm', 'other')),
    details     text        check (details is null or char_length(details) <= 1000),
    status      text        not null default 'open' check (status in ('open', 'actioned', 'dismissed')),
    handled_by  uuid        references users (id) on delete set null,
    handled_at  timestamptz,
    created_at  timestamptz not null default now(),
    unique (reporter_id, target_kind, target_id)
);
create index reports_open_idx on reports (created_at) where status = 'open';

create table moderation_actions (
    id           bigint generated always as identity primary key,
    moderator_id uuid        references users (id) on delete set null,
    report_id    uuid        references reports (id) on delete set null,
    target_kind  text        not null check (target_kind in ('thread', 'post', 'user')),
    target_id    uuid        not null,
    action       text        not null check (action in ('hide', 'restore', 'remove', 'dismiss', 'reveal_identity', 'verify')),
    reason       text        not null check (char_length(btrim(reason)) between 3 and 500),
    created_at   timestamptz not null default now()
);

create table kb_articles (
    id               uuid primary key,
    topic_id         uuid        references topics (id) on delete set null,
    title            text        not null check (char_length(btrim(title)) between 8 and 160),
    body             text        not null check (char_length(btrim(body)) between 20 and 20000),
    source_thread_id uuid        references threads (id) on delete set null,
    editor_id        uuid        references users (id) on delete set null,
    status           text        not null default 'published' check (status in ('draft', 'published', 'retired')),
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    search           tsvector generated always as (to_tsvector('english', title || ' ' || body)) stored
);
create index kb_articles_search_idx on kb_articles using gin (search);
