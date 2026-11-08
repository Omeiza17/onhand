-- 2026-11-08 (2) spec change: per-product step size for one-tap -/+.
-- Null means the unit's default (piece/pack/tin 1, g 100, kg 0.5, ml 250, l 0.5),
-- which the application applies. COUNTED products only.
alter table product
    add column step_size numeric(10, 3) check (step_size > 0);

alter table product
    add constraint product_step_size_counted_only check (tracking_mode = 'COUNTED' or step_size is null);
