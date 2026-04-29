-- Make vehicle_type nullable — replaced by vehicle_plate/contact/phone fields.

ALTER TABLE md_carrier ALTER COLUMN vehicle_type DROP NOT NULL;
