CREATE TABLE public.bd_document_charge_item (
    id bigint PRIMARY KEY,
    module_key varchar(64) NOT NULL,
    document_id bigint NOT NULL,
    line_no integer NOT NULL,
    charge_name varchar(64) NOT NULL,
    charge_direction varchar(32) NOT NULL,
    settlement_party_type varchar(32),
    settlement_party_id bigint,
    settlement_party_name varchar(128),
    amount numeric(14,2) NOT NULL,
    billable boolean DEFAULT true NOT NULL,
    source_module_key varchar(64),
    source_document_id bigint,
    source_charge_item_id bigint,
    remark varchar(255),
    created_by bigint DEFAULT 0 NOT NULL,
    created_name varchar(64) DEFAULT 'system' NOT NULL,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by bigint,
    updated_name varchar(64),
    updated_at timestamp,
    deleted_flag boolean DEFAULT false NOT NULL,
    version bigint,
    CONSTRAINT chk_document_charge_direction
        CHECK (charge_direction IN ('RECEIVABLE', 'PAYABLE', 'INTERNAL')),
    CONSTRAINT chk_document_charge_party_type
        CHECK (
            settlement_party_type IS NULL
            OR settlement_party_type IN ('CUSTOMER', 'SUPPLIER', 'CARRIER', 'COMPANY')
        ),
    CONSTRAINT chk_document_charge_amount_non_negative
        CHECK (amount >= 0)
);

CREATE INDEX idx_bd_document_charge_item_document
    ON public.bd_document_charge_item (module_key, document_id, deleted_flag, line_no, id);

CREATE INDEX idx_bd_document_charge_item_source
    ON public.bd_document_charge_item (source_module_key, source_document_id, source_charge_item_id);

CREATE UNIQUE INDEX uk_bd_document_charge_item_document_line_active
    ON public.bd_document_charge_item (module_key, document_id, line_no)
    WHERE deleted_flag = false;
