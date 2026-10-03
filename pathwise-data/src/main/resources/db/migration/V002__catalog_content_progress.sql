-- M1: catalogue (levels → programs → courses → subjects → topics → skills), prerequisite graph,
-- learning modules with typed sections, flashcards, resources, content packs and reading progress.
-- Progress here is COVERAGE (what was studied), never mastery: mastery comes from evidence (M2).

create table catalog_nodes (
    id          uuid primary key,
    parent_id   uuid references catalog_nodes (id),
    kind        text        not null check (kind in ('level', 'program', 'course', 'subject')),
    key         text        not null unique check (key ~ '^[a-z0-9][a-z0-9-]{1,80}$'),
    title       text        not null check (char_length(btrim(title)) between 2 and 120),
    description text,
    position    integer     not null default 0,
    status      text        not null default 'published' check (status in ('draft', 'published', 'archived')),
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    check ((kind = 'level') = (parent_id is null))
);
comment on table catalog_nodes is 'Organisational hierarchy. Allowed parents: subject→course→program→level (enforced by trigger).';
create index catalog_nodes_parent_idx on catalog_nodes (parent_id, position);
create trigger catalog_nodes_touch before update on catalog_nodes for each row execute function pw_touch_updated_at();

create function pw_check_catalog_parent() returns trigger
language plpgsql as $$
declare
    parent_kind text;
begin
    if new.parent_id is null then
        return new;
    end if;
    select kind into parent_kind from catalog_nodes where id = new.parent_id;
    if not ((new.kind = 'program' and parent_kind = 'level')
         or (new.kind = 'course' and parent_kind = 'program')
         or (new.kind = 'subject' and parent_kind = 'course')) then
        raise exception 'a % cannot be placed under a %', new.kind, coalesce(parent_kind, 'nothing') using errcode = '23514';
    end if;
    return new;
end;
$$;
create trigger catalog_nodes_parent_kind before insert or update on catalog_nodes
    for each row execute function pw_check_catalog_parent();

create table topics (
    id              uuid primary key,
    subject_id      uuid        not null references catalog_nodes (id),
    parent_topic_id uuid references topics (id),
    key             text        not null check (key ~ '^[a-z0-9][a-z0-9-]{1,80}$'),
    title           text        not null check (char_length(btrim(title)) between 2 and 120),
    summary         text,
    position        integer     not null default 0,
    is_optional     boolean     not null default false,
    is_advanced     boolean     not null default false,
    status          text        not null default 'published' check (status in ('draft', 'published', 'archived')),
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    unique (subject_id, key),
    check (parent_topic_id is distinct from id)
);
create index topics_subject_idx on topics (subject_id, position);
create index topics_parent_idx on topics (parent_topic_id);
create trigger topics_touch before update on topics for each row execute function pw_touch_updated_at();

create table skills (
    id          uuid primary key,
    topic_id    uuid          not null references topics (id) on delete cascade,
    key         text          not null check (key ~ '^[a-z0-9][a-z0-9-]{1,80}$'),
    title       text          not null check (char_length(btrim(title)) between 2 and 160),
    description text,
    is_core     boolean       not null default true,
    weight      numeric(4, 2) not null default 1 check (weight > 0 and weight <= 10),
    position    integer       not null default 0,
    created_at  timestamptz   not null default now(),
    unique (topic_id, key)
);
comment on table skills is 'The unit of mastery. Questions, activities and modules are tagged with skills.';

create table topic_prerequisites (
    topic_id              uuid not null references topics (id) on delete cascade,
    prerequisite_topic_id uuid not null references topics (id) on delete cascade,
    strength              text not null default 'required' check (strength in ('required', 'recommended')),
    primary key (topic_id, prerequisite_topic_id),
    check (topic_id <> prerequisite_topic_id)
);
comment on table topic_prerequisites is 'Directed acyclic graph. Acyclicity is validated before every write (application + test).';
create index topic_prerequisites_prereq_idx on topic_prerequisites (prerequisite_topic_id);

