-- 销售合同: 仅规定项目级总金额与总吨位, 不设明细表, 不建打印模板。
-- 说明:
--   1) 与基线中已退役的 ct_sales_contract(旧合同模块) 无任何关系, 不复用旧表;
--   2) status 取值对齐 StatusConstants: 草稿 / 已审核 / 已发出 / 归档 / 作废;
--   3) 合同金额/吨位是销售订单保存前的只读校验依据, 不参与订单落库阻断;
--   4) 合同正文以附件形式绑定(moduleKey = sales-contract), 复用现有附件能力。

CREATE TABLE public.so_sales_contract (
    id bigint NOT NULL,
    contract_no character varying(64) NOT NULL,
    name character varying(128),
    customer_id bigint NOT NULL,
    customer_name character varying(200) NOT NULL,
    project_id bigint NOT NULL,
    project_name character varying(200) NOT NULL,
    sign_date date NOT NULL,
    start_date date,
    end_date date,
    total_amount numeric(18,2) NOT NULL,
    total_tonnage numeric(18,8) NOT NULL,
    status character varying(16) NOT NULL,
    remark character varying(255),
    version bigint DEFAULT 0 NOT NULL,
    created_by bigint DEFAULT 0 NOT NULL,
    created_name character varying(64) DEFAULT 'system'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by bigint,
    updated_name character varying(64),
    updated_at timestamp without time zone,
    deleted_flag boolean DEFAULT false NOT NULL
)
WITH (fillfactor='70', autovacuum_vacuum_scale_factor='0.02');

ALTER TABLE ONLY public.so_sales_contract
    ADD CONSTRAINT so_sales_contract_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.so_sales_contract
    ADD CONSTRAINT so_sales_contract_contract_no_key UNIQUE (contract_no);

CREATE INDEX idx_so_sales_contract_project_id ON public.so_sales_contract USING btree (project_id);
CREATE INDEX idx_so_sales_contract_customer_id ON public.so_sales_contract USING btree (customer_id);
CREATE INDEX idx_so_sales_contract_status ON public.so_sales_contract USING btree (status);

COMMENT ON TABLE public.so_sales_contract IS '销售合同: 项目级金额/吨位额度, 无明细表';
COMMENT ON COLUMN public.so_sales_contract.contract_no IS '合同编号, 全表唯一(含软删行)';
COMMENT ON COLUMN public.so_sales_contract.status IS '状态: 草稿 / 已审核 / 已发出 / 归档 / 作废';

-- 权限目录种子: 与 PermissionCodes 保持一致, 由启动时的目录同步幂等 upsert;
-- 此处显式落库以便迁移后即可在权限矩阵中看到, 超管角色通过通配权限 '*' 自动拥有。
INSERT INTO public.sys_permission (code, resource, action, field, description)
VALUES
    ('sales-contracts:read', 'sales-contracts', 'read', NULL, '销售合同查询'),
    ('sales-contracts:create', 'sales-contracts', 'create', NULL, '销售合同创建'),
    ('sales-contracts:update', 'sales-contracts', 'update', NULL, '销售合同编辑'),
    ('sales-contracts:delete', 'sales-contracts', 'delete', NULL, '销售合同删除')
ON CONFLICT (code) DO NOTHING;
