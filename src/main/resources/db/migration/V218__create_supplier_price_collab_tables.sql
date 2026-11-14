-- 供应商价格表实时协作（Node 边车 leo/collab-server）的三张持久化表。
--
-- 权威契约：leo/collab-server/src/store/postgres-store.ts 的 REQUIRED_COLUMNS。
-- 列名不可改 —— 边车以 schema-check=strict 启动自检，缺表/缺列会直接启动失败并列出缺什么。
-- 这三张表只被边车读写，Java 侧不映射 JPA 实体（Java 只签发票据、不碰协作文档）。
--
-- 写入职责（设计文档 §6.1）：协作开启期间 mk_supplier_price_item / mk_supplier_price_list
-- 只由边车的物化事务写，Java 侧的收益是「文档与 SQL 同事务」，不存在两边各写一半的中间态。
--
-- 迁移特性：空库可顺序执行、可重复执行已验证（CREATE TABLE / CREATE INDEX 不做 IF NOT EXISTS
-- 之外的隐式依赖）；脚本内不包含任何数据回填，因此对已有环境零副作用。

CREATE TABLE public.mk_supplier_price_collab_doc (
    room       text PRIMARY KEY,
    state      bytea NOT NULL,
    updated_at timestamp(6) WITHOUT TIME ZONE NOT NULL DEFAULT now()
);

COMMENT ON TABLE public.mk_supplier_price_collab_doc IS '供应商价格表协作文档快照: 一行 = 一个房间最新 Y.Doc 的完整二进制状态';
COMMENT ON COLUMN public.mk_supplier_price_collab_doc.room IS '房间名, 形如 supplier-price:<supplierId>(一个供应商一个房间)';
COMMENT ON COLUMN public.mk_supplier_price_collab_doc.state IS 'Y.Doc 全量状态(Y.encodeStateAsUpdate 的结果), 边车启动/加载房间时优先用它';
COMMENT ON COLUMN public.mk_supplier_price_collab_doc.updated_at IS '快照最后一次写回时间(边车按 debounce 合并写, 不是每条输入都更新)';

CREATE TABLE public.mk_supplier_price_collab_update (
    id          bigserial PRIMARY KEY,
    room        text NOT NULL,
    update_blob bytea NOT NULL,
    created_at  timestamp(6) WITHOUT TIME ZONE NOT NULL DEFAULT now()
);

COMMENT ON TABLE public.mk_supplier_price_collab_update IS '协作增量更新日志: 按房间顺序追加 Yjs update, 用于审计与回放(边车可关闭持久化)';
COMMENT ON COLUMN public.mk_supplier_price_collab_update.id IS '自增主键, 同时充当房间内的全局顺序号(边车插入时不传 id)';
COMMENT ON COLUMN public.mk_supplier_price_collab_update.room IS '房间名, 与 mk_supplier_price_collab_doc.room 同一口径';
COMMENT ON COLUMN public.mk_supplier_price_collab_update.update_blob IS '单条 Yjs update 的二进制载荷(空 update 不落库)';
COMMENT ON COLUMN public.mk_supplier_price_collab_update.created_at IS '该增量被服务端受理的时间';

CREATE INDEX IF NOT EXISTS idx_supplier_price_collab_update_room_id
    ON public.mk_supplier_price_collab_update USING btree (room, id);

CREATE TABLE public.mk_supplier_price_cell_op (
    id            bigint PRIMARY KEY,
    list_id       bigint NOT NULL,
    cell_key      text NOT NULL,
    price         numeric(12,2),
    price_status  varchar(16),
    remark        varchar(255),
    actor_id      bigint,
    list_revision bigint NOT NULL,
    created_at    timestamp(6) WITHOUT TIME ZONE NOT NULL DEFAULT now()
);

COMMENT ON TABLE public.mk_supplier_price_cell_op IS '单元格改价操作流水: 协作期间每次物化写回按「格子」记一条, 供追溯谁在什么时候把价格改成什么';
COMMENT ON COLUMN public.mk_supplier_price_cell_op.id IS '主键, 由边车生成雪花 ID(无数据库默认值)';
COMMENT ON COLUMN public.mk_supplier_price_cell_op.list_id IS '所属供应商价格表 id(mk_supplier_price_list.id)';
COMMENT ON COLUMN public.mk_supplier_price_cell_op.cell_key IS '单元格业务键, 形如 [category,material,spec,length].join(0x00), 与前端文档模型一致';
COMMENT ON COLUMN public.mk_supplier_price_cell_op.price IS '本次写回后的价格, 空表示未报价';
COMMENT ON COLUMN public.mk_supplier_price_cell_op.price_status IS '本次写回后的报价状态(如 NORMAL / OUT_OF_STOCK)';
COMMENT ON COLUMN public.mk_supplier_price_cell_op.remark IS '本次写回后的备注';
COMMENT ON COLUMN public.mk_supplier_price_cell_op.actor_id IS '操作人用户 id(雪花, 可能为空表示系统物化)';
COMMENT ON COLUMN public.mk_supplier_price_cell_op.list_revision IS '本次操作后价格表的版本号, 与 mk_supplier_price_list.version 对齐';
COMMENT ON COLUMN public.mk_supplier_price_cell_op.created_at IS '该格子被写回的时间';

CREATE INDEX IF NOT EXISTS idx_supplier_price_cell_op_list_created
    ON public.mk_supplier_price_cell_op USING btree (list_id, created_at);

CREATE INDEX IF NOT EXISTS idx_supplier_price_cell_op_cell_key
    ON public.mk_supplier_price_cell_op USING btree (cell_key);
