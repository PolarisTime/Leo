-- Make warehouse contact/address fields optional.

ALTER TABLE md_warehouse ALTER COLUMN contact_name DROP NOT NULL;
ALTER TABLE md_warehouse ALTER COLUMN contact_phone DROP NOT NULL;
ALTER TABLE md_warehouse ALTER COLUMN address DROP NOT NULL;
