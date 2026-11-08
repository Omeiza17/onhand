-- More household units, named after UN/ECE Recommendation 21 package types
-- (bottle BO, jar JR, roll RO, box BX, bag BG, carton CT, tube TU, tub TB,
-- bar BR, sachet SH, bunch BH). Existing units are unchanged.
alter table product drop constraint product_default_unit_check;

alter table product
    add constraint product_default_unit_check check (default_unit in (
        'piece', 'g', 'kg', 'ml', 'l', 'pack', 'tin',
        'bottle', 'jar', 'roll', 'box', 'bag', 'carton', 'tube', 'tub', 'bar', 'sachet', 'bunch'
    ));
