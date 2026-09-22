-- 项目资料: 多条"价格规定"(网价浮动规则), 交付核定单选其一并记忆上次使用。
-- 说明:
--   1) 一个项目可有 N 条价格规定: 名称 + 方向(ADD加价/SUBTRACT减价) + 金额(元/吨) + 备注;
--   2) 交付核定按网价赋价时"单选互斥"(不叠加), 并把所选规则快照到销售订单;
--   3) md_project.last_price_rule_id 记住该项目上次使用的规则(跨单据);
--   4) 规则软删(deleted_flag), 保证历史单据可追溯; 旧 price_float_* 列保留但不再使用。

CREATE TABLE public.md_project_price_rule (
    id bigint NOT NULL,
    project_id bigint NOT NULL,
    name character varying(64) NOT NULL,
    mode character varying(8) NOT NULL,
    amount numeric(12, 2) NOT NULL,
    remark character varying(255),
    sort_order integer DEFAULT 0 NOT NULL,
    created_by bigint DEFAULT 0 NOT NULL,
    created_name character varying(64) DEFAULT 'system'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by bigint,
    updated_name character varying(64),
    updated_at timestamp without time zone,
    deleted_flag boolean DEFAULT false NOT NULL,
    CONSTRAINT md_project_price_rule_pkey PRIMARY KEY (id),
    CONSTRAINT fk_project_price_rule_project FOREIGN KEY (project_id) REFERENCES public.md_project (id),
    CONSTRAINT chk_project_price_rule_mode CHECK (mode IN ('ADD', 'SUBTRACT')),
    CONSTRAINT chk_project_price_rule_amount CHECK (amount >= 0)
);

COMMENT ON TABLE public.md_project_price_rule IS '项目价格规定(网价浮动规则)';
COMMENT ON COLUMN public.md_project_price_rule.name IS '规定名称(项目内唯一)';
COMMENT ON COLUMN public.md_project_price_rule.mode IS '方向: ADD加价/SUBTRACT减价';
COMMENT ON COLUMN public.md_project_price_rule.amount IS '固定幅度(元/吨), 非负';
COMMENT ON COLUMN public.md_project_price_rule.sort_order IS '展示顺序';

CREATE UNIQUE INDEX uk_project_price_rule_name
    ON public.md_project_price_rule (project_id, name)
    WHERE deleted_flag = false;
CREATE INDEX idx_project_price_rule_project
    ON public.md_project_price_rule (project_id)
    WHERE deleted_flag = false;

-- 项目级记忆: 上次交付核定使用的价格规定。
ALTER TABLE public.md_project
    ADD COLUMN last_price_rule_id bigint;
COMMENT ON COLUMN public.md_project.last_price_rule_id IS '上次交付核定使用的价格规定ID(跨单据记忆)';

-- 销售订单: 价格规定快照(保证历史可读, 不随规则变更/删除而失真)。
ALTER TABLE public.so_sales_order
    ADD COLUMN price_rule_id bigint,
    ADD COLUMN price_rule_name character varying(64),
    ADD COLUMN price_float_mode character varying(8),
    ADD COLUMN price_float_value numeric(12, 2);
COMMENT ON COLUMN public.so_sales_order.price_rule_id IS '交付核定所用价格规定ID(快照)';
COMMENT ON COLUMN public.so_sales_order.price_rule_name IS '交付核定所用价格规定名称(快照)';
COMMENT ON COLUMN public.so_sales_order.price_float_mode IS '交付核定所用方向(快照): ADD/SUBTRACT';
COMMENT ON COLUMN public.so_sales_order.price_float_value IS '交付核定所用幅度(快照, 元/吨)';

-- 迁移: 现有单一浮动约定转为首条价格规定(名称"默认")。
INSERT INTO public.md_project_price_rule (id, project_id, name, mode, amount, remark, sort_order)
SELECT (1000000 + ROW_NUMBER() OVER (ORDER BY project.id))::bigint,
       project.id,
       '默认',
       project.price_float_mode,
       project.price_float_value,
       '由原网价浮动约定迁移',
       0
FROM public.md_project project
WHERE project.deleted_flag = false
  AND project.price_float_mode IS NOT NULL
  AND project.price_float_value IS NOT NULL;
