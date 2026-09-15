-- Printer configuration is device-local, not tenant state.
--
-- Printer bindings (enabled, name, MAC address, paper size — for both the customer
-- receipt and kitchen roles) describe one physical terminal's paired hardware. Storing
-- them on restaurantprofiles meant a shared, tenant-wide row: whichever terminal synced
-- last pushed its own printer MAC onto every other terminal in the restaurant.
--
-- These now live only in the Android app's local `printer_profiles` table, alongside the
-- device-local KOT queue. Print *preferences* that are genuinely business-wide
-- (auto_print_on_success, include_logo_in_print) stay server-side.
ALTER TABLE restaurantprofiles
    DROP COLUMN IF EXISTS printer_enabled,
    DROP COLUMN IF EXISTS printer_name,
    DROP COLUMN IF EXISTS printer_mac,
    DROP COLUMN IF EXISTS paper_size,
    DROP COLUMN IF EXISTS kitchen_printer_enabled,
    DROP COLUMN IF EXISTS kitchen_printer_name,
    DROP COLUMN IF EXISTS kitchen_printer_mac,
    DROP COLUMN IF EXISTS kitchen_printer_paper_size;
