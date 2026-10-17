CREATE UNIQUE INDEX IF NOT EXISTS ux_menuitems_restaurant_category_name_norm
ON menuitems (
    restaurant_id,
    category_id,
    lower(regexp_replace(btrim(name), '\s+', ' ', 'g'))
)
WHERE is_deleted = false;
