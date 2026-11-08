-- Slice 6: shopping list.
-- "Running low at home" items are derived from stock when the list is read (STATUS
-- products that are LOW/OUT, COUNTED products below min_quantity), so they never go
-- stale. This table only holds what can't be derived:
--   MANUAL rows: things you added (free text, linked to a product when the name matches);
--   AUTO rows:   a running-low product ticked "in the basket".
create table shopping_list_item (
    id          uuid        primary key default uuidv7(),
    source      text        not null check (source in ('MANUAL', 'AUTO')),
    product_id  uuid        references product (id) on delete cascade,
    name        text        check (char_length(btrim(name)) between 1 and 100),
    checked_at  timestamptz,
    created_at  timestamptz not null default now(),
    constraint shopping_manual_has_name check (source <> 'MANUAL' or name is not null),
    constraint shopping_auto_is_a_ticked_product check (
        source <> 'AUTO' or (product_id is not null and checked_at is not null and name is null)
    )
);

-- one tick per running-low product
create unique index shopping_auto_product_uq on shopping_list_item (product_id) where source = 'AUTO';
-- no duplicate manual entries waiting to be bought
create unique index shopping_manual_name_uq on shopping_list_item (lower(name))
    where source = 'MANUAL' and checked_at is null;
