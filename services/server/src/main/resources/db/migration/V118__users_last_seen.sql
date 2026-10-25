-- User heartbeat: last authenticated activity for a signed-in user.
--
-- Written only server-side, from a JWT-authenticated POST /auth/heartbeat, and
-- throttled to one write a minute inside the UPDATE itself — the same shape as
-- restaurant_terminal.last_seen_at (V82). Nothing a client sends can set it.
--
-- Deliberately NOT mapped on the User entity: users travel in the master-sync
-- payload as UserDTO, so a mapped field would enter that contract and the device
-- schema. Presence stays a server-side column.
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_seen_at BIGINT;
CREATE INDEX IF NOT EXISTS idx_users_last_seen ON users(restaurant_id, last_seen_at);
