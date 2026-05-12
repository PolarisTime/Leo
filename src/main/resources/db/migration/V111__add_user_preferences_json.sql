ALTER TABLE sys_user
    ADD COLUMN IF NOT EXISTS preferences_json TEXT;
