-- KhanaBook Database Reset and Seed Script
-- Resets all tables except flyway_schema_history and seeds one shop + admin.

DO $$ 
DECLARE 
    r RECORD;
BEGIN
    -- Truncate all tables in the public schema
    FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
        IF r.tablename NOT IN ('flyway_schema_history') THEN
            EXECUTE 'TRUNCATE TABLE ' || quote_ident(r.tablename) || ' CASCADE';
        END IF;
    END LOOP;
END $$;

-- Reset all sequences
DO $$ 
DECLARE 
    r RECORD;
BEGIN
    FOR r IN (SELECT relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = 'public' AND c.relkind = 'S') LOOP
        EXECUTE 'ALTER SEQUENCE ' || quote_ident(r.relname) || ' RESTART WITH 1';
    END LOOP;
END $$;

-- Insert one shop (Restaurant ID 1001)
INSERT INTO restaurantprofiles (
    local_id, device_id, restaurant_id, updated_at, created_at, 
    shop_name, shop_address, whatsapp_number, email, country, currency, 
    is_deleted, server_updated_at, version, last_reset_date, last_reset_date_proper
) VALUES (
    1, 'SYSTEM', 1001, (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    'The First Shop', '123 Main St, City', '919000000001', 'owner@firstshop.com', 'India', 'INR',
    FALSE, (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT, 1, TO_CHAR(CURRENT_DATE, 'YYYY-MM-DD'), CURRENT_DATE
);

-- Insert shop owner (for Restaurant 1001)
INSERT INTO users (
    local_id, device_id, restaurant_id, updated_at, created_at,
    name, email, login_id, auth_provider, role, is_active, version, phone_number
) VALUES (
    1, 'SYSTEM', 1001, (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    'Shop Owner', 'owner@firstshop.com', '919000000001', 'PHONE', 'OWNER', TRUE, 1, '919000000001'
);

-- Insert KBOOK_ADMIN (Global Admin, Restaurant ID 0)
INSERT INTO users (
    local_id, device_id, restaurant_id, updated_at, created_at,
    name, email, login_id, auth_provider, role, is_active, version, phone_number
) VALUES (
    2, 'SYSTEM', 0, (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    'KhanaBook Admin', 'admin@kbook.com', '917000000001', 'PHONE', 'KBOOK_ADMIN', TRUE, 1, '917000000001'
);
