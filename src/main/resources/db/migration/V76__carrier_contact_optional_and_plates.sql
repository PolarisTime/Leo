-- Make carrier contact fields optional and add vehicle plates support.

ALTER TABLE md_carrier ALTER COLUMN contact_name DROP NOT NULL;
ALTER TABLE md_carrier ALTER COLUMN contact_phone DROP NOT NULL;

ALTER TABLE md_carrier ADD COLUMN IF NOT EXISTS vehicle_plates TEXT;

COMMENT ON COLUMN md_carrier.vehicle_plates IS 'JSON array of {plate, contact} for multi-vehicle carriers';
