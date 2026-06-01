ALTER TABLE sys_security_secret DROP CONSTRAINT IF EXISTS chk_security_secret_status;
ALTER TABLE sys_security_secret ADD CONSTRAINT chk_security_secret_status
    CHECK (status IN ('ACTIVE', 'RETIRED'));
