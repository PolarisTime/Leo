alter table st_supplier_statement_item
    add column if not exists source_inbound_item_id bigint;

alter table st_customer_statement_item
    add column if not exists source_sales_order_item_id bigint;
