-- 打印模板唯一约束与乐观锁
-- 1) 部分唯一索引：未删除模板中 (bill_type, template_code) 全局唯一（跨结算主体也唯一）。
--    基线数据已核对无重复组合；基线中既有的含结算主体唯一索引允许不同结算主体复用同一编码，
--    本索引更严格，两者并存，既有索引保持不动（非破坏性变更约束）。
CREATE UNIQUE INDEX IF NOT EXISTS uk_print_template_bill_type_code_active
    ON sys_print_template (bill_type, template_code)
    WHERE deleted_flag = FALSE;

-- 2) JPA 乐观锁列：由 @Version 管理并发更新，插入时取默认值 0，更新时由 Hibernate 递增。
ALTER TABLE sys_print_template
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
