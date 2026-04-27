CREATE TABLE IF NOT EXISTS sys_print_template (
    id BIGINT PRIMARY KEY,
    bill_type VARCHAR(64) NOT NULL,
    template_name VARCHAR(128) NOT NULL,
    template_html TEXT NOT NULL,
    is_default VARCHAR(1) NOT NULL DEFAULT '0',
    created_by BIGINT NOT NULL DEFAULT 0,
    created_name VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_name VARCHAR(64),
    updated_at TIMESTAMP,
    deleted_flag BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_sys_print_template_bill_type ON sys_print_template (bill_type);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_print_template_bill_type_name
    ON sys_print_template (bill_type, template_name, deleted_flag);

INSERT INTO sys_print_template (
    id, bill_type, template_name, template_html, is_default
) VALUES
    (
        700540000000000001,
        'purchase-orders',
        '采购订单默认模板',
        '<div><h2>采购订单</h2><div>单号：{{orderNo}}</div><div>供应商：{{supplierName}}</div><div>日期：{{orderDate}}</div><!--DETAIL_ROW_START--><div>{{detail.materialCode}} / {{detail.spec}} / {{detail.weightTon}}</div><!--DETAIL_ROW_END--></div>',
        '1'
    ),
    (
        700540000000000002,
        'sales-outbounds',
        '销售出库默认模板',
        '<div><h2>销售出库</h2><div>单号：{{outboundNo}}</div><div>客户：{{customerName}}</div><div>项目：{{projectName}}</div><!--DETAIL_ROW_START--><div>{{detail.materialCode}} / {{detail.batchNo}} / {{detail.weightTon}}</div><!--DETAIL_ROW_END--></div>',
        '1'
    )
ON CONFLICT (id) DO NOTHING;
