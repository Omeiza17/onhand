-- 2026-11-08 (4) spec change: Edit product.

-- Products are archived, never hard-deleted (stock events reference them).
alter table product add column archived_at timestamptz;

-- Names stay unique (case-insensitive) among products that are not archived,
-- so an archived "Milk" doesn't block adding a new one.
drop index product_name_uq;
create unique index product_name_uq on product (lower(name)) where archived_at is null;

-- MOVED events record where the stock came from (location_id is where it went).
alter table stock_event add column from_location_id uuid references location (id) on delete cascade;

-- For moves and mode/unit changes: the product's tracking fields before the change
-- (tracking mode, unit, minimum, step size, default location), so undo can restore them.
alter table stock_event add column product_before jsonb;
