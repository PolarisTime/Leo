ALTER TABLE md_customer ALTER COLUMN contact_name DROP NOT NULL;
ALTER TABLE md_customer ALTER COLUMN contact_phone DROP NOT NULL;
ALTER TABLE md_customer ALTER COLUMN city DROP NOT NULL;
ALTER TABLE md_customer ALTER COLUMN settlement_mode DROP NOT NULL;

ALTER TABLE md_customer ADD COLUMN IF NOT EXISTS project_name VARCHAR(128);
UPDATE md_customer SET project_name = customer_name WHERE project_name IS NULL;
ALTER TABLE md_customer ALTER COLUMN project_name SET NOT NULL;

ALTER TABLE md_customer ADD COLUMN IF NOT EXISTS project_name_abbr VARCHAR(64);
ALTER TABLE md_customer ADD COLUMN IF NOT EXISTS project_address VARCHAR(255);
