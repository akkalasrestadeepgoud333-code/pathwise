-- M5: rooms. A room is a class: a shared curriculum (units → items) that each student moves through on
-- their own path. The owner is a membership row, not a column, so ownership can move atomically.

create table rooms (
    id                       uuid primary key,
    name                     text        not null check (char_length(btrim(name)) between 2 and 120),
    description              text        check (description is null or char_length(description) <= 2000),
    subject_id               uuid        references catalog_nodes (id),
    code                     text        not null check (code ~ '^[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{8}$'),
    join_policy              text        not null default 'approval' check (join_policy in ('open', 'approval', 'invite_only')),
    students_see_class_stats boolean     not null default false,
    created_at               timestamptz not null default now(),
    updated_at               timestamptz not null default now(),
    archived_at              timestamptz
);
create unique index rooms_code_uq on rooms (code);

create table room_memberships (
    room_id     uuid        not null references rooms (id) on delete cascade,
    user_id     uuid        not null references users (id) on delete cascade,
    role        text        not null check (role in ('owner', 'co_teacher', 'student')),
    status      text        not null check (status in ('pending', 'active', 'removed')),
    perms       jsonb       not null default '{}'::jsonb,
    joined_at   timestamptz,
    approved_by uuid        references users (id) on delete set null,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    primary key (room_id, user_id),
    check ((status = 'active') = (joined_at is not null)),
    check (role = 'co_teacher' or perms = '{}'::jsonb)
);
create unique index room_memberships_one_owner on room_memberships (room_id) where role = 'owner' and status = 'active';
create index room_memberships_user_idx on room_memberships (user_id, status);

create table room_invites (
    id          uuid primary key,
    room_id     uuid        not null references rooms (id) on delete cascade,
    token_hash  text        not null,
    role        text        not null check (role in ('student', 'co_teacher')),
    max_uses    integer     not null check (max_uses between 1 and 1000),
    uses        integer     not null default 0 check (uses >= 0),
    expires_at  timestamptz not null,
    revoked_at  timestamptz,
    created_by  uuid        references users (id) on delete set null,
    created_at  timestamptz not null default now(),
    check (uses <= max_uses)
);
create unique index room_invites_token_uq on room_invites (token_hash);

create table room_units (
    id         uuid primary key,
    room_id    uuid        not null references rooms (id) on delete cascade,
    title      text        not null check (char_length(btrim(title)) between 1 and 120),
    position   integer     not null,
    publish_at timestamptz,
    created_at timestamptz not null default now()
);
create index room_units_room_idx on room_units (room_id, position);

create table room_unit_items (
    id            uuid primary key,
    unit_id       uuid        not null references room_units (id) on delete cascade,
    position      integer     not null,
    module_id     uuid        references modules (id),
    assessment_id uuid        references assessments (id),
    activity_id   uuid        references activities (id),
    publish_at    timestamptz,
    is_required   boolean     not null default true,
    created_at    timestamptz not null default now(),
    check (num_nonnulls(module_id, assessment_id, activity_id) = 1)
);
create index room_unit_items_unit_idx on room_unit_items (unit_id, position);

create table announcements (
    id         uuid primary key,
    room_id    uuid        not null references rooms (id) on delete cascade,
    author_id  uuid        references users (id) on delete set null,
    body       text        not null check (char_length(btrim(body)) between 1 and 4000),
    pinned     boolean     not null default false,
    created_at timestamptz not null default now(),
    deleted_at timestamptz
);
create index announcements_room_idx on announcements (room_id, created_at desc) where deleted_at is null;

-- Room-owned assessments: same tables as platform ones, scoped by owner_room_id.
alter table assessments
    add constraint assessments_owner_room_fk foreign key (owner_room_id) references rooms (id) on delete cascade,
    add column created_by uuid references users (id) on delete set null,
    add column results_released_at timestamptz;
create unique index assessments_room_key_uq on assessments (key) where owner_room_id is not null;
create index assessments_room_idx on assessments (owner_room_id) where owner_room_id is not null;