create table modules (
    id            uuid primary key,
    topic_id      uuid        not null references topics (id),
    owner_room_id uuid,        -- room-owned modules arrive in M5; the FK is added with the rooms table
    author_id     uuid references users (id),
    key           text        not null check (key ~ '^[a-z0-9][a-z0-9-]{1,80}$'),
    title         text        not null check (char_length(btrim(title)) between 2 and 160),
    summary       text,
    objectives    text[]      not null default '{}',
    est_minutes   smallint    not null default 10 check (est_minutes between 1 and 240),
    difficulty    smallint    not null default 2 check (difficulty between 1 and 5),
    position      integer     not null default 0,
    status        text        not null default 'draft' check (status in ('draft', 'published', 'archived')),
    version       integer     not null default 1 check (version >= 1),
    published_at  timestamptz,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    deleted_at    timestamptz,
    check ((status = 'published') <= (published_at is not null))
);
create unique index modules_platform_key_uq on modules (key) where owner_room_id is null;
create unique index modules_room_key_uq on modules (owner_room_id, key) where owner_room_id is not null;
create index modules_topic_idx on modules (topic_id, position) where deleted_at is null;
create trigger modules_touch before update on modules for each row execute function pw_touch_updated_at();

create table module_skills (
    module_id uuid not null references modules (id) on delete cascade,
    skill_id  uuid not null references skills (id) on delete cascade,
    primary key (module_id, skill_id)
);
create index module_skills_skill_idx on module_skills (skill_id);

create table module_sections (
    id          uuid primary key,
    module_id   uuid        not null references modules (id) on delete cascade,
    key         text        not null check (key ~ '^[a-z0-9][a-z0-9-]{0,80}$'),
    position    integer     not null,
    kind        text        not null check (kind in ('overview', 'explanation', 'example', 'visual', 'flashcards',
                                                    'summary', 'resources', 'common_doubts')),
    title       text        not null check (char_length(btrim(title)) between 1 and 160),
    body        jsonb       not null default '{}',
    is_required boolean     not null default true,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    unique (module_id, key)
);
comment on column module_sections.body is 'Typed per kind; validated by the content service before every write.';
create index module_sections_module_idx on module_sections (module_id, position);
create trigger module_sections_touch before update on module_sections for each row execute function pw_touch_updated_at();

create table flashcards (
    id         uuid primary key,
    module_id  uuid    not null references modules (id) on delete cascade,
    skill_id   uuid references skills (id) on delete set null,
    front      text    not null check (char_length(btrim(front)) between 1 and 500),
    back       text    not null check (char_length(btrim(back)) between 1 and 1000),
    position   integer not null default 0
);
create index flashcards_module_idx on flashcards (module_id, position);

create table resources (
    id           uuid primary key,
    module_id    uuid    not null references modules (id) on delete cascade,
    kind         text    not null check (kind in ('link', 'video_link')),
    title        text    not null check (char_length(btrim(title)) between 2 and 200),
    url          text    not null check (url ~ '^https://'),
    licence_note text,
    position     integer not null default 0
);
create index resources_module_idx on resources (module_id, position);

create table content_packs (
    key         text primary key check (key ~ '^[a-z0-9][a-z0-9-]{1,80}$'),
    version     integer     not null check (version >= 1),
    checksum    text        not null check (char_length(checksum) = 64),
    imported_at timestamptz not null default now(),
    imported_by uuid references users (id)
);
comment on table content_packs is 'Which version of each bundled content pack is loaded; re-import is skipped when the checksum matches.';

create table module_progress (
    user_id         uuid        not null references users (id) on delete cascade,
    module_id       uuid        not null references modules (id) on delete cascade,
    started_at      timestamptz not null default now(),
    last_section_id uuid references module_sections (id) on delete set null,
    last_seen_at    timestamptz not null default now(),
    completed_at    timestamptz,
    primary key (user_id, module_id)
);
comment on table module_progress is 'Coverage, not mastery.';
create index module_progress_user_recent_idx on module_progress (user_id, last_seen_at desc);

create table section_progress (
    user_id      uuid        not null references users (id) on delete cascade,
    section_id   uuid        not null references module_sections (id) on delete cascade,
    completed_at timestamptz not null default now(),
    primary key (user_id, section_id)
);
