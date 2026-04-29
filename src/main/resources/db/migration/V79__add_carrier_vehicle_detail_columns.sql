-- Add individual vehicle plate/contact/phone columns for carrier.

ALTER TABLE md_carrier ADD COLUMN IF NOT EXISTS vehicle_plate VARCHAR(16);
ALTER TABLE md_carrier ADD COLUMN IF NOT EXISTS vehicle_contact VARCHAR(64);
ALTER TABLE md_carrier ADD COLUMN IF NOT EXISTS vehicle_phone VARCHAR(32);
