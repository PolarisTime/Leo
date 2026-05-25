-- RBAC2: Separation of Duties — 互斥角色约束
CREATE TABLE IF NOT EXISTS sys_role_conflict (
    id BIGINT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    conflict_role_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted_flag BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_role_conflict_pair UNIQUE (role_id, conflict_role_id),
    CONSTRAINT fk_role_conflict_role FOREIGN KEY (role_id) REFERENCES sys_role(id),
    CONSTRAINT fk_role_conflict_conflict_role FOREIGN KEY (conflict_role_id) REFERENCES sys_role(id),
    CONSTRAINT chk_role_conflict_self CHECK (role_id <> conflict_role_id)
);

CREATE INDEX IF NOT EXISTS idx_role_conflict_role_id ON sys_role_conflict(role_id) WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_role_conflict_conflict_role_id ON sys_role_conflict(conflict_role_id) WHERE deleted_flag = FALSE;
