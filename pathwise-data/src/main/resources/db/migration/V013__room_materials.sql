-- Teachers' own material in a room: written notes (Markdown, sanitised when shown) and PDF files.
-- File bytes live on disk under pathwise.storage.dir with a server-generated name; only metadata is here.

create table room_materials (
    id           uuid primary key,
    room_id      uuid        not null references rooms (id) on delete cascade,
    author_id    uuid        not null references users (id),
    kind         text        not null check (kind in ('note', 'pdf')),
    title        text        not null check (char_length(btrim(title)) between 2 and 160),
    body         text        check (body is null or char_length(body) <= 20000),
    file_name    text        check (file_name is null or char_length(file_name) <= 200),
    file_size    integer     check (file_size is null or file_size between 1 and 10485760),
    storage_key  text        unique check (storage_key is null or storage_key ~ '^[0-9a-f-]{36}\.pdf$'),
    created_at   timestamptz not null default now(),
    deleted_at   timestamptz,
    check ((kind = 'note') = (body is not null)),
    check ((kind = 'pdf') = (storage_key is not null and file_name is not null and file_size is not null))
);
create index room_materials_room_idx on room_materials (room_id, created_at desc) where deleted_at is null;
