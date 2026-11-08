-- Slice 2: current stock and the append-only change log.

-- Current state: one row per product per location.
-- COUNTED products use quantity, STATUS products use status — exactly one is set.
create table stock_item (
    id          uuid           primary key default uuidv7(),
    product_id  uuid           not null references product (id) on delete cascade,
    -- a location that still holds stock cannot be deleted
    location_id uuid           not null references location (id) on delete restrict,
    quantity    numeric(10, 3) check (quantity >= 0),
    status      text           check (status in ('HAVE', 'LOW', 'OUT')),
    expires_on  date,
    updated_at  timestamptz    not null default now(),
    constraint stock_item_product_location_uq unique (product_id, location_id),
    constraint stock_item_quantity_or_status check ((quantity is null) <> (status is null))
);

create index stock_item_location_idx on stock_item (location_id);

-- Every change, for history and undo. Rows are never deleted by the app;
-- undo sets undone_at instead.
create table stock_event (
    id             uuid           primary key default uuidv7(),
    product_id     uuid           not null references product (id) on delete cascade,
    location_id    uuid           not null references location (id) on delete cascade,
    type           text           not null check (type in ('ADDED', 'USED', 'MOVED', 'ADJUSTED', 'STATUS_CHANGED')),
    quantity_delta numeric(10, 3),
    status_from    text           check (status_from in ('HAVE', 'LOW', 'OUT')),
    status_to      text           check (status_to in ('HAVE', 'LOW', 'OUT')),
    -- clock_timestamp, not now(): keeps events ordered even within one transaction
    occurred_at    timestamptz    not null default clock_timestamp(),
    note           text           check (char_length(note) <= 200),
    undone_at      timestamptz
);

create index stock_event_item_idx on stock_event (product_id, location_id, occurred_at desc);
