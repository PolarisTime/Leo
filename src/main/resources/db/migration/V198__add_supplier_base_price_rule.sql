-- 供应商品牌基价加价规则(按 供应商 + 品牌 一套): 录入基价后据此自动推算其它规格价
-- 说明:
--   1) 一套规则 = 若干行 (supplier_id, brand_name, material, spec, length, delta);
--      其中至多一行 is_base = true 且 delta = 0, 该行就是「基价规格」; 其余行 delta 为相对基价
--      的加减(元/吨), 允许为负(减价)。
--   2) 整体替换语义: 保存时同一事务内先软删旧行再插入新行, 因此唯一索引只作用于 deleted_flag = false 的行;
--      软删行保留审计痕迹, 不参与唯一性计算, 但仍参与版本号计算(见第 3 点)。
--   3) version 由应用层维护: 同一套规则每次写入的行(含该次被软删的旧行)共享同一个版本号, 每次整体替换 +1;
--      对外 GET 的 version 取该 (supplier_id, brand_name) **全部行(含软删留痕行)** 的最大值, 从未写过 = 0。
--      未删除行非空时, 这个最大值恒等于「未删除行版本号的最大值」; 清空规则(items 为空)后没有未删除行,
--      新版本只能由留痕行承载 —— 否则空集读回 0, 前端下一次前置条件(X-Resource-Version)会永久 412。
--      写入用请求头 X-Resource-Version 做前置条件, 不匹配返回 412。该列不是 JPA @Version 乐观锁列
--      (行是整组替换而非逐行更新), 故不设自增序列。
--   4) 品牌名/材质/定尺长度与 mk_supplier_price_list 对齐(64/16/16); delta 精度与价格列一致;
--      绝对值上限 100000 与接口层校验保持一致。
--   5) 未删除行内 (supplier_id, brand_name, material, spec, length) 唯一; 每个 (supplier_id, brand_name)
--      至多一条基价行, 由部分唯一索引兜底。
--
-- 迁移后校验(期望 1 张表 + 2 个唯一索引):
--   select count(*) from information_schema.tables
--    where table_schema = 'public' and table_name = 'mk_supplier_base_price_rule';
--   select indexname from pg_indexes
--    where schemaname = 'public' and tablename = 'mk_supplier_base_price_rule' order by indexname;
--
-- 注意: 本脚本一旦被任一环境执行就不能再改内容(Flyway 校验和), 后续修正只能新增更高版本。

CREATE TABLE public.mk_supplier_base_price_rule (
    id bigint NOT NULL,
    supplier_id bigint NOT NULL,
    brand_name character varying(64) NOT NULL,
    material character varying(16) NOT NULL,
    spec integer NOT NULL,
    length character varying(16) NOT NULL,
    delta numeric(12,2) DEFAULT 0 NOT NULL,
    is_base boolean DEFAULT false NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_by bigint DEFAULT 0 NOT NULL,
    created_name character varying(64) DEFAULT 'system'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by bigint,
    updated_name character varying(64),
    updated_at timestamp without time zone,
    deleted_flag boolean DEFAULT false NOT NULL,
    CONSTRAINT mk_supplier_base_price_rule_pkey PRIMARY KEY (id),
    CONSTRAINT fk_supplier_base_price_rule_supplier FOREIGN KEY (supplier_id) REFERENCES public.md_supplier(id),
    CONSTRAINT ck_supplier_base_price_rule_spec CHECK (spec > 0),
    CONSTRAINT ck_supplier_base_price_rule_delta CHECK (delta >= -100000 AND delta <= 100000),
    CONSTRAINT ck_supplier_base_price_rule_base_delta CHECK (is_base = false OR delta = 0)
);

-- 未删除行内, 同一供应商 + 品牌 + 规格只有一条规则
CREATE UNIQUE INDEX uk_supplier_base_price_rule_key
    ON public.mk_supplier_base_price_rule USING btree (supplier_id, brand_name, material, spec, length)
    WHERE deleted_flag = false;

-- 未删除行内, 每个 (供应商, 品牌) 至多一条基价行
CREATE UNIQUE INDEX uk_supplier_base_price_rule_base
    ON public.mk_supplier_base_price_rule USING btree (supplier_id, brand_name)
    WHERE deleted_flag = false AND is_base = true;

COMMENT ON TABLE public.mk_supplier_base_price_rule IS
    '供应商品牌基价加价规则: 按 (供应商, 品牌) 一套, 每行给出相对基价的加减; 整体替换, 软删旧行';
COMMENT ON COLUMN public.mk_supplier_base_price_rule.supplier_id IS '供应商ID, 关联 md_supplier.id';
COMMENT ON COLUMN public.mk_supplier_base_price_rule.brand_name IS '品牌/产地, 与该供应商价格表 brand_name 同口径, 不建品牌主数据';
COMMENT ON COLUMN public.mk_supplier_base_price_rule.material IS '材质, 长度已拆分, 不得含 9米/12米';
COMMENT ON COLUMN public.mk_supplier_base_price_rule.spec IS '规格 = 直径 mm, 必须大于 0';
COMMENT ON COLUMN public.mk_supplier_base_price_rule.length IS '定尺; 无定尺概念时存空串, 与 mk_supplier_price_item.length 对齐';
COMMENT ON COLUMN public.mk_supplier_base_price_rule.delta IS
    '相对基价的加减(元/吨), 可为负; is_base 行必须为 0; 精度两位小数, 绝对值不超过 100000';
COMMENT ON COLUMN public.mk_supplier_base_price_rule.is_base IS
    '是否基价规格: true 表示该行即基价(delta 必为 0); 每个 (供应商, 品牌) 至多一条';
COMMENT ON COLUMN public.mk_supplier_base_price_rule.version IS
    '规则版本号(整套共享): 取该业务键全部行(含软删留痕行)的最大值, 从未写过=0; 每次整体替换(含清空) +1; 非 JPA 乐观锁列';
COMMENT ON COLUMN public.mk_supplier_base_price_rule.deleted_flag IS
    '软删除标记, true 表示已被整体替换; 仅 false 的行参与唯一性与规则读取, 版本号仍统计留痕行';
