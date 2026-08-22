-- 通用单据附加费用表（采购订单/销售订单/物流单共用）
-- 独立于各 items 货物明细表，避免污染重量/件数/导入数学；
-- module_key 由后端白名单校验，document_id 不建物理外键（跨模块通用子表）。
CREATE TABLE bd_document_charge_item (
    id bigint PRIMARY KEY,
    module_key varchar(64) NOT NULL,
    document_id bigint NOT NULL,
    line_no integer NOT NULL,
    charge_name varchar(128) NOT NULL,
    material_id bigint REFERENCES md_material(id),
    amount numeric(14, 2) NOT NULL,
    unit varchar(16),
    remark varchar(255),
    created_by bigint DEFAULT 0 NOT NULL,
    created_name varchar(64) DEFAULT 'system' NOT NULL,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by bigint,
    updated_name varchar(64),
    updated_at timestamp,
    deleted_flag boolean DEFAULT FALSE NOT NULL
);

CREATE INDEX idx_bd_document_charge_item_document
    ON bd_document_charge_item (module_key, document_id, deleted_flag, line_no);

-- 未删除行内 (module_key, document_id, line_no) 唯一，line_no 由服务层同步时重编号。
CREATE UNIQUE INDEX uk_bd_document_charge_item_document_line_active
    ON bd_document_charge_item (module_key, document_id, line_no)
    WHERE deleted_flag = FALSE;

ALTER TABLE bd_document_charge_item
    ADD CONSTRAINT chk_bd_document_charge_amount_non_negative
        CHECK (amount >= 0);
