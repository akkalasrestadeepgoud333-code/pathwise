-- M8: tutor conversations. Private to the student (teachers can't read them); the student can delete them.

create table ai_conversations (
    id          uuid primary key,
    user_id     uuid        not null references users (id) on delete cascade,
    mode        text        not null check (mode in ('tutor', 'hint', 'explain', 'resource')),
    title       text        not null check (char_length(title) between 1 and 160),
    context_ref jsonb       not null default '{}'::jsonb,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    deleted_at  timestamptz
);
create index ai_conversations_user_idx on ai_conversations (user_id, updated_at desc) where deleted_at is null;

create table ai_messages (
    id              bigint generated always as identity primary key,
    conversation_id uuid        not null references ai_conversations (id) on delete cascade,
    role            text        not null check (role in ('user', 'assistant')),
    content         text        not null check (char_length(content) between 1 and 12000),
    source          text        check (source in ('ai', 'guided')),
    model           text,
    created_at      timestamptz not null default now(),
    check ((role = 'assistant') = (source is not null))
);
create index ai_messages_conversation_idx on ai_messages (conversation_id, id);
