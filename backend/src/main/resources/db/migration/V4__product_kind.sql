-- 2026-11-08 spec change: scope widened from food to all used-up-and-re-bought
-- household items. Existing products become FOOD.
alter table product
    add column kind text not null default 'FOOD'
        check (kind in ('FOOD', 'HOUSEHOLD'));
