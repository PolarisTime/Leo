-- Add vehicle remark columns for each vehicle entry.

ALTER TABLE md_carrier ADD COLUMN IF NOT EXISTS vehicle_remark VARCHAR(64);
ALTER TABLE md_carrier ADD COLUMN IF NOT EXISTS vehicle_remark2 VARCHAR(64);
ALTER TABLE md_carrier ADD COLUMN IF NOT EXISTS vehicle_remark3 VARCHAR(64);
