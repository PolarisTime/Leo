-- Add vehicle 2 and vehicle 3 columns for carrier.

ALTER TABLE md_carrier ADD COLUMN IF NOT EXISTS vehicle_plate2 VARCHAR(16);
ALTER TABLE md_carrier ADD COLUMN IF NOT EXISTS vehicle_contact2 VARCHAR(64);
ALTER TABLE md_carrier ADD COLUMN IF NOT EXISTS vehicle_phone2 VARCHAR(32);
ALTER TABLE md_carrier ADD COLUMN IF NOT EXISTS vehicle_plate3 VARCHAR(16);
ALTER TABLE md_carrier ADD COLUMN IF NOT EXISTS vehicle_contact3 VARCHAR(64);
ALTER TABLE md_carrier ADD COLUMN IF NOT EXISTS vehicle_phone3 VARCHAR(32);
