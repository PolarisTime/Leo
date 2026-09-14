-- Phase 5 库存余额快照（增量余额表）。
-- 背景：库存余额此前在每次记账和列表查询时都对整张 inv_transaction 做全历史 SUM 聚合，
-- 写路径每行一次全表聚合、查询端整体 GROUP BY + 深 OFFSET，随账本增长线性劣化。
-- 本迁移新增 inv_balance 增量快照表，由 inv_transaction 记账在“同一事务、同一
-- (material_id, warehouse_id) 咨询锁”内 UPSERT 维护，查询与移动加权成本直接读快照，不再全表聚合。
--
-- 维度：与 InventoryBalanceReader 的成本读取维度一致，为 (material_id, warehouse_id)。
-- warehouse_id 采用哨兵 0 表示“无仓库”维度：雪花ID恒为正整数，0 永不与真实仓库冲突，
-- 因此该列可用 NOT NULL 并直接作为主键/冲突目标，简化 UPSERT 与 keyset 分页；
-- 代价是 inv_transaction 中 warehouse_id 为空的行在快照里统一落为 0。
--
-- 一致性：快照只由账本驱动（记账 +，软删 −），保证 Σ(未删除账本) == 快照；
-- 若出现偏差，可调用 InventoryBalanceMaintenanceService#rebuild 从账本幂等重算。

CREATE TABLE public.inv_balance (
    material_id bigint NOT NULL,
    warehouse_id bigint DEFAULT 0 NOT NULL,
    material_code character varying(64),
    warehouse_name character varying(128),
    batch_no character varying(64),
    quantity integer DEFAULT 0 NOT NULL,
    amount numeric(14,2) DEFAULT 0 NOT NULL,
    created_by bigint DEFAULT 0 NOT NULL,
    created_name character varying(64) DEFAULT 'system'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by bigint,
    updated_name character varying(64),
    updated_at timestamp without time zone,
    deleted_flag boolean DEFAULT false NOT NULL,
    -- 复合主键同时承担 (material_id, warehouse_id) 唯一约束：
    -- 一个物料在一个仓库（含哨兵 0 的无仓库维度）只有一行余额，UPSERT 冲突目标即此键。
    CONSTRAINT inv_balance_pkey PRIMARY KEY (material_id, warehouse_id)
);

COMMENT ON TABLE public.inv_balance IS
    '库存余额增量快照：按 (material_id, warehouse_id) 维护，由 inv_transaction 记账/软删同事务 UPSERT，不再全历史聚合。';
COMMENT ON COLUMN public.inv_balance.material_id IS '物料ID（雪花ID），与 warehouse_id 组成主键。';
COMMENT ON COLUMN public.inv_balance.warehouse_id IS '仓库ID（雪花ID）；0 为哨兵值，表示无仓库维度。';
COMMENT ON COLUMN public.inv_balance.material_code IS '物料编码快照：初始化取账本 MAX，运行期由记账覆盖为最新非空值。';
COMMENT ON COLUMN public.inv_balance.warehouse_name IS '仓库名称快照：初始化取账本 MAX，运行期由记账覆盖为最新非空值。';
COMMENT ON COLUMN public.inv_balance.batch_no IS '批次号快照（展示用、非维度键）：初始化取账本 MAX，运行期覆盖为最新非空值。';
COMMENT ON COLUMN public.inv_balance.quantity IS '带符号库存数量余额 SUM(quantity * direction)。';
COMMENT ON COLUMN public.inv_balance.amount IS '带符号库存价值余额 SUM(amount)，恒等于 Σ账本带符号金额。';

-- 一次性初始化：从现有未删除账本按 (material_id, warehouse_id) 聚合，保证迁移后余额立即正确。
-- 幂等：重复执行时 UPSERT 覆盖为与账本一致的值。
INSERT INTO public.inv_balance (
    material_id, warehouse_id, material_code, warehouse_name, batch_no, quantity, amount
)
SELECT t.material_id,
       COALESCE(t.warehouse_id, 0) AS warehouse_id,
       MAX(t.material_code) AS material_code,
       MAX(t.warehouse_name) AS warehouse_name,
       MAX(t.batch_no) AS batch_no,
       COALESCE(SUM(t.quantity * t.direction), 0) AS quantity,
       COALESCE(SUM(t.amount), 0) AS amount
FROM public.inv_transaction t
WHERE t.deleted_flag = false
GROUP BY t.material_id, COALESCE(t.warehouse_id, 0);

-- 索引：复合主键 (material_id, warehouse_id) 已是有序 B-tree，直接支撑默认的
-- (material_id, warehouse_id) keyset 分页与按维度等值查询，无需额外索引。
-- keyword 使用 ILIKE '%...%'，btree 无法加速，故不在热写快照表上新增低价值索引；
-- 如后续确需全文/子串检索，再以 pg_trgm GIN 索引单独评估。
