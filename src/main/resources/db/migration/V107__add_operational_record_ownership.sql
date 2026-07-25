-- 将业务所有权与 created_by 创建审计解耦。
-- 本迁移属于兼容发布：owner_user_id 暂时允许为空，待新版本稳定后再通过后续迁移强制非空。

ALTER TABLE public.so_sales_order
    ADD COLUMN owner_user_id bigint;

ALTER TABLE public.sys_attachment
    ADD COLUMN owner_user_id bigint;

CREATE TABLE public.sys_record_ownership_migration_audit (
    migration_version integer NOT NULL,
    entity_type character varying(64) NOT NULL,
    record_id bigint NOT NULL,
    original_created_by bigint NOT NULL,
    new_owner_user_id bigint NOT NULL,
    reason character varying(128) NOT NULL,
    migrated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sys_record_ownership_migration_audit
        PRIMARY KEY (migration_version, entity_type, record_id),
    CONSTRAINT chk_sys_record_ownership_migration_entity
        CHECK (entity_type IN ('sales-order', 'attachment'))
);

DO $$
DECLARE
    active_account_count integer;
    owned_record_count bigint;
BEGIN
    SELECT COUNT(*)
      INTO active_account_count
      FROM public.sys_user
     WHERE deleted_flag = FALSE
       AND status = 'NORMAL';

    SELECT (SELECT COUNT(*) FROM public.so_sales_order)
         + (SELECT COUNT(*) FROM public.sys_attachment)
      INTO owned_record_count;

    IF active_account_count > 1 THEN
        RAISE EXCEPTION 'V107: 单人模式存在多个活动账号，拒绝推断业务所有者';
    END IF;

    IF owned_record_count > 0 AND active_account_count <> 1 THEN
        RAISE EXCEPTION 'V107: 存在业务记录但活动账号数量不是 1，拒绝迁移业务所有权';
    END IF;
END $$;

WITH active_account AS (
    SELECT id
      FROM public.sys_user
     WHERE deleted_flag = FALSE
       AND status = 'NORMAL'
)
INSERT INTO public.sys_record_ownership_migration_audit (
    migration_version,
    entity_type,
    record_id,
    original_created_by,
    new_owner_user_id,
    reason
)
SELECT 107,
       'sales-order',
       sales_order.id,
       sales_order.created_by,
       active_account.id,
       'SINGLE_ACCOUNT_OPERATIONAL_OWNERSHIP'
  FROM public.so_sales_order sales_order
 CROSS JOIN active_account;

WITH active_account AS (
    SELECT id
      FROM public.sys_user
     WHERE deleted_flag = FALSE
       AND status = 'NORMAL'
)
INSERT INTO public.sys_record_ownership_migration_audit (
    migration_version,
    entity_type,
    record_id,
    original_created_by,
    new_owner_user_id,
    reason
)
SELECT 107,
       'attachment',
       attachment.id,
       attachment.created_by,
       active_account.id,
       'SINGLE_ACCOUNT_OPERATIONAL_OWNERSHIP'
  FROM public.sys_attachment attachment
 CROSS JOIN active_account;

WITH active_account AS (
    SELECT id
      FROM public.sys_user
     WHERE deleted_flag = FALSE
       AND status = 'NORMAL'
)
UPDATE public.so_sales_order sales_order
   SET owner_user_id = active_account.id
  FROM active_account;

WITH active_account AS (
    SELECT id
      FROM public.sys_user
     WHERE deleted_flag = FALSE
       AND status = 'NORMAL'
)
UPDATE public.sys_attachment attachment
   SET owner_user_id = active_account.id
  FROM active_account;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM public.so_sales_order
         WHERE owner_user_id IS NULL
    ) THEN
        RAISE EXCEPTION 'V107: 销售订单业务所有者回填不完整';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM public.sys_attachment
         WHERE owner_user_id IS NULL
    ) THEN
        RAISE EXCEPTION 'V107: 附件业务所有者回填不完整';
    END IF;

    IF (SELECT COUNT(*)
          FROM public.sys_record_ownership_migration_audit
         WHERE migration_version = 107
           AND entity_type = 'sales-order')
       <> (SELECT COUNT(*) FROM public.so_sales_order) THEN
        RAISE EXCEPTION 'V107: 销售订单所有权迁移审计数量不一致';
    END IF;

    IF (SELECT COUNT(*)
          FROM public.sys_record_ownership_migration_audit
         WHERE migration_version = 107
           AND entity_type = 'attachment')
       <> (SELECT COUNT(*) FROM public.sys_attachment) THEN
        RAISE EXCEPTION 'V107: 附件所有权迁移审计数量不一致';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM public.sys_record_ownership_migration_audit ownership_audit
          JOIN public.so_sales_order sales_order
            ON sales_order.id = ownership_audit.record_id
         WHERE ownership_audit.migration_version = 107
           AND ownership_audit.entity_type = 'sales-order'
           AND (ownership_audit.original_created_by <> sales_order.created_by
                OR ownership_audit.new_owner_user_id <> sales_order.owner_user_id)
    ) THEN
        RAISE EXCEPTION 'V107: 销售订单创建审计或业务所有权校验失败';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM public.sys_record_ownership_migration_audit ownership_audit
          JOIN public.sys_attachment attachment
            ON attachment.id = ownership_audit.record_id
         WHERE ownership_audit.migration_version = 107
           AND ownership_audit.entity_type = 'attachment'
           AND (ownership_audit.original_created_by <> attachment.created_by
                OR ownership_audit.new_owner_user_id <> attachment.owner_user_id)
    ) THEN
        RAISE EXCEPTION 'V107: 附件创建审计或业务所有权校验失败';
    END IF;
END $$;

CREATE INDEX idx_so_sales_order_owner_user
    ON public.so_sales_order (owner_user_id);

CREATE INDEX idx_sys_attachment_owner_user
    ON public.sys_attachment (owner_user_id);

ALTER TABLE public.so_sales_order
    ADD CONSTRAINT fk_so_sales_order_owner_user
        FOREIGN KEY (owner_user_id) REFERENCES public.sys_user (id)
        ON DELETE RESTRICT NOT VALID;

ALTER TABLE public.sys_attachment
    ADD CONSTRAINT fk_sys_attachment_owner_user
        FOREIGN KEY (owner_user_id) REFERENCES public.sys_user (id)
        ON DELETE RESTRICT NOT VALID;

ALTER TABLE public.so_sales_order
    VALIDATE CONSTRAINT fk_so_sales_order_owner_user;

ALTER TABLE public.sys_attachment
    VALIDATE CONSTRAINT fk_sys_attachment_owner_user;

COMMENT ON COLUMN public.so_sales_order.owner_user_id IS
    '业务操作所有者；与不可变的 created_by 创建审计分离';

COMMENT ON COLUMN public.sys_attachment.owner_user_id IS
    '未绑定附件的业务操作所有者；与不可变的 created_by 创建审计分离';

COMMENT ON TABLE public.sys_record_ownership_migration_audit IS
    '业务所有权迁移审计；保留迁移前 created_by 与迁移后 owner_user_id 的对应关系';
