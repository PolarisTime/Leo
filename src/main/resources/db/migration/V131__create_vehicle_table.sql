-- V131: Extract Carrier vehicle fields into normalized md_vehicle table
-- M6: Carrier had 12 fixed columns for max 3 vehicles (1NF violation)

CREATE TABLE IF NOT EXISTS md_vehicle (
    id BIGINT PRIMARY KEY,
    carrier_id BIGINT NOT NULL,
    plate VARCHAR(16) NOT NULL,
    contact VARCHAR(32),
    phone VARCHAR(20),
    remark VARCHAR(64),
    sort_order INT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_name VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_name VARCHAR(64),
    updated_at TIMESTAMP,
    deleted_flag BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_md_vehicle_carrier FOREIGN KEY (carrier_id) REFERENCES md_carrier (id)
);

CREATE INDEX IF NOT EXISTS idx_md_vehicle_carrier_id ON md_vehicle (carrier_id);
CREATE INDEX IF NOT EXISTS idx_md_vehicle_plate ON md_vehicle (plate);

-- Migrate vehicle 1 data
INSERT INTO md_vehicle (id, carrier_id, plate, contact, phone, remark, sort_order)
SELECT nextval('hibernate_sequence'), id, vehicle_plate, vehicle_contact, vehicle_phone, vehicle_remark, 0
FROM md_carrier
WHERE vehicle_plate IS NOT NULL AND vehicle_plate <> '';

-- Migrate vehicle 2 data
INSERT INTO md_vehicle (id, carrier_id, plate, contact, phone, remark, sort_order)
SELECT nextval('hibernate_sequence'), id, vehicle_plate2, vehicle_contact2, vehicle_phone2, vehicle_remark2, 1
FROM md_carrier
WHERE vehicle_plate2 IS NOT NULL AND vehicle_plate2 <> '';

-- Migrate vehicle 3 data
INSERT INTO md_vehicle (id, carrier_id, plate, contact, phone, remark, sort_order)
SELECT nextval('hibernate_sequence'), id, vehicle_plate3, vehicle_contact3, vehicle_phone3, vehicle_remark3, 2
FROM md_carrier
WHERE vehicle_plate3 IS NOT NULL AND vehicle_plate3 <> '';

-- Drop old vehicle columns from Carrier
ALTER TABLE md_carrier
    DROP COLUMN vehicle_plate,
    DROP COLUMN vehicle_contact,
    DROP COLUMN vehicle_phone,
    DROP COLUMN vehicle_plate2,
    DROP COLUMN vehicle_contact2,
    DROP COLUMN vehicle_phone2,
    DROP COLUMN vehicle_plate3,
    DROP COLUMN vehicle_contact3,
    DROP COLUMN vehicle_phone3,
    DROP COLUMN vehicle_remark,
    DROP COLUMN vehicle_remark2,
    DROP COLUMN vehicle_remark3;
