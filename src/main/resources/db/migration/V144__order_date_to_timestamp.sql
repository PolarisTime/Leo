-- V144: Business date fields DATE → TIMESTAMP for time precision
ALTER TABLE po_purchase_order ALTER COLUMN order_date TYPE TIMESTAMP;
