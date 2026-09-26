-- 打印模板: 按「项目 + 单据类型」记住该项目上次打印所选模板, 下次打开打印弹窗时默认回填。
-- 说明:
--   1) 粒度 = (project_id, bill_type), 同一项目下不同单据类型(如销售订单/采购订单)各记一份;
--   2) template_id 存引用, template_name 存快照便于展示; 模板被删除/停用时前端回退默认模板;
--   3) 无项目的单据不记忆(回退 is_default/首个模板), 故不写无 project_id 的行;
--   4) 不加外键: 与 mk_quote_item.purchase_order_id 一致的计划态引用取舍, 且避免 system 模块与
--      master 模块的表级耦合; 项目软删后偏好行保留但不参与查询。

CREATE TABLE public.sys_project_print_preference (
    id bigint NOT NULL,
    project_id bigint NOT NULL,
    bill_type character varying(64) NOT NULL,
    template_id bigint NOT NULL,
    template_name character varying(128) NOT NULL,
    created_by bigint DEFAULT 0 NOT NULL,
    created_name character varying(64) DEFAULT 'system'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by bigint,
    updated_name character varying(64),
    updated_at timestamp without time zone,
    deleted_flag boolean DEFAULT false NOT NULL,
    CONSTRAINT sys_project_print_preference_pkey PRIMARY KEY (id)
);

COMMENT ON TABLE public.sys_project_print_preference IS '项目打印模板偏好(按项目+单据类型记忆上次所选模板)';
COMMENT ON COLUMN public.sys_project_print_preference.project_id IS '项目ID(md_project.id)';
COMMENT ON COLUMN public.sys_project_print_preference.bill_type IS '单据类型(与 sys_print_template.bill_type 一致)';
COMMENT ON COLUMN public.sys_project_print_preference.template_id IS '上次所选打印模板ID(计划态引用, 不加外键)';
COMMENT ON COLUMN public.sys_project_print_preference.template_name IS '模板名称快照(便于展示)';

CREATE UNIQUE INDEX uk_project_print_preference_project_bill
    ON public.sys_project_print_preference (project_id, bill_type)
    WHERE deleted_flag = false;

CREATE INDEX idx_project_print_preference_project
    ON public.sys_project_print_preference (project_id)
    WHERE deleted_flag = false;
