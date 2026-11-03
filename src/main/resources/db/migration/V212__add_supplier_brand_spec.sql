-- 供应商品牌规格配置(md_supplier_brand_spec): 某供应商某品牌允许的「类别 + 材质 + 规格 + 定尺」组合
-- 说明:
--   1) 报价表只显示这里被勾选的组合; 该品牌没有任何未删除行(空集)即表示回落到「按商品资料筛出的该品牌
--      全量组合」, 因此「清空」只是把行软删, 不需要额外的「已配置」标记行。
--   2) 品牌仍只是 md_supplier_brand 上的一串名称(不建品牌主数据), 故配置挂在 supplier_brand_id 上而不是
--      (supplier_id, brand_name): 品牌行软删后其实体不再对外可见(接口按未删除品牌读取), 但配置行保留,
--      同一品牌被重新勾选时(SupplierService.synchronizeBrands 复用同名行)配置自动恢复。
--   3) 整集替换语义: 保存时同一事务内先把该供应商全部品牌的旧行软删, 再写入新行; 唯一索引只约束
--      deleted_flag = false 的行, 所以软删留痕不会挡住之后再次写入同样的组合。
--   4) spec = 直径(mm), 必须大于 0; category/material/length 与商品资料同名口径(32/32/16)。
--   5) created_at 带时区: 该表只承载配置快照, 不参与单据时间口径, 按任务约定落 timestamptz;
--      由数据库默认值填充, 实体只读映射。
--
-- 迁移后校验(期望 1 张表 + 1 个部分唯一索引 + 1 个普通索引 + 1 个外键):
--   select count(*) from information_schema.tables
--    where table_schema = 'public' and table_name = 'md_supplier_brand_spec';
--   select indexname from pg_indexes
--    where schemaname = 'public' and tablename = 'md_supplier_brand_spec' order by indexname;
--
-- 注意: 本脚本一旦被任一环境执行就不能再改内容(Flyway 校验和), 后续修正只能新增更高版本。

CREATE TABLE public.md_supplier_brand_spec (
    id bigint NOT NULL,
    supplier_brand_id bigint NOT NULL,
    category character varying(32) NOT NULL,
    material character varying(32) NOT NULL,
    spec integer NOT NULL,
    length character varying(16) NOT NULL,
    deleted_flag boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT md_supplier_brand_spec_pkey PRIMARY KEY (id),
    CONSTRAINT fk_supplier_brand_spec_brand FOREIGN KEY (supplier_brand_id)
        REFERENCES public.md_supplier_brand(id),
    CONSTRAINT ck_supplier_brand_spec_spec CHECK (spec > 0)
);

-- 未删除行内, 同一 品牌 + 类别 + 材质 + 规格 + 定尺 只有一条配置
CREATE UNIQUE INDEX uk_supplier_brand_spec_active
    ON public.md_supplier_brand_spec USING btree (supplier_brand_id, category, material, spec, length)
    WHERE deleted_flag = false;

-- 读取与整集替换都是一次取某供应商全部品牌的配置, 按品牌外键建索引
CREATE INDEX idx_md_supplier_brand_spec_brand
    ON public.md_supplier_brand_spec USING btree (supplier_brand_id);

COMMENT ON TABLE public.md_supplier_brand_spec IS
    '供应商品牌规格配置: 某供应商某品牌允许的 类别/材质/规格/定尺 组合; 空集表示回落到按商品资料全量, 整体替换写入';
COMMENT ON COLUMN public.md_supplier_brand_spec.supplier_brand_id IS
    '供应商品牌ID, 关联 md_supplier_brand.id; 品牌软删后配置随之不再对外可见';
COMMENT ON COLUMN public.md_supplier_brand_spec.category IS
    '类别(商品资料 category 同口径), 不允许为空';
COMMENT ON COLUMN public.md_supplier_brand_spec.material IS
    '材质(商品资料 material 同口径), 不允许为空; 长度已拆分, 不得含 9米/12米';
COMMENT ON COLUMN public.md_supplier_brand_spec.spec IS '规格 = 直径 mm, 必须大于 0';
COMMENT ON COLUMN public.md_supplier_brand_spec.length IS
    '定尺(如 9米/12米), 不允许为空; 与商品资料 length 同口径';
COMMENT ON COLUMN public.md_supplier_brand_spec.deleted_flag IS
    '软删除标记, true 表示已被整体替换; 仅 false 的行参与唯一性与配置读取';
